package com.example

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.crashlytics
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GrihaLedgerApp : Application() {
  override fun onCreate() {
    super.onCreate()
    try {
      if (FirebaseApp.getApps(this).isNotEmpty()) {
        Firebase.crashlytics.isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
      }
    } catch (_: Exception) {
      // Safe no-op when Firebase is not initialized (e.g. isolated JVM unit tests).
    }
  }
}
