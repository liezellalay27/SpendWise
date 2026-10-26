package com.spendwise.app

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.net.ssl.SSLHandshakeException

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("auth", Context.MODE_PRIVATE)

    var isAuthenticated by mutableStateOf(preferences.getString("access_token", null) != null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var requiresEmailConfirmation by mutableStateOf(false)
        private set
    var displayName by mutableStateOf(preferences.getString("display_name", null).orEmpty())
        private set
    var email by mutableStateOf(preferences.getString("email", null).orEmpty())
        private set

    fun signIn(email: String, password: String) {
        authenticate(email = email, password = password, isSignUp = false)
    }

    private fun String.jwtSubject(): String? =
        runCatching {
            val payload = split(".")[1]
            val json = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
            Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
        }.getOrNull()

    fun signUp(name: String, email: String, password: String) {
        authenticate(name.trim(), email, password, isSignUp = true)
    }

    fun signOut() {
        preferences.edit().clear().apply()
        isAuthenticated = false
        displayName = ""
        email = ""
    }

    fun clearError() {
        errorMessage = null
    }

    fun handleAuthCallback(uri: Uri) {
        val fragment = uri.fragment.orEmpty()
        val values = fragment.split("&")
            .mapNotNull { part ->
                val separator = part.indexOf("=")
                if (separator <= 0) null
                else part.substring(0, separator) to Uri.decode(part.substring(separator + 1))
            }
            .toMap()
        val accessToken = values["access_token"]
        if (accessToken.isNullOrBlank()) {
            errorMessage = "Email confirmation did not return a valid session. Try logging in again."
            return
        }
        preferences.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", values["refresh_token"])
            .putString("user_id", accessToken.jwtSubject())
            .apply()
        isAuthenticated = true
        requiresEmailConfirmation = false
        errorMessage = null
    }

    private fun authenticate(name: String = "", email: String, password: String, isSignUp: Boolean) {
        if (isSignUp && name.isBlank()) {
            errorMessage = "Enter your name."
            return
        }
        if (email.isBlank() || !email.contains("@")) {
            errorMessage = "Enter a valid email address."
            return
        }
        if (password.length < 6) {
            errorMessage = "Password must be at least 6 characters."
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            requiresEmailConfirmation = false
            runCatching {
                if (isSignUp) SupabaseAuthApi.signUp(name, email.trim(), password)
                else SupabaseAuthApi.signIn(email.trim(), password)
            }.onSuccess { response ->
                if (!response.access_token.isNullOrBlank()) {
                    preferences.edit()
                        .putString("access_token", response.access_token)
                        .putString("refresh_token", response.refresh_token)
                        .putString("user_id", response.user?.id)
                        .putString("email", response.user?.email ?: email.trim())
                        .putString("display_name", response.user?.user_metadata?.get("display_name") ?: name)
                        .apply()
                    displayName = response.user?.user_metadata?.get("display_name")
                        ?.takeIf { it.isNotBlank() } ?: name
                    this@AuthViewModel.email = response.user?.email ?: email.trim()
                    isAuthenticated = true
                } else if (isSignUp) {
                    requiresEmailConfirmation = true
                } else {
                    errorMessage = "Authentication did not return a session."
                }
            }.onFailure {
                errorMessage = it.toAuthMessage()
            }
            isLoading = false
        }
    }

    private fun Throwable.toAuthMessage(): String {
        var current: Throwable? = this
        while (current != null) {
            if (current is SSLHandshakeException ||
                current.message?.contains("Chain validation failed", ignoreCase = true) == true
            ) {
                return "Secure connection failed. Set the emulator date and time automatically, then cold boot the emulator and try again."
            }
            current = current.cause
        }
        return when (this) {
            is HttpException -> when (code()) {
                400 -> "Supabase rejected the request. The email may not be confirmed yet, or the link/session may have expired."
                422 -> "Supabase rejected the email or password. Check the values and try again."
                else -> "Supabase returned HTTP ${code()}. Try again."
            }
            is IOException -> "Could not reach Supabase. Check the emulator internet connection."
            else -> message ?: "Unable to contact Supabase."
        }
    }
}
