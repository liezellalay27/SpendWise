package com.spendwise.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
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

    fun signIn(email: String, password: String) {
        authenticate(email, password, isSignUp = false)
    }

    fun signUp(email: String, password: String) {
        authenticate(email, password, isSignUp = true)
    }

    fun signOut() {
        preferences.edit().clear().apply()
        isAuthenticated = false
    }

    fun clearError() {
        errorMessage = null
    }

    private fun authenticate(email: String, password: String, isSignUp: Boolean) {
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
                if (isSignUp) SupabaseAuthApi.signUp(email.trim(), password)
                else SupabaseAuthApi.signIn(email.trim(), password)
            }.onSuccess { response ->
                if (!response.access_token.isNullOrBlank()) {
                    preferences.edit()
                        .putString("access_token", response.access_token)
                        .putString("refresh_token", response.refresh_token)
                        .putString("email", response.user?.email ?: email.trim())
                        .apply()
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
            is IOException -> "Could not reach Supabase. Check the emulator internet connection."
            else -> message ?: "Unable to contact Supabase."
        }
    }
}
