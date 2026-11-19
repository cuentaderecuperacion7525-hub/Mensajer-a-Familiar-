package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
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
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "GoogleAuthHelper"

sealed interface GoogleAuthResult {
    data class Success(val user: FirebaseUser) : GoogleAuthResult
    data object Cancelled : GoogleAuthResult
    data class Error(val message: String) : GoogleAuthResult
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

object GoogleAuthHelper {

    suspend fun attemptSilentSignIn(activityContext: Context): FirebaseUser? {
        val current = runCatching { Firebase.auth.currentUser }.getOrNull()
        if (current != null) return current
        val pendingUser = runCatching {
            Firebase.auth.pendingAuthResult?.await()?.user
        }.getOrNull()
        if (pendingUser != null) return pendingUser
        val webClientId = runCatching {
            activityContext.getString(R.string.default_web_client_id)
        }.getOrDefault("")
        if (webClientId.isBlank()) return null

        return try {
            val credentialManager = CredentialManager.create(activityContext)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val response = credentialManager.getCredential(
                request = request,
                context = activityContext
            )
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCred = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = Firebase.auth.signInWithCredential(firebaseCred).await()
                authResult.user
            } else null
        } catch (_: NoCredentialException) {
            null
        } catch (_: GetCredentialCancellationException) {
            null
        } catch (e: Exception) {
            Log.d(TAG, "Silent Google Sign-In skipped: ${e.message}")
            null
        }
    }

    suspend fun signInWithGoogle(activityContext: Context): GoogleAuthResult {
        return try {
            val webClientId = activityContext.getString(R.string.default_web_client_id)
            val credentialManager = CredentialManager.create(activityContext)
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val response = credentialManager.getCredential(
                request = request,
                context = activityContext
            )
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCred = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = Firebase.auth.signInWithCredential(firebaseCred).await()
                val user = authResult.user
                if (user != null) {
                    GoogleAuthResult.Success(user)
                } else {
                    GoogleAuthResult.Error("No se pudo iniciar sesión con tu cuenta de Google.")
                }
            } else {
                GoogleAuthResult.Error("Tipo de credencial incompatible.")
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleAuthResult.Cancelled
        } catch (credErr: GetCredentialException) {
            Log.w(TAG, "Credential Manager unavailable (${credErr.type}), falling back to browser Google Sign-In", credErr)
            signInWithGoogleViaBrowser(activityContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed, attempting browser fallback", e)
            val fallback = signInWithGoogleViaBrowser(activityContext)
            if (fallback is GoogleAuthResult.Success || fallback is GoogleAuthResult.Cancelled) {
                fallback
            } else {
                GoogleAuthResult.Error(e.localizedMessage ?: "Error al iniciar sesión con Google.")
            }
        }
    }

    suspend fun signInWithGoogleViaBrowser(activityContext: Context): GoogleAuthResult {
        val activity = activityContext.findActivity()
            ?: return GoogleAuthResult.Error("No se pudo abrir el navegador para iniciar sesión.")
        return try {
            val provider = OAuthProvider.newBuilder("google.com")
                .addCustomParameter("prompt", "select_account")
                .build()
            val authResult = Firebase.auth
                .startActivityForSignInWithProvider(activity, provider)
                .await()
            val user = authResult.user ?: Firebase.auth.currentUser
            if (user != null) {
                GoogleAuthResult.Success(user)
            } else {
                GoogleAuthResult.Error("No se pudo completar el inicio de sesión desde el navegador.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.localizedMessage.orEmpty()
            if (msg.contains("canceled", ignoreCase = true) ||
                msg.contains("cancelled", ignoreCase = true)
            ) {
                GoogleAuthResult.Cancelled
            } else {
                Log.e(TAG, "Browser Google Sign-In failed", e)
                GoogleAuthResult.Error(msg.ifBlank { "Error al iniciar sesión desde el navegador." })
            }
        }
    }

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return ctx as? Activity
    }

    suspend fun signOut(context: Context) {
        try {
            Firebase.auth.signOut()
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "Sign-out credential cleanup warning", e)
        }
    }
}
