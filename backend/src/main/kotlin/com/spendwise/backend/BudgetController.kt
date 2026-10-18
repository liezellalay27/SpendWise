package com.spendwise.backend

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class DashboardResponse(
    val budgetCents: Long,
    val spentCents: Long,
    val incomeCents: Long,
    val emergencyFundCents: Long,
    val recentActivity: List<ActivityResponse>
)

data class ActivityResponse(
    val id: String,
    val label: String,
    val amountCents: Long,
    val createdAt: Instant
)

data class MoneyRequest(
    val amountCents: Long,
    val name: String? = null
)

data class EmergencyAllocationRequest(val percentage: Int)

@RestController
@RequestMapping("/api/v1")
class BudgetController(private val budgetService: BudgetService) {

    @GetMapping("/dashboard")
    fun dashboard(): DashboardResponse = budgetService.dashboard()

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    fun addExpense(@RequestBody request: MoneyRequest): DashboardResponse {
        return budgetService.addExpense(request.amountCents, request.name)
    }

    @PostMapping("/income")
    @ResponseStatus(HttpStatus.CREATED)
    fun addIncome(@RequestBody request: MoneyRequest): DashboardResponse {
        return budgetService.addIncome(request.amountCents)
    }

    @PatchMapping("/settings/emergency-allocation")
    fun updateEmergencyAllocation(
        @RequestBody request: EmergencyAllocationRequest
    ): DashboardResponse {
        return budgetService.updateEmergencyAllocation(request.percentage)
    }
}

class BudgetService {
    private val budgetCents = 3_000_000L
    private var spentCents = 0L
    private var incomeCents = 0L
    private var emergencyFundCents = 0L
    private var emergencyAllocationPercent = 10
    private val activity = ArrayDeque<ActivityResponse>()

    @Synchronized
    fun dashboard(): DashboardResponse = snapshot()

    @Synchronized
    fun addExpense(amountCents: Long, name: String?): DashboardResponse {
        require(amountCents > 0) { "Expense amount must be greater than zero" }
        spentCents += amountCents
        record(name?.trim().takeUnless { it.isNullOrEmpty() } ?: "Expense", -amountCents)
        return snapshot()
    }

    @Synchronized
    fun addIncome(amountCents: Long): DashboardResponse {
        require(amountCents > 0) { "Income amount must be greater than zero" }
        val emergencyAllocation = amountCents * emergencyAllocationPercent / 100
        incomeCents += amountCents - emergencyAllocation
        emergencyFundCents += emergencyAllocation
        record("Income", amountCents)
        return snapshot()
    }

    @Synchronized
    fun updateEmergencyAllocation(percentage: Int): DashboardResponse {
        require(percentage in 0..100) { "Emergency allocation must be between 0 and 100" }
        emergencyAllocationPercent = percentage
        return snapshot()
    }

    private fun record(label: String, amountCents: Long) {
        activity.addFirst(ActivityResponse(UUID.randomUUID().toString(), label, amountCents, Instant.now()))
        while (activity.size > 5) activity.removeLast()
    }

    private fun snapshot() = DashboardResponse(
        budgetCents,
        spentCents,
        incomeCents,
        emergencyFundCents,
        activity.toList()
    )
}
