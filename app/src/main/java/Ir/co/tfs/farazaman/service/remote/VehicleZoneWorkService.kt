package Ir.co.tfs.farazaman.service.remote

import Ir.co.tfs.farazaman.data.model.VehicleZoneWorkResponse
import retrofit2.Response
import retrofit2.http.GET

interface VehicleZoneWorkService {
    @GET("/api/ApplicationUser/VehicleZoneWork")
    suspend fun getVehicleZoneWork(): Response<VehicleZoneWorkResponse>
}

