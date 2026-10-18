package com.spendwise.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SpendWiseTheme { SpendWiseApp() } }
    }
}

@Composable
private fun SpendWiseApp(model: SpendWiseViewModel = viewModel()) {
    Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        SpendWiseShell(model)
    }
}

@Composable
private fun SpendWiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
