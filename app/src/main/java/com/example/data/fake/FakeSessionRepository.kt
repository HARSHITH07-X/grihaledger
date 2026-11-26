package com.example.data.fake

import com.example.domain.model.Member
import com.example.domain.model.UserProfile
import com.example.domain.repository.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class FakeSessionRepository @Inject constructor(
  private val store: FakeWorkspaceStore,
) : SessionRepository {

  override fun observeUserProfile(): Flow<UserProfile?> =
    combine(store.authUser, store.userProfiles) { authUser, profiles ->
      if (authUser == null) null
      else
        profiles[authUser.uid]
          ?: UserProfile(
            uid = authUser.uid,
            displayName = authUser.displayName,
            email = authUser.email,
            familyId = null,
          )
    }

  override fun observeCurrentMember(): Flow<Member?> =
    combine(
      observeUserProfile(),
      store.membersByFamily,
      store.debugPreviewMemberUid,
    ) { profile, membersByFamily, debugUid ->
      val familyId = profile?.familyId ?: return@combine null
      val effectiveUid = debugUid ?: profile.uid
      membersByFamily[familyId].orEmpty().firstOrNull { it.uid == effectiveUid && it.isActive }
    }

  override fun observeAvailableDebugMembers(): Flow<List<Member>> =
    combine(observeUserProfile(), store.membersByFamily) { profile, membersByFamily ->
      val familyId = profile?.familyId ?: FakeSeedData.FAMILY_ID
      membersByFamily[familyId].orEmpty().filter { it.isActive }
    }

  override suspend fun switchDebugMember(uid: String) {
    val currentUid = store.authUser.value?.uid
    val activeFamilyId =
      currentUid?.let { store.userProfiles.value[it]?.familyId } ?: FakeSeedData.FAMILY_ID
    if (store.membersByFamily.value[activeFamilyId].orEmpty().any { it.uid == uid }) {
      store.debugPreviewMemberUid.value = uid
    }
  }
}
