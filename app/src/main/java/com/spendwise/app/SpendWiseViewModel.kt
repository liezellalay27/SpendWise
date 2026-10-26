package com.spendwise.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

data class BudgetCycle(
    val budget: Long = 30_000_00,
    val lowBalanceThreshold: Long = 5_000_00,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now().plusDays(30)
)

data class ActivityItem(val label: String, val amount: Long)
data class ExtraBudget(val name: String, val amount: Long)

class SpendWiseViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val preferences = application.getSharedPreferences("spendwise", Context.MODE_PRIVATE)

    val hasConfiguredBudget: Boolean
        get() = preferences.contains("start_date") &&
            preferences.contains("end_date") &&
            preferences.contains("budget")

    var cycle by mutableStateOf(
        BudgetCycle(
            budget = preferences.getLong("budget", 30_000_00),
            startDate = preferences.getString("start_date", null)
                ?.let(LocalDate::parse) ?: LocalDate.now(),
            endDate = preferences.getString("end_date", null)
                ?.let(LocalDate::parse) ?: LocalDate.now().plusDays(30)
        )
    )
        private set
    var spent by mutableStateOf(preferences.getLong("spent", 0))
        private set
    var incomeAdded by mutableStateOf(preferences.getLong("income", 0))
        private set
    var emergencyFund by mutableStateOf(preferences.getLong("emergency", 0))
        private set
    var emergencyAllocationPercent by mutableStateOf(
        preferences.getInt("emergency_percent", 10)
    )
        private set
    var recentActivity by mutableStateOf(listOf<ActivityItem>())
        private set
    var extraBudgets by mutableStateOf(loadExtraBudgets())
        private set
    var isSyncing by mutableStateOf(false)
        private set
    var syncMessage by mutableStateOf<String?>(null)
        private set

    val daysRemaining: Long
        get() = maxOf(1, ChronoUnit.DAYS.between(LocalDate.now(), cycle.endDate) + 1)

    val remainingBalance: Long
        get() = cycle.budget + extraBudgets.sumOf { it.amount } + incomeAdded - spent - emergencyFund

    val safeToSpendToday: Long
        get() = maxOf(0, remainingBalance / daysRemaining)

    fun addExpense(name: String, amount: Long) {
        if (amount <= 0) return
        spent += amount
        val expenseName = name.trim().ifBlank { "Expense" }
        recentActivity = (listOf(ActivityItem(expenseName, -amount)) + recentActivity).take(5)
        persist()
        syncAction("Saving expense") {
            SupabaseDataApi.addExpense(appContext, backendCycleId(), expenseName, amount)
        }
    }

    fun addIncome(amount: Long) {
        if (amount <= 0) return
        val emergencyAllocation = amount * emergencyAllocationPercent / 100
        incomeAdded += amount - emergencyAllocation
        emergencyFund += emergencyAllocation
        recentActivity = (listOf(ActivityItem("Income", amount)) + recentActivity).take(5)
        persist()
        syncAction("Saving income") {
            SupabaseDataApi.addIncome(appContext, backendCycleId(), amount, emergencyAllocationPercent)
        }
    }

    fun addSavings(amount: Long) {
        if (amount <= 0 || amount > remainingBalance) return
        emergencyFund += amount
        recentActivity = (listOf(ActivityItem("Manual savings", -amount)) + recentActivity).take(5)
        persist()
        syncAction("Saving contribution") {
            SupabaseDataApi.addSavings(appContext, amount)
        }
    }

    fun addExtraBudget(name: String, amount: Long) {
        if (amount <= 0) return
        val budgetName = name.trim().ifBlank { "Additional budget" }
        extraBudgets = extraBudgets + ExtraBudget(budgetName, amount)
        recentActivity = (
            listOf(ActivityItem("Budget: $budgetName", amount)) + recentActivity
            ).take(5)
        persist()
        syncAction("Saving extra budget") {
            SupabaseDataApi.addExtraBudget(appContext, backendCycleId(), budgetName, amount)
        }
    }

    fun updateEmergencyAllocationPercent(percent: Int) {
        emergencyAllocationPercent = percent.coerceIn(0, 100)
        persist()
    }

    fun resetCycleData() {
        spent = 0
        incomeAdded = 0
        emergencyFund = 0
        extraBudgets = emptyList()
        recentActivity = emptyList()
        persist()
    }

    fun updateBudget(startDate: LocalDate, endDate: LocalDate, budget: Long) {
        if (budget <= 0 || !endDate.isAfter(startDate)) return
        cycle = cycle.copy(startDate = startDate, endDate = endDate, budget = budget)
        persist()
        syncAction("Saving budget") {
            val existing = SupabaseDataApi.activeCycle(appContext)
            if (existing == null) SupabaseDataApi.createCycle(appContext, cycle)
            else SupabaseDataApi.updateCycle(appContext, existing.id, cycle)
        }
    }

    fun syncWithBackend() {
        viewModelScope.launch {
            isSyncing = true
            syncMessage = null
            runCatching { SupabaseDataApi.snapshot(appContext) }
                .onSuccess { dashboard ->
                    cycle = cycle.copy(
                        startDate = LocalDate.parse(dashboard.cycle.startDate),
                        endDate = LocalDate.parse(dashboard.cycle.endDate),
                        budget = dashboard.cycle.budgetCents
                    )
                    spent = dashboard.spentCents
                    incomeAdded = dashboard.incomeCents
                    emergencyFund = dashboard.emergencyFundCents
                    extraBudgets = dashboard.extraBudgets
                    persist()
                    syncMessage = "Synced with Supabase"
                }
                .onFailure { syncMessage = "Sync failed: ${it.message ?: "check your connection"}" }
            isSyncing = false
        }
    }

    private suspend fun backendCycleId(): String {
        return SupabaseDataApi.activeCycle(appContext)?.id
            ?: run {
                SupabaseDataApi.createCycle(appContext, cycle)
                SupabaseDataApi.activeCycle(appContext)?.id
                    ?: error("Budget cycle was not returned by Supabase")
            }
    }

    private fun syncAction(label: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { syncMessage = "$label synced" }
                .onFailure { syncMessage = "$label failed: ${it.message ?: "check your connection"}" }
        }
    }

    private fun persist() {
        preferences.edit()
            .putLong("spent", spent)
            .putLong("income", incomeAdded)
            .putLong("emergency", emergencyFund)
            .putInt("emergency_percent", emergencyAllocationPercent)
            .putLong("budget", cycle.budget)
            .putString("start_date", cycle.startDate.toString())
            .putString("end_date", cycle.endDate.toString())
            .putString(
                "extra_budgets",
                extraBudgets.joinToString("|") { "${it.name.replace("|", "")}::${it.amount}" }
            )
            .apply()
    }

    private fun loadExtraBudgets(): List<ExtraBudget> =
        preferences.getString("extra_budgets", "")
            ?.split("|")
            ?.mapNotNull { entry ->
                val parts = entry.split("::", limit = 2)
                if (parts.size == 2) parts[1].toLongOrNull()?.let { ExtraBudget(parts[0], it) } else null
            }
            ?: emptyList()
}
