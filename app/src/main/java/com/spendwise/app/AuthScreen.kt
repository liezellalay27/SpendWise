package com.spendwise.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AuthScreen(model: AuthViewModel) {
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("SpendWise", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (isSignUp) "Create your account to start your plan."
            else "Welcome back. Let’s make your money clear.",
            color = Color.Gray
        )
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        model.errorMessage?.let {
            Text(it, color = Color(0xFFB3261E), modifier = Modifier.padding(bottom = 12.dp))
        }
        if (model.requiresEmailConfirmation) {
            Text(
                "Account created. Check your email to confirm your account, then sign in.",
                color = Color(0xFF315C4A),
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        Button(
            onClick = {
                if (isSignUp) model.signUp(email, password) else model.signIn(email, password)
            },
            enabled = !model.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (model.isLoading) CircularProgressIndicator(strokeWidth = 2.dp)
            else Text(if (isSignUp) "Create account" else "Log in")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                isSignUp = !isSignUp
                model.clearError()
            },
            enabled = !model.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isSignUp) "Already have an account? Log in" else "New to SpendWise? Sign up")
        }
    }
}
