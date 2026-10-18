package com.spendwise.app

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class DashboardResponse(
    val spentCents: Long = 0,
    val incomeCents: Long = 0,
    val emergencyFundCents: Long = 0,
    val recentActivity: List<ApiActivity> = emptyList()
)

data class ApiActivity(
    val label: String = "",
    val amountCents: Long = 0
)

interface SpendWiseService {
    @GET("api/v1/dashboard")
    suspend fun getDashboard(): DashboardResponse
}

object SpendWiseApi {
    private val service: SpendWiseService = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:8080/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SpendWiseService::class.java)

    suspend fun dashboard(): DashboardResponse = service.getDashboard()
}
