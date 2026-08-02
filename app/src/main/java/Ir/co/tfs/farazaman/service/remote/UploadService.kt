package Ir.co.tfs.farazaman.service.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface UploadService {
    @Multipart
    @POST("api/GisGeolocation/upload")
    suspend fun uploadTrack(
        @Part file: MultipartBody.Part
    ): Response<Any>
}
