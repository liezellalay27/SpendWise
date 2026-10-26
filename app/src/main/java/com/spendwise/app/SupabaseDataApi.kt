package com.spendwise.app

import android.content.Context
import android.util.Base64
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Query

data class SupabaseCycle(
    val id: String,
    @SerializedName("start_date") val startDate: String,
    @SerializedName("end_date") val endDate: String,
    @SerializedName("budget_cents") val budgetCents: Long
)

data class SupabaseExpense(
    @SerializedName("amount_cents") val amountCents: Long
)

data class SupabaseIncome(
    @SerializedName("amount_cents") val amountCents: Long,
    @SerializedName("emergency_allocation_cents") val emergencyAllocationCents: Long
)

data class SupabaseExtraBudget(
    val name: String,
    @SerializedName("amount_cents") val amountCents: Long
)

data class SupabaseFundTransaction(
    val type: String,
    @SerializedName("amount_cents") val amountCents: Long
)

private data class CycleRequest(
    @SerializedName("user_id") val userId: String,
    val name: String = "Budget cycle",
    @SerializedName("start_date") val startDate: String,
    @SerializedName("end_date") val endDate: String,
    @SerializedName("budget_cents") val budgetCents: Long
)

private data class ExpenseRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("cycle_id") val cycleId: String,
    val name: String,
    @SerializedName("amount_cents") val amountCents: Long
)

private data class IncomeRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("cycle_id") val cycleId: String,
    val source: String,
    @SerializedName("amount_cents") val amountCents: Long,
    @SerializedName("emergency_allocation_percent") val emergencyAllocationPercent: Int,
    @SerializedName("emergency_allocation_cents") val emergencyAllocationCents: Long
)

private data class ExtraBudgetRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("cycle_id") val cycleId: String,
    val name: String,
    @SerializedName("amount_cents") val amountCents: Long
)

private data class FundTransactionRequest(
    @SerializedName("user_id") val userId: String,
    val type: String,
    @SerializedName("amount_cents") val amountCents: Long,
    val source: String
)

private interface SupabaseDataService {
    @GET("rest/v1/budget_cycles")
    suspend fun activeCycle(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("user_id") userId: String,
        @Query("status") status: String = "eq.ACTIVE",
        @Query("limit") limit: Int = 1
    ): List<SupabaseCycle>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/budget_cycles")
    suspend fun createCycle(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body request: CycleRequest
    ): List<SupabaseCycle>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/budget_cycles")
    suspend fun updateCycle(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("id") id: String,
        @Body request: CycleRequest
    ): List<SupabaseCycle>

    @GET("rest/v1/expenses")
    suspend fun expenses(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("user_id") userId: String,
        @Query("cycle_id") cycleId: String
    ): List<SupabaseExpense>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/expenses")
    suspend fun createExpense(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body request: ExpenseRequest
    ): List<SupabaseExpense>

    @GET("rest/v1/income_transactions")
    suspend fun income(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("user_id") userId: String,
        @Query("cycle_id") cycleId: String
    ): List<SupabaseIncome>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/income_transactions")
    suspend fun createIncome(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body request: IncomeRequest
    ): List<SupabaseIncome>

    @GET("rest/v1/extra_budgets")
    suspend fun extraBudgets(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("user_id") userId: String,
        @Query("cycle_id") cycleId: String
    ): List<SupabaseExtraBudget>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/extra_budgets")
    suspend fun createExtraBudget(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body request: ExtraBudgetRequest
    ): List<SupabaseExtraBudget>

    @GET("rest/v1/emergency_fund_transactions")
    suspend fun fundTransactions(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("user_id") userId: String
    ): List<SupabaseFundTransaction>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/emergency_fund_transactions")
    suspend fun createFundTransaction(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body request: FundTransactionRequest
    ): List<SupabaseFundTransaction>
}

data class BackendSnapshot(
    val cycle: SupabaseCycle,
    val spentCents: Long,
    val incomeCents: Long,
    val emergencyFundCents: Long,
    val extraBudgets: List<ExtraBudget>
)

object SupabaseDataApi {
    private val service: SupabaseDataService by lazy {
        Retrofit.Builder()
            .baseUrl("${BuildConfig.SUPABASE_URL}/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SupabaseDataService::class.java)
    }

    suspend fun snapshot(context: Context): BackendSnapshot {
        val cycle = activeCycle(context)
            ?: error("No active budget cycle exists")
        val (userId, token) = credentials(context)
        val expenses = service.expenses(key(), bearer(token), "eq.$userId", "eq.${cycle.id}")
        val income = service.income(key(), bearer(token), "eq.$userId", "eq.${cycle.id}")
        val extras = service.extraBudgets(key(), bearer(token), "eq.$userId", "eq.${cycle.id}")
        val fundTransactions = service.fundTransactions(key(), bearer(token), "eq.$userId")
        return BackendSnapshot(
            cycle = cycle,
            spentCents = expenses.sumOf { it.amountCents },
            incomeCents = income.sumOf { it.amountCents - it.emergencyAllocationCents },
            emergencyFundCents = fundTransactions.sumOf {
                if (it.type == "WITHDRAWAL") -it.amountCents else it.amountCents
            } + income.sumOf { it.emergencyAllocationCents },
            extraBudgets = extras.map { ExtraBudget(it.name, it.amountCents) }
        )
    }

    suspend fun activeCycle(context: Context): SupabaseCycle? {
        val (userId, token) = credentials(context)
        return service.activeCycle(key(), bearer(token), "eq.$userId").firstOrNull()
    }

    suspend fun createCycle(context: Context, cycle: BudgetCycle) {
        val (userId, token) = credentials(context)
        service.createCycle(
            key(), bearer(token),
            CycleRequest(userId, startDate = cycle.startDate.toString(), endDate = cycle.endDate.toString(), budgetCents = cycle.budget)
        )
    }

    suspend fun updateCycle(context: Context, cycleId: String, cycle: BudgetCycle) {
        val (userId, token) = credentials(context)
        service.updateCycle(
            key(), bearer(token), "eq.$cycleId",
            CycleRequest(userId, startDate = cycle.startDate.toString(), endDate = cycle.endDate.toString(), budgetCents = cycle.budget)
        )
    }

    suspend fun addExpense(context: Context, cycleId: String, name: String, amount: Long) {
        val (userId, token) = credentials(context)
        service.createExpense(key(), bearer(token), ExpenseRequest(userId, cycleId, name, amount))
    }

    suspend fun addIncome(context: Context, cycleId: String, amount: Long, allocationPercent: Int) {
        val (userId, token) = credentials(context)
        val allocation = amount * allocationPercent / 100
        service.createIncome(
            key(), bearer(token),
            IncomeRequest(userId, cycleId, "Added income", amount, allocationPercent, allocation)
        )
    }

    suspend fun addExtraBudget(context: Context, cycleId: String, name: String, amount: Long) {
        val (userId, token) = credentials(context)
        service.createExtraBudget(key(), bearer(token), ExtraBudgetRequest(userId, cycleId, name, amount))
    }

    suspend fun addSavings(context: Context, amount: Long) {
        val (userId, token) = credentials(context)
        service.createFundTransaction(
            key(), bearer(token), FundTransactionRequest(userId, "CONTRIBUTION", amount, "Manual savings")
        )
    }

    private fun credentials(context: Context): Pair<String, String> {
        val preferences = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
        val token = preferences.getString("access_token", null) ?: error("Session has expired")
        val userId = preferences.getString("user_id", null) ?: token.jwtSubject()
            ?: error("Signed-in user is missing")
        require(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            "Supabase configuration is missing"
        }
        return userId to token
    }

    private fun key() = BuildConfig.SUPABASE_ANON_KEY
    private fun bearer(token: String) = "Bearer $token"

    private fun String.jwtSubject(): String? =
        runCatching {
            val payload = split(".")[1]
            val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP))
            Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
        }.getOrNull()
}
