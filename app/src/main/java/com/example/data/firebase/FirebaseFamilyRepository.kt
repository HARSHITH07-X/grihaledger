package com.example.data.firebase

import com.example.data.fake.FakeSeedData
import com.example.domain.model.Category
import com.example.domain.model.Family
import com.example.domain.model.Member
import com.example.domain.model.Role
import com.example.domain.model.TransactionType
import com.example.domain.repository.FamilyRepository
import com.example.domain.repository.SessionRepository
import com.example.domain.rules.Permissions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirebaseFamilyRepository @Inject constructor(
  private val auth: FirebaseAuth,
  private val db: FirebaseFirestore,
  private val sessionRepository: SessionRepository,
) : FamilyRepository {

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  override fun observeFamily(): Flow<Family?> =
    sessionRepository.observeUserProfile().flatMapLatest { profile ->
      val familyId = profile?.familyId
      if (profile == null || familyId.isNullOrBlank()) {
        flowOf(null)
      } else {
        val docRef = db.collection("families").document(familyId)
        docRef
          .snapshots()
          .map { snap ->
            if (!snap.exists()) null
            else {
              val createdAt =
                snap.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                  ?.toDate()
                  ?.time ?: 0L
              Family(
                id = snap.id,
                name = snap.getString("name") ?: "Family",
                currencyCode = snap.getString("currencyCode") ?: "INR",
                createdAtEpochMillis = createdAt,
                ledgerEpoch = (snap.getLong("ledgerEpoch") ?: 0L).toInt(),
              )
            }
          }
          .catch { error ->
            val fsError = error.unwrapFirestoreException()
            if (fsError == null && error is CancellationException) throw error
            if (auth.currentUser?.uid == profile.uid) {
              handleFirestoreError(fsError ?: error, OperationType.GET, docRef.path, auth)
            }
            emit(null)
          }
      }
    }

  override fun observeMembers(): Flow<List<Member>> =
    sessionRepository.observeUserProfile().flatMapLatest { profile ->
      val familyId = profile?.familyId
      if (profile == null || familyId.isNullOrBlank()) {
        flowOf(emptyList())
      } else {
        val collectionRef = db.collection("families").document(familyId).collection("members")
        collectionRef
          .snapshots()
          .map { snapshot ->
            snapshot.documents
              .mapNotNull { it.toDomainMemberOrNull() }
              .sortedWith(compareBy<Member> { it.role.ordinal }.thenBy { it.displayName })
          }
          .catch { error ->
            val fsError = error.unwrapFirestoreException()
            if (fsError == null && error is CancellationException) throw error
            if (auth.currentUser?.uid == profile.uid) {
              handleFirestoreError(fsError ?: error, OperationType.LIST, collectionRef.path, auth)
            }
            emit(emptyList())
          }
      }
    }

  override fun observeCategories(): Flow<List<Category>> =
    sessionRepository.observeUserProfile().flatMapLatest { profile ->
      val familyId = profile?.familyId
      if (profile == null || familyId.isNullOrBlank()) {
        flowOf(emptyList())
      } else {
        val collectionRef = db.collection("families").document(familyId).collection("categories")
        collectionRef
          .snapshots()
          .map { snapshot ->
            snapshot.documents
              .mapNotNull { doc ->
                val name = doc.getString("name") ?: return@mapNotNull null
                val type =
                  if (doc.getString("type") == "income") TransactionType.INCOME
                  else TransactionType.EXPENSE
                Category(
                  id = doc.id,
                  familyId = doc.getString("familyId") ?: familyId,
                  name = name,
                  type = type,
                  iconKey = doc.getString("iconKey") ?: "category",
                  colorHex = doc.getLong("colorHex") ?: 0xFF1E9E6AL,
                  isDefault = doc.getBoolean("isDefault") ?: true,
                  sortOrder = (doc.getLong("sortOrder") ?: 0L).toInt(),
                )
              }
              .sortedBy { it.sortOrder }
          }
          .catch { error ->
            val fsError = error.unwrapFirestoreException()
            if (fsError == null && error is CancellationException) throw error
            if (auth.currentUser?.uid == profile.uid) {
              handleFirestoreError(fsError ?: error, OperationType.LIST, collectionRef.path, auth)
            }
            emit(emptyList())
          }
      }
    }

  override suspend fun createFamily(familyName: String): Result<String> {
    val trimmedName = familyName.trim()
    if (trimmedName.isEmpty() || trimmedName.length > 60) {
      return Result.failure(IllegalArgumentException("Enter a family name between 1 and 60 characters."))
    }

    val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in."))
    val uid = user.uid
    val familyId = "fam_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val displayName =
      user.displayName?.takeIf { it.isNotBlank() }
        ?: user.email?.substringBefore('@')
        ?: "Family Admin"
    val email = user.email?.takeIf { it.isNotBlank() } ?: "$uid@users.local"

    val familyRef = db.collection("families").document(familyId)
    val memberRef = familyRef.collection("members").document(uid)
    val userRef = db.collection("users").document(uid)

    return try {
      val batch = db.batch()
      batch.set(
        familyRef,
        mapOf(
          "name" to trimmedName,
          "currencyCode" to "INR",
          "createdByUid" to uid,
          "adminCount" to 1L,
          "ledgerEpoch" to 0L,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.set(
        memberRef,
        mapOf(
          "uid" to uid,
          "familyId" to familyId,
          "displayName" to displayName.take(80),
          "email" to email.take(200),
          "role" to "admin",
          "isActive" to true,
          "joinedAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      FakeSeedData.categories.forEach { category ->
        val catRef = familyRef.collection("categories").document(category.id)
        batch.set(
          catRef,
          mapOf(
            "name" to category.name,
            "familyId" to familyId,
            "type" to if (category.type == TransactionType.INCOME) "income" else "expense",
            "iconKey" to category.iconKey,
            "colorHex" to category.colorHex,
            "isDefault" to category.isDefault,
            "sortOrder" to category.sortOrder.toLong(),
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      val userSnapshot = userRef.get().await()
      if (userSnapshot.exists()) {
        batch.update(
          userRef,
          mapOf(
            "familyId" to familyId,
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
            "familyId" to familyId,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      batch.commit().await()
      Result.success(familyId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, familyRef.path, auth)
      Result.failure(IllegalStateException(e.toFriendlyFirestoreMessage("Could not create family.")))
    }
  }

  override suspend fun changeMemberRole(
    familyId: String,
    targetUid: String,
    newRole: Role,
  ): Result<Unit> {
    val actorUid = requireUserId()
    return try {
      val members = observeMembers().first()
      val actor =
        members.firstOrNull { it.uid == actorUid }
          ?: return Result.failure(IllegalStateException("Your member record was not found."))
      val target =
        members.firstOrNull { it.uid == targetUid }
          ?: return Result.failure(IllegalStateException("Member not found."))
      val activeAdmins = members.count { it.isActive && it.role == Role.ADMIN }

      if (!Permissions.canChangeMemberRole(actor, target, newRole, activeAdmins)) {
        return Result.failure(
          IllegalStateException("Cannot change role: either unauthorized or protected last admin.")
        )
      }

      val familyRef = db.collection("families").document(familyId)
      val memberRef = familyRef.collection("members").document(targetUid)
      val batch = db.batch()
      batch.update(
        memberRef,
        mapOf(
          "role" to newRole.toFirestoreValue(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      val delta =
        when {
          target.role != Role.ADMIN && newRole == Role.ADMIN -> 1L
          target.role == Role.ADMIN && newRole != Role.ADMIN -> -1L
          else -> 0L
        }
      if (delta != 0L) {
        batch.update(
          familyRef,
          mapOf(
            "adminCount" to FieldValue.increment(delta),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "families/$familyId/members/$targetUid", auth)
      Result.failure(IllegalStateException(e.toFriendlyFirestoreMessage("Could not update role.")))
    }
  }

  override suspend fun removeMember(familyId: String, targetUid: String): Result<Unit> {
    val actorUid = requireUserId()
    return try {
      val members = observeMembers().first()
      val actor =
        members.firstOrNull { it.uid == actorUid }
          ?: return Result.failure(IllegalStateException("Your member record was not found."))
      val target =
        members.firstOrNull { it.uid == targetUid }
          ?: return Result.failure(IllegalStateException("Member not found."))
      val activeAdmins = members.count { it.isActive && it.role == Role.ADMIN }

      if (!Permissions.canRemoveMember(actor, target, activeAdmins)) {
        return Result.failure(
          IllegalStateException("Cannot remove member: unauthorized or last admin protected.")
        )
      }

      val familyRef = db.collection("families").document(familyId)
      val memberRef = familyRef.collection("members").document(targetUid)
      val batch = db.batch()
      batch.delete(memberRef)
      if (target.role == Role.ADMIN) {
        batch.update(
          familyRef,
          mapOf(
            "adminCount" to FieldValue.increment(-1L),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, "families/$familyId/members/$targetUid", auth)
      Result.failure(IllegalStateException(e.toFriendlyFirestoreMessage("Could not remove member.")))
    }
  }

  override suspend fun clearRemovedFamilyLink(): Result<Unit> {
    val uid = requireUserId()
    val userRef = db.collection("users").document(uid)
    return try {
      userRef
        .update(
          mapOf(
            "familyId" to null,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, userRef.path, auth)
      Result.failure(e)
    }
  }
}

internal fun Role.toFirestoreValue(): String =
  when (this) {
    Role.ADMIN -> "admin"
    Role.MEMBER -> "member"
    Role.VIEWER -> "viewer"
  }
