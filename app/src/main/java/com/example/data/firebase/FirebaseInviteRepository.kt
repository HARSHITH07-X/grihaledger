package com.example.data.firebase

import com.example.domain.model.Invite
import com.example.domain.model.InviteStatus
import com.example.domain.model.InviteValidationResult
import com.example.domain.model.Role
import com.example.domain.repository.InviteRepository
import com.example.domain.repository.SessionRepository
import com.example.domain.rules.InviteRules
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseInviteRepository @Inject constructor(
  private val auth: FirebaseAuth,
  private val db: FirebaseFirestore,
  private val sessionRepository: SessionRepository,
) : InviteRepository {

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  override fun observePendingInvites(familyId: String): Flow<List<Invite>> =
    flow {
      val uid = auth.currentUser?.uid
      if (uid == null || familyId.isBlank()) {
        emit(emptyList())
        return@flow
      }
      val query =
        db.collection("invites")
          .whereEqualTo("familyId", familyId)
          .whereEqualTo("status", "pending")

      query
        .snapshots()
        .map { snap ->
          val now = System.currentTimeMillis()
          snap.documents
            .mapNotNull { it.toDomainInviteOrNull() }
            .filter { it.expiresAtEpochMillis > now }
            .sortedByDescending { it.createdAtEpochMillis }
        }
        .catch { error ->
          val fsError = error.unwrapFirestoreException()
          if (fsError == null && error is CancellationException) throw error
          if (auth.currentUser?.uid == uid) {
            handleFirestoreError(fsError ?: error, OperationType.LIST, "invites", auth)
          }
          emit(emptyList())
        }
        .collect { emit(it) }
    }

  override suspend fun createInvite(
    familyId: String,
    familyName: String,
    role: Role,
  ): Result<Invite> {
    val uid = requireUserId()
    if (!InviteRules.isAssignableRole(role)) {
      return Result.failure(IllegalArgumentException("Invites can only grant Member or Viewer roles."))
    }

    val code = InviteRules.generateCode()
    val nowMillis = System.currentTimeMillis()
    val expiresMillis = nowMillis + InviteRules.EXPIRY_MILLIS
    val inviteRef = db.collection("invites").document(code)

    return try {
      val payload =
        mapOf(
          "code" to code,
          "familyId" to familyId,
          "familyName" to familyName.take(60),
          "role" to role.toFirestoreValue(),
          "status" to "pending",
          "createdByUid" to uid,
          "createdAt" to FieldValue.serverTimestamp(),
          "expiresAt" to Timestamp(Date(expiresMillis)),
        )
      inviteRef.set(payload).await()
      Result.success(
        Invite(
          code = code,
          familyId = familyId,
          familyName = familyName,
          role = role,
          status = InviteStatus.PENDING,
          createdByUid = uid,
          createdAtEpochMillis = nowMillis,
          expiresAtEpochMillis = expiresMillis,
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, inviteRef.path, auth)
      Result.failure(IllegalStateException(e.toFriendlyFirestoreMessage("Could not create invite.")))
    }
  }

  override suspend fun revokeInvite(code: String): Result<Unit> {
    val uid = requireUserId()
    val normalized = InviteRules.normalizeCode(code)
    val inviteRef = db.collection("invites").document(normalized)
    return try {
      inviteRef
        .update(
          mapOf(
            "status" to "revoked",
            "revokedByUid" to uid,
            "revokedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, inviteRef.path, auth)
      Result.failure(IllegalStateException(e.toFriendlyFirestoreMessage("Could not revoke invite.")))
    }
  }

  override suspend fun previewInvite(rawCode: String): Result<InviteValidationResult> {
    requireUserId()
    val normalized = InviteRules.normalizeCode(rawCode)
    val profile = sessionRepository.observeUserProfile().first()
    val currentFamilyId = profile?.familyId
    val now = System.currentTimeMillis()

    if (!InviteRules.isValidCodeFormat(normalized) || !currentFamilyId.isNullOrBlank()) {
      return Result.success(
        InviteRules.validateForJoin(
          invite = null,
          normalizedCode = normalized,
          currentUserFamilyId = currentFamilyId,
          nowEpochMillis = now,
        )
      )
    }

    val inviteRef = db.collection("invites").document(normalized)
    return try {
      val snapshot = inviteRef.get().await()
      val invite = snapshot.toDomainInviteOrNull()
      Result.success(
        InviteRules.validateForJoin(
          invite = invite,
          normalizedCode = normalized,
          currentUserFamilyId = currentFamilyId,
          nowEpochMillis = System.currentTimeMillis(),
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, inviteRef.path, auth)
      Result.failure(IllegalStateException(e.toFriendlyFirestoreMessage("Could not check invite code.")))
    }
  }

  override suspend fun acceptInvite(code: String): Result<String> {
    val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in."))
    val uid = user.uid
    val normalized = InviteRules.normalizeCode(code)
    val inviteRef = db.collection("invites").document(normalized)

    return try {
      val inviteSnap = inviteRef.get().await()
      val invite = inviteSnap.toDomainInviteOrNull()
      val profile = sessionRepository.observeUserProfile().first()
      val validation =
        InviteRules.validateForJoin(
          invite = invite,
          normalizedCode = normalized,
          currentUserFamilyId = profile?.familyId,
          nowEpochMillis = System.currentTimeMillis(),
        )
      if (validation is InviteValidationResult.Invalid) {
        return Result.failure(IllegalStateException(validation.message))
      }
      val validInvite = (validation as InviteValidationResult.Valid).invite

      val displayName =
        user.displayName?.takeIf { it.isNotBlank() }
          ?: user.email?.substringBefore('@')
          ?: "Family Member"
      val email = user.email?.takeIf { it.isNotBlank() } ?: "$uid@users.local"

      val memberRef =
        db.collection("families")
          .document(validInvite.familyId)
          .collection("members")
          .document(uid)
      val userRef = db.collection("users").document(uid)

      val batch = db.batch()
      batch.set(
        memberRef,
        mapOf(
          "uid" to uid,
          "familyId" to validInvite.familyId,
          "displayName" to displayName.take(80),
          "email" to email.take(200),
          "role" to validInvite.role.toFirestoreValue(),
          "isActive" to true,
          "inviteCode" to normalized,
          "joinedAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.update(
        inviteRef,
        mapOf(
          "status" to "used",
          "usedByUid" to uid,
          "usedAt" to FieldValue.serverTimestamp(),
        ),
      )
      val userSnap = userRef.get().await()
      if (userSnap.exists()) {
        batch.update(
          userRef,
          mapOf(
            "familyId" to validInvite.familyId,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      } else {
        batch.set(
          userRef,
          mapOf(
            "uid" to uid,
            "displayName" to displayName.take(80),
            "email" to email.take(200),
            "familyId" to validInvite.familyId,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      batch.commit().await()
      Result.success(validInvite.familyId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, inviteRef.path, auth)
      Result.failure(
        IllegalStateException(
          e.toFriendlyFirestoreMessage("This invite is no longer valid or was already used.")
        )
      )
    }
  }

  private fun DocumentSnapshot.toDomainInviteOrNull(): Invite? {
    if (!exists()) return null
    val code = getString("code") ?: id
    val familyId = getString("familyId") ?: return null
    val familyName = getString("familyName") ?: "Family"
    val role =
      when (getString("role")?.lowercase()) {
        "viewer" -> Role.VIEWER
        "member" -> Role.MEMBER
        else -> Role.ADMIN
      }
    val status =
      when (getString("status")?.lowercase()) {
        "used" -> InviteStatus.USED
        "revoked" -> InviteStatus.REVOKED
        else -> InviteStatus.PENDING
      }
    val createdByUid = getString("createdByUid") ?: ""
    val createdAt =
      getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        ?.toDate()
        ?.time ?: 0L
    val expiresAt = getTimestamp("expiresAt")?.toDate()?.time ?: 0L
    return Invite(
      code = code,
      familyId = familyId,
      familyName = familyName,
      role = role,
      status = status,
      createdByUid = createdByUid,
      createdAtEpochMillis = createdAt,
      expiresAtEpochMillis = expiresAt,
      usedByUid = getString("usedByUid"),
    )
  }
}
