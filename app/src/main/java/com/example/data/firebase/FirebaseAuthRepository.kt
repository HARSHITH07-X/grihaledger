package com.example.data.firebase

import com.example.domain.model.AuthUser
import com.example.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseAuthRepository @Inject constructor(
  private val auth: FirebaseAuth,
  private val db: FirebaseFirestore,
) : AuthRepository {

  override fun observeAuthState(): Flow<AuthUser?> =
    callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
          trySend(firebaseAuth.currentUser?.toAuthUser())
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser?.toAuthUser())
        awaitClose { auth.removeAuthStateListener(listener) }
      }
      .distinctUntilChanged()

  override fun currentUser(): AuthUser? = auth.currentUser?.toAuthUser()

  override suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser> =
    try {
      val credential = GoogleAuthProvider.getCredential(idToken, null)
      val result = auth.signInWithCredential(credential).await()
      val firebaseUser =
        result.user ?: return Result.failure(IllegalStateException("Google sign-in returned no user."))
      ensureUserProfileExists(firebaseUser)
      Result.success(firebaseUser.toAuthUser())
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }

  override suspend fun signOut() {
    auth.signOut()
  }

  internal suspend fun ensureUserProfileExists(firebaseUser: FirebaseUser) {
    val userRef = db.collection("users").document(firebaseUser.uid)
    try {
      val snapshot = userRef.get().await()
      val displayName =
        firebaseUser.displayName?.takeIf { it.isNotBlank() }
          ?: firebaseUser.email?.substringBefore('@')
          ?: "Family Member"
      val email = firebaseUser.email?.takeIf { it.isNotBlank() } ?: "${firebaseUser.uid}@users.local"

      if (!snapshot.exists()) {
        val payload =
          mapOf(
            "uid" to firebaseUser.uid,
            "displayName" to displayName.take(80),
            "email" to email.take(200),
            "familyId" to null,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        userRef.set(payload).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, userRef.path, auth)
      throw e
    }
  }

  private fun FirebaseUser.toAuthUser(): AuthUser =
    AuthUser(
      uid = uid,
      displayName =
        displayName?.takeIf { it.isNotBlank() }
          ?: email?.substringBefore('@')
          ?: "Family Member",
      email = email.orEmpty(),
    )
}
