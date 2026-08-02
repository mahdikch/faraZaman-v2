package Ir.co.tfs.farazaman.service.remote

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthService {
    @FormUrlEncoded
    @POST("token")
    suspend fun login(
        @Header("Authorization") authHeader: String,
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("grant_type") grantType: String = "password"
    ): Response<TokenResponse>
    
    @FormUrlEncoded
    @POST("token")
    suspend fun refreshToken(
        @Header("Authorization") authHeader: String,
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token"
    ): Response<TokenResponse>
}

data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int,
    val refresh_token: String
)
