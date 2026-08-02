package Ir.co.tfs.farazaman.service.remote

import Ir.co.tfs.farazaman.data.model.UserRolesResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface RolesService {
    @GET("api/AccountUser/Roles")
    suspend fun getUserRoles(
        @Header("Authorization") token: String
    ): Response<UserRolesResponse>
}

