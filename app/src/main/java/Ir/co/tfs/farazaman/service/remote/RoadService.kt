package Ir.co.tfs.farazaman.service.remote

import Ir.co.tfs.farazaman.data.model.RoadData
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface RoadService {
    @GET("api/GisLayer/DataLayerRoadByPoint")
    suspend fun getRoadData(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("buffer") buffer: Int = 30
    ): List<RoadData>
} 
