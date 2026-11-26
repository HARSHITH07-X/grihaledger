package com.example.data.firebase

import com.example.domain.model.Member
import com.example.domain.model.Role
import com.example.domain.model.UserProfile
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.SessionRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirebaseSessionRepository @Inject constructor(
  private val auth: FirebaseAuth,
  private val db: FirebaseFirestore,
  private val authRepository: AuthRepository,
  private val firebaseAuthRepository: FirebaseAuthRepository,
) : SessionRepository {

  override fun observeUserProfile(): Flow<UserProfile?> =
    authRepository.observeAuthState().flatMapLatest { authUser ->
      if (authUser == null) {
        flowOf(null)
      } else {
        flow {
          val firebaseUser = auth.currentUser
          if (firebaseUser != null && firebaseUser.uid == authUser.uid) {
            runCatching { firebaseAuthRepository.ensureUserProfileExists(firebaseUser) }
          }
          val docRef = db.collection("users").document(authUser.uid)
          emitAll(
            docRef
              .snapshots()
              .map { snap ->
                if (!snap.exists()) {
                  UserProfile(
                    uid = authUser.uid,
                    displayName = authUser.displayName,
                    email = authUser.email,
                    familyId = null,
                  )
                } else {
                  UserProfile(
                    uid = snap.getString("uid") ?: authUser.uid,
                    displayName = snap.getString("displayName") ?: authUser.displayName,
                    email = snap.getString("email") ?: authUser.email,
                    familyId = snap.getString("familyId")?.takeIf { it.isNotBlank() },
                  )
                }
              }
              .catch { error ->
                val fsError = error.unwrapFirestoreException()
                if (fsError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == authUser.uid) {
                  handleFirestoreError(fsError ?: error, OperationType.GET, docRef.path, auth)
                }
                emit(null)
              }
          )
        }
      }
    }

  override fun observeCurrentMember(): Flow<Member?> =
    observeUserProfile().flatMapLatest { profile ->
      val familyId = profile?.familyId
      if (profile == null || familyId.isNullOrBlank()) {
        flowOf(null)
      } else {
        val memberRef =
          db.collection("families").document(familyId).collection("members").document(profile.uid)
        memberRef
          .snapshots()
          .map { snap -> snap.toDomainMemberOrNull() }
          .catch { error ->
            val fsError = error.unwrapFirestoreException()
            if (fsError == null && error is CancellationException) throw error
            if (fsError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
              // Member was removed from family or session changed
              emit(null)
            } else {
              if (auth.currentUser?.uid == profile.uid) {
                handleFirestoreError(fsError ?: error, OperationType.GET, memberRef.path, auth)
              }
              emit(null)
            }
          }
      }
    }

  override fun observeAvailableDebugMembers(): Flow<List<Member>> = flowOf(emptyList())

  override suspend fun switchDebugMember(uid: String) = Unit
}

internal fun DocumentSnapshot.toDomainMemberOrNull(): Member? {
  if (!exists()) return null
  val uid = getString("uid")?.takeIf { it.isNotBlank() } ?: id
  val familyId = getString("familyId")?.takeIf { it.isNotBlank() } ?: return null
  val displayName = getString("displayName") ?: "Family Member"
  val email = getString("email") ?: ""
  val role =
    when (getString("role")?.lowercase()) {
      "admin" -> Role.ADMIN
      "viewer" -> Role.VIEWER
      else -> Role.MEMBER
    }
  val joinedAt =
    getTimestamp("joinedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      ?.toDate()
      ?.time
      ?: 0L
  val isActive = getBoolean("isActive") ?: true
  return Member(
    uid = uid,
    familyId = familyId,
    displayName = displayName,
    email = email,
    role = role,
    joinedAtEpochMillis = joinedAt,
    isActive = isActive,
  )
}
