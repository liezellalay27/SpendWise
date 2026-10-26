package com.spendwise.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private lateinit var authModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authModel = androidx.lifecycle.ViewModelProvider(this)[AuthViewModel::class.java]
        handleAuthIntent(intent)
        setContent { SpendWiseTheme { SpendWiseApp(authModel = authModel) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        if (intent?.data?.scheme == "spendwise") {
            authModel.handleAuthCallback(intent.data!!)
        }
    }
}

@Composable
private fun SpendWiseApp(
    model: SpendWiseViewModel = viewModel(),
    authModel: AuthViewModel = viewModel()
) {
    Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (authModel.isAuthenticated) {
            SpendWiseShell(
                model = model,
                displayName = authModel.displayName,
                email = authModel.email,
                onSignOut = authModel::signOut
            )
        } else {
            AuthScreen(authModel)
        }
    }
}

@Composable
private fun SpendWiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
