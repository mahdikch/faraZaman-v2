// در فایل ViolationApiService.kt

package Ir.co.tfs.farazaman.service.remote

import Ir.co.tfs.farazaman.data.model.SubmitViolationResponse
import Ir.co.tfs.farazaman.data.model.ViolationSuccessResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ViolationApiService {

    @Multipart
    @POST("api/BillOriginViEvent/Create")
    suspend fun createViolation(
        @Part model: MultipartBody.Part, // ما درخواست را به صورت multipart ارسال می‌کنیم
        @Part files: List<MultipartBody.Part>? = null // فایل‌های تصویر
    ): Response<ViolationSuccessResponse>
}
