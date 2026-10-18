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

enum class BudgetPeriod(val label: String, val days: Long) {
    DAILY("Daily", 1),
    WEEKLY("Weekly", 7),
    MONTHLY("Monthly", 30)
}

data class BudgetCycle(
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val budget: Long = 30_000_00,
    val lowBalanceThreshold: Long = 5_000_00,
    val startDate: LocalDate = LocalDate.now()
)

data class ActivityItem(val label: String, val amount: Long)

class SpendWiseViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("spendwise", Context.MODE_PRIVATE)

    var cycle by mutableStateOf(
        BudgetCycle(budget = preferences.getLong("budget", 30_000_00))
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
    var selectedPeriod by mutableStateOf(cycle.period)
        private set
    var isSyncing by mutableStateOf(false)
        private set
    var syncMessage by mutableStateOf<String?>(null)
        private set

    val daysRemaining: Long
        get() = maxOf(1, cycle.period.days - ChronoUnit.DAYS.between(cycle.startDate, LocalDate.now()))

    val remainingBalance: Long
        get() = cycle.budget + incomeAdded - spent - emergencyFund

    val safeToSpendToday: Long
        get() = maxOf(0, remainingBalance / daysRemaining)

    fun addExpense(name: String, amount: Long) {
        if (amount <= 0) return
        spent += amount
        recentActivity = (listOf(ActivityItem(name.ifBlank { "Expense" }, -amount)) + recentActivity).take(5)
        persist()
    }

    fun addIncome(amount: Long) {
        if (amount <= 0) return
        val emergencyAllocation = amount * emergencyAllocationPercent / 100
        incomeAdded += amount - emergencyAllocation
        emergencyFund += emergencyAllocation
        recentActivity = (listOf(ActivityItem("Income", amount)) + recentActivity).take(5)
        persist()
    }

    fun updateEmergencyAllocationPercent(percent: Int) {
        emergencyAllocationPercent = percent.coerceIn(0, 100)
        persist()
    }

    fun setPeriod(period: BudgetPeriod) {
        selectedPeriod = period
        cycle = cycle.copy(period = period, startDate = LocalDate.now())
        persist()
    }

    fun syncWithBackend() {
        viewModelScope.launch {
            isSyncing = true
            syncMessage = null
            runCatching { SpendWiseApi.dashboard() }
                .onSuccess { dashboard ->
                    spent = dashboard.spentCents
                    incomeAdded = dashboard.incomeCents
                    emergencyFund = dashboard.emergencyFundCents
                    recentActivity = dashboard.recentActivity.map {
                        ActivityItem(it.label, it.amountCents)
                    }
                    persist()
                    syncMessage = "Synced with SpendWise backend"
                }
                .onFailure { syncMessage = "Offline mode: local data is being used" }
            isSyncing = false
        }
    }

    private fun persist() {
        preferences.edit()
            .putLong("spent", spent)
            .putLong("income", incomeAdded)
            .putLong("emergency", emergencyFund)
            .putInt("emergency_percent", emergencyAllocationPercent)
            .putString("period", selectedPeriod.name)
            .apply()
    }
}
