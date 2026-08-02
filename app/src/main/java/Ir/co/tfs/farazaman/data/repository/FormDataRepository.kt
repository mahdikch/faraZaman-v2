package Ir.co.tfs.farazaman.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import Ir.co.tfs.farazaman.data.api.FormDataApiService
import Ir.co.tfs.farazaman.data.model.FormDataApiRequest
import Ir.co.tfs.farazaman.data.model.FormDataRequest
import Ir.co.tfs.farazaman.data.model.FormDataResponse
import Ir.co.tfs.farazaman.data.model.RequestData
import Ir.co.tfs.farazaman.util.TokenManager
import retrofit2.Response
import javax.inject.Inject

class FormDataRepository @Inject constructor(
    private val apiService: FormDataApiService,
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val tokenManager: TokenManager
) {
    
    suspend fun getFormData(formDataRequest: FormDataRequest? = null): Response<FormDataResponse> {
        val authHeader = tokenManager.getAuthorizationHeader()
        if (authHeader.isNullOrEmpty()) {
            throw IllegalStateException("Access token not found. Please login first.")
        }
        
        val request = if (formDataRequest != null) {
            FormDataApiRequest(request = formDataRequest)
        } else {
            // Initial request with just tenantId
            val initialRequest = FormDataRequest(
                contractIds = emptyList(),
                organIds = emptyList(),
                billCleaningViolationGroupIds = emptyList(),
                billCleaningViolationIds = emptyList(),
                billCleaningItemGroupIds = emptyList(),
                billCleaningItemIds = emptyList(),
                visitDate = "",
                maxVisitDate = "",
                minVisitDate = "",
                isDeleted = false,
                tenantId = 1,
                billOriginCleaningItemIds = emptyList(),
                billCleaningViolationId = 0,
                contractId = 0,
                organId = 0
            )
            FormDataApiRequest(request = initialRequest)
        }
        
        return apiService.getFormData(
            request = request
        )
    }
} 
