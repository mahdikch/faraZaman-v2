package Ir.co.tfs.farazaman.service.remote

import Ir.co.tfs.farazaman.data.model.UserInfoResponse
import retrofit2.Response
import retrofit2.http.GET

interface UserService {
    @GET("/api/AccountUser/Info")
    suspend fun getUserInfo(): Response<UserInfoResponse>
}

