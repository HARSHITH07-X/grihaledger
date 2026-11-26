package com.example.di

import com.example.data.ai.GeminiAiAdvisorRepository
import com.example.data.ai.GeminiAudioTranscriptionRepository
import com.example.data.ai.GeminiLiveAdvisorRepository
import com.example.data.firebase.FirebaseAuthRepository
import com.example.data.firebase.FirebaseFamilyRepository
import com.example.data.firebase.FirebaseInviteRepository
import com.example.data.firebase.FirebaseLedgerRepository
import com.example.data.firebase.FirebaseSessionRepository
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
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

  @Binds
  @Singleton
  abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

  @Binds
  @Singleton
  abstract fun bindSessionRepository(impl: FirebaseSessionRepository): SessionRepository

  @Binds
  @Singleton
  abstract fun bindFamilyRepository(impl: FirebaseFamilyRepository): FamilyRepository

  @Binds
  @Singleton
  abstract fun bindInviteRepository(impl: FirebaseInviteRepository): InviteRepository

  @Binds
  @Singleton
  abstract fun bindLedgerRepository(impl: FirebaseLedgerRepository): LedgerRepository

  @Binds
  @Singleton
  abstract fun bindAiAdvisorRepository(impl: GeminiAiAdvisorRepository): AiAdvisorRepository

  @Binds
  @Singleton
  abstract fun bindAudioTranscriptionRepository(
    impl: GeminiAudioTranscriptionRepository,
  ): AudioTranscriptionRepository

  @Binds
  @Singleton
  abstract fun bindLiveVoiceAdvisorRepository(
    impl: GeminiLiveAdvisorRepository,
  ): LiveVoiceAdvisorRepository
}
