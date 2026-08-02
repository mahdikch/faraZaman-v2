package Ir.co.tfs.farazaman.service.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface ApiService {
    @GET("api/GisLayers/DataLayerRoadByPoint")
    suspend fun getRoadByPoint(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("buffer") buffer: Int
    ): ResponseBody

    @GET("api/GisLayer/Index")
    suspend fun getGisLayerIndex(
        @Header("Accept") accept: String = "application/json"
    ): retrofit2.Response<okhttp3.ResponseBody>
}
