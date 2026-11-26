package com.example.data.fake

import com.example.domain.model.AuthUser
import com.example.domain.model.UserProfile
import com.example.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class FakeAuthRepository @Inject constructor(
  private val store: FakeWorkspaceStore,
) : AuthRepository {

  override fun observeAuthState(): Flow<AuthUser?> = store.authUser.asStateFlow()

  override fun currentUser(): AuthUser? = store.authUser.value

  override suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser> {
    val user =
      AuthUser(
        uid = "uid_google_${idToken.take(6).ifBlank { "user" }}",
        displayName = "Priya Sharma",
        email = "priya@sharma.family",
      )
    store.authUser.value = user
    val currentProfiles = store.userProfiles.value
    if (!currentProfiles.containsKey(user.uid)) {
      store.userProfiles.value =
        currentProfiles +
          (user.uid to
            UserProfile(
              uid = user.uid,
              displayName = user.displayName,
              email = user.email,
              familyId = null,
            ))
    }
    return Result.success(user)
  }

  override suspend fun signOut() {
    store.debugPreviewMemberUid.value = null
    store.authUser.value = null
  }

  fun setSignedInUser(user: AuthUser?, familyId: String? = null) {
    store.debugPreviewMemberUid.value = null
    store.authUser.value = user
    if (user != null) {
      store.userProfiles.value =
        store.userProfiles.value +
          (user.uid to
            UserProfile(
              uid = user.uid,
              displayName = user.displayName,
              email = user.email,
              familyId = familyId,
            ))
    }
  }
}
