package com.example.di

import com.example.data.fake.FakeAiAdvisorRepository
import com.example.data.fake.FakeAudioTranscriptionRepository
import com.example.data.fake.FakeAuthRepository
import com.example.data.fake.FakeFamilyRepository
import com.example.data.fake.FakeInviteRepository
import com.example.data.fake.FakeLedgerRepository
import com.example.data.fake.FakeLiveVoiceAdvisorRepository
import com.example.data.fake.FakeSessionRepository
import com.example.domain.repository.AiAdvisorRepository
import com.example.domain.repository.AudioTranscriptionRepository
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.FamilyRepository
import com.example.domain.repository.InviteRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.LiveVoiceAdvisorRepository
import com.example.domain.repository.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
  components = [SingletonComponent::class],
  replaces = [RepositoryModule::class],
)
abstract class FakeTestRepositoryModule {

  @Binds
  @Singleton
  abstract fun bindAuthRepository(impl: FakeAuthRepository): AuthRepository

  @Binds
  @Singleton
  abstract fun bindSessionRepository(impl: FakeSessionRepository): SessionRepository

  @Binds
  @Singleton
  abstract fun bindFamilyRepository(impl: FakeFamilyRepository): FamilyRepository

  @Binds
  @Singleton
  abstract fun bindInviteRepository(impl: FakeInviteRepository): InviteRepository

  @Binds
  @Singleton
  abstract fun bindLedgerRepository(impl: FakeLedgerRepository): LedgerRepository

  @Binds
  @Singleton
  abstract fun bindAiAdvisorRepository(impl: FakeAiAdvisorRepository): AiAdvisorRepository

  @Binds
  @Singleton
  abstract fun bindAudioTranscriptionRepository(
    impl: FakeAudioTranscriptionRepository,
  ): AudioTranscriptionRepository

  @Binds
  @Singleton
  abstract fun bindLiveVoiceAdvisorRepository(
    impl: FakeLiveVoiceAdvisorRepository,
  ): LiveVoiceAdvisorRepository
}
