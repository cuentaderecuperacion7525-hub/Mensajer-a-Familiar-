package com.example.data.remote

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
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
        val current = Firebase.auth.currentUser
        if (current != null) return current
        val webClientId = activityContext.getString(R.string.default_web_client_id)
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
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            GoogleAuthResult.Error(e.localizedMessage ?: "Error al iniciar sesión con Google.")
        }
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
