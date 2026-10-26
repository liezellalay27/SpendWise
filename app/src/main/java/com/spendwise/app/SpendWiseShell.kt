package com.spendwise.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private data class AppTab(val label: String, val marker: String)

@Composable
fun SpendWiseShell(
    model: SpendWiseViewModel,
    displayName: String,
    email: String,
    onSignOut: () -> Unit
) {
    LaunchedEffect(Unit) {
        model.syncWithBackend()
    }
    val tabs = listOf(
        AppTab("Home", "⌂"),
        AppTab("Activity", "↕"),
        AppTab("Savings", "◇"),
        AppTab("Profile", "○")
    )
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Text(tab.marker) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> DashboardScreen(model, displayName, Modifier.padding(padding))
            1 -> ActivityScreen(model, Modifier.padding(padding))
            2 -> SavingsScreen(model, Modifier.padding(padding))
            else -> ProfileScreen(model, displayName, email, Modifier.padding(padding), onSignOut)
        }
    }
}
