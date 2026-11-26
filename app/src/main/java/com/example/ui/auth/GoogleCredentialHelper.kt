package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

object GoogleCredentialHelper {

  private const val TAG = "Auth"

  private tailrec fun Context.findActivity(): Activity? =
    when (this) {
      is Activity -> this
      is ContextWrapper -> baseContext.findActivity()
      else -> null
    }

  suspend fun attemptSilentAutoSignIn(context: Context): String? {
    val serverClientId =
      runCatching { context.getString(R.string.default_web_client_id) }.getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: return null

    val credentialContext: Context = context.findActivity() ?: context
    return try {
      val credentialManager = CredentialManager.create(credentialContext)
      val googleIdOption =
        GetGoogleIdOption.Builder()
          .setFilterByAuthorizedAccounts(true)
          .setServerClientId(serverClientId)
          .setAutoSelectEnabled(true)
          .build()
      val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
      val result = credentialManager.getCredential(context = credentialContext, request = request)
      extractGoogleIdToken(result.credential)
    } catch (e: CancellationException) {
      throw e
    } catch (_: NoCredentialException) {
      null
    } catch (_: GetCredentialCancellationException) {
      null
    } catch (_: GetCredentialException) {
      null
    } catch (_: Throwable) {
      null
    }
  }

  suspend fun requestInteractiveGoogleSignIn(context: Context): Result<String> {
    val serverClientId =
      runCatching { context.getString(R.string.default_web_client_id) }.getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: return Result.failure(
          IllegalStateException(
            "Google Sign-In is not configured. Add google-services.json and rebuild."
          )
        )

    val credentialContext: Context = context.findActivity() ?: context
    return try {
      val credentialManager = CredentialManager.create(credentialContext)
      val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
      val request =
        GetCredentialRequest.Builder().addCredentialOption(signInWithGoogleOption).build()
      val result = credentialManager.getCredential(context = credentialContext, request = request)
      val idToken = extractGoogleIdToken(result.credential)
      if (idToken != null) {
        Result.success(idToken)
      } else {
        Result.failure(IllegalStateException("Could not read Google ID token from credential."))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: GetCredentialCancellationException) {
      Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
      Result.failure(SignInCancelledException())
    } catch (_: NoCredentialException) {
      Result.failure(
        IllegalStateException(
          "No Google account is signed in on this device. Add a Google account in Settings and try again."
        )
      )
    } catch (e: GetCredentialException) {
      Log.e(TAG, "Google Sign-In credential error", e)
      Result.failure(
        IllegalStateException(
          e.errorMessage?.toString()?.takeIf { it.isNotBlank() }
            ?: "Google Sign-In could not be completed. Check Play Services and SHA-1 setup."
        )
      )
    } catch (e: Exception) {
      Log.e(TAG, "Google Sign-In failed unexpectedly", e)
      Result.failure(
        IllegalStateException(e.localizedMessage ?: "Google Sign-In failed unexpectedly.")
      )
    }
  }

  suspend fun clearCredentialState(context: Context) {
    withContext(NonCancellable) {
      runCatching {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
      }
    }
  }

  private fun extractGoogleIdToken(credential: Credential): String? {
    if (credential is CustomCredential &&
      credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
      return try {
        GoogleIdTokenCredential.createFrom(credential.data).idToken
      } catch (_: GoogleIdTokenParsingException) {
        null
      }
    }
    return null
  }
}

class SignInCancelledException : Exception("Sign-in cancelled.")
