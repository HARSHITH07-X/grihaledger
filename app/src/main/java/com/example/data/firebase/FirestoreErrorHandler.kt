package com.example.data.firebase

import android.util.Log
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
  CREATE("create"),
  UPDATE("update"),
  DELETE("delete"),
  LIST("list"),
  GET("get"),
  WRITE("write"),
}

fun handleFirestoreError(
  exception: Throwable,
  operationType: OperationType,
  path: String?,
  auth: FirebaseAuth,
): String {
  val causes = generateSequence(exception) { it.cause }
  val rootError =
    causes.firstOrNull { it is FirebaseFirestoreException }
      ?: causes.firstOrNull { it !is CancellationException }
      ?: exception

  val currentUser = auth.currentUser
  val providerInfoList =
    currentUser?.providerData?.map { provider ->
      JSONObject().apply {
        put("providerId", provider.providerId)
        put("email", provider.email)
      }
    } ?: emptyList()

  val authInfoJson =
    JSONObject().apply {
      put("userId", currentUser?.uid)
      put("email", currentUser?.email)
      put("emailVerified", currentUser?.isEmailVerified)
      put("tenantId", currentUser?.tenantId)
      put("providerInfo", JSONArray(providerInfoList))
    }

  val errorInfoJson =
    JSONObject().apply {
      put("error", rootError.message ?: rootError.toString())
      put("operationType", operationType.value)
      put("path", path)
      put("authInfo", authInfoJson)
    }

  val jsonString = errorInfoJson.toString()
  Log.e("FirestoreError", "Firestore Error: $jsonString")
  if (!BuildConfig.DEBUG) {
    try {
      val crashlytics = Firebase.crashlytics
      crashlytics.setCustomKey("firestore_operation", operationType.value)
      crashlytics.setCustomKey("firestore_collection", sanitizeCollectionPath(path))
      crashlytics.recordException(rootError)
    } catch (_: Exception) {
      // Ignore if Crashlytics is unavailable.
    }
  }
  return jsonString
}

private fun sanitizeCollectionPath(path: String?): String {
  if (path.isNullOrBlank()) return "unknown"
  val segments = path.split('/').filter { it.isNotBlank() }
  return segments
    .filterIndexed { index, _ -> index % 2 == 0 }
    .joinToString("/")
    .ifBlank { "unknown" }
}

fun Throwable.unwrapFirestoreException(): FirebaseFirestoreException? {
  var current: Throwable? = this
  while (current != null) {
    if (current is FirebaseFirestoreException) return current
    current = current.cause
  }
  return null
}

fun Throwable.toFriendlyFirestoreMessage(fallback: String): String {
  val firestoreError = unwrapFirestoreException()
  return when (firestoreError?.code) {
    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
      "You don't have permission for this action, or the item is no longer valid."
    FirebaseFirestoreException.Code.UNAVAILABLE ->
      "You appear to be offline. Changes will sync once connection returns."
    FirebaseFirestoreException.Code.NOT_FOUND ->
      "The requested record could not be found."
    else -> firestoreError?.localizedMessage ?: localizedMessage ?: fallback
  }
}
