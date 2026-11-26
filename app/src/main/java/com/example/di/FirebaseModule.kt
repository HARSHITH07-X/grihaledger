package com.example.di

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

  @Provides
  @Singleton
  fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

  @Provides
  @Singleton
  fun provideFirebaseFirestore(
    @ApplicationContext context: Context,
  ): FirebaseFirestore {
    val databaseId = context.getString(R.string.firestore_database_id)
    val firestore = FirebaseFirestore.getInstance(databaseId)
    firestore.firestoreSettings =
      FirebaseFirestoreSettings.Builder()
        .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
        .build()
    return firestore
  }
}
