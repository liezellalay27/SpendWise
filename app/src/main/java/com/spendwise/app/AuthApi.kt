package com.spendwise.app

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

data class AuthRequest(
    val email: String,
    val password: String,
    val data: Map<String, String> = emptyMap()
)

data class AuthResponse(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val user: AuthUser? = null
)

data class AuthUser(
    val id: String? = null,
    val email: String? = null,
    val user_metadata: Map<String, String>? = null
)

data class AuthErrorResponse(val msg: String? = null, val error_description: String? = null)

interface SupabaseAuthService {
    @POST("auth/v1/signup")
    suspend fun signUp(
        @Query("apikey") apiKey: String,
        @Query("redirect_to") redirectTo: String,
        @Body request: AuthRequest
    ): AuthResponse

    @POST("auth/v1/token")
    suspend fun signIn(
        @Query("grant_type") grantType: String = "password",
        @Query("apikey") apiKey: String,
        @Body request: AuthRequest
    ): AuthResponse
}

object SupabaseAuthApi {
    private val service: SupabaseAuthService by lazy {
        require(BuildConfig.SUPABASE_URL.isNotBlank()) {
            "SUPABASE_URL is missing. Add it to gradle.properties."
        }
        require(BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            "SUPABASE_ANON_KEY is missing. Add it to local.properties."
        }
        Retrofit.Builder()
            .baseUrl("${BuildConfig.SUPABASE_URL}/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SupabaseAuthService::class.java)
    }

    suspend fun signUp(name: String, email: String, password: String): AuthResponse =
        service.signUp(
            apiKey = BuildConfig.SUPABASE_ANON_KEY,
            redirectTo = AUTH_CALLBACK_URI,
            request = AuthRequest(
                email = email,
                password = password,
                data = mapOf("display_name" to name)
            )
        )

    suspend fun signIn(email: String, password: String): AuthResponse =
        service.signIn(apiKey = BuildConfig.SUPABASE_ANON_KEY, request = AuthRequest(email, password))

    const val AUTH_CALLBACK_URI = "spendwise://auth/callback"
}
