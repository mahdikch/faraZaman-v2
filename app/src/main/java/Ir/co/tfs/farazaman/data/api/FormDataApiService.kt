package Ir.co.tfs.farazaman.data.api

import Ir.co.tfs.farazaman.data.model.FormDataApiRequest
import Ir.co.tfs.farazaman.data.model.FormDataResponse
import Ir.co.tfs.farazaman.data.model.MissionIndexResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface FormDataApiService {
    @POST("api/BillOriginViEvent/FormData")
    suspend fun getFormData(
        @Body request: FormDataApiRequest
    ): Response<FormDataResponse>
    
    /**
     * Upload GPS track file
     */
    @POST("api/GisGeolocation/upload")
    suspend fun uploadTrackFile(
        @Body request: Any
    ): Response<Any>
    
    /**
     * Get mission details by encryption
     */
    @POST("api/BillOriginCleaningItemDailyItem/Details/{encryption}")
    suspend fun getMissionDetails(
        @Path("encryption") encryption: String,
        @Body request: Any = mapOf("request" to mapOf("TenantId" to 1))
    ): Response<Any>
    
    /**
     * Create new mission
     */
    @POST("api/BillOriginCleaningItemDailyItem/Create")
    suspend fun createMission(
        @Body request: Any
    ): Response<Any>
    
    /**
     * Get mission index/details after creation
     */
    @POST("api/BillOriginCleaningItemDailyItem/Index")
    suspend fun getMissionIndex(
        @Body request: Any
    ): Response<MissionIndexResponse>
} 
