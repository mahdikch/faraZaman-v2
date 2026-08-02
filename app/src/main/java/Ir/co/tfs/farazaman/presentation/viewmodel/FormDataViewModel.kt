package Ir.co.tfs.farazaman.presentation.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import Ir.co.tfs.farazaman.data.db.TrackContentProvider
import Ir.co.tfs.farazaman.data.db.DatabaseHelper
import Ir.co.tfs.farazaman.data.model.DropdownItem
import Ir.co.tfs.farazaman.data.model.FormDataRequest
import Ir.co.tfs.farazaman.data.model.FormDataResponse
import Ir.co.tfs.farazaman.data.model.SubmitViolationRequest
import Ir.co.tfs.farazaman.data.model.ViolationData
import Ir.co.tfs.farazaman.data.model.ViolationErrorResponse
import Ir.co.tfs.farazaman.data.model.BillOriginViEventGeoLocation
import Ir.co.tfs.farazaman.data.model.GisGeolocationTrack
import Ir.co.tfs.farazaman.data.repository.FormDataRepository
import Ir.co.tfs.farazaman.service.remote.ViolationApiService
import okhttp3.MultipartBody
import retrofit2.Response
import javax.inject.Inject
import Ir.co.tfs.farazaman.data.model.ApiErrorResponse
import android.content.Context
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

@HiltViewModel
class FormDataViewModel @Inject constructor(
    private val application: Application,
    private val repository: FormDataRepository,
    private val violationApiService: ViolationApiService,
    private val prefs: SharedPreferences
) : ViewModel() {
    // متغیرهایی برای نگهداری ID های انتخاب شده
    private var selectedOrganId: Int? = null
    private var selectedContractId: Int? = null
    private var selectedViolationGroupId: Int? = null
    private var selectedViolationId: Int? = null
    private var selectedItemGroupId: Int? = null
    private var selectedOriginItemId: Int? = null

    private val _submissionState = MutableLiveData<SubmissionState?>()
    val submissionState: LiveData<SubmissionState?> = _submissionState

    private val _formData = MutableLiveData<FormDataResponse>()
    val formData: LiveData<FormDataResponse> = _formData
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private var formDataJob: Job? = null
    private var submitJob: Job? = null
    private var isSubmitting = false
    
    // Current form data request state
    private var currentFormDataRequest = FormDataRequest(
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

    fun saveViolationAsWaypoint(
        trackId: Long,
        latitude: Double,
        longitude: Double,
        violationIdFromServer: String
    ) {
        // اجرای عملیات دیتابیس در یک ترد پس‌زمینه مخصوص I/O
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("ViewModelLog", "Attempting to save waypoint in background for track: $trackId")

            // Check if track has GPS points before saving waypoint
            val hasTrackPoints = checkTrackHasPoints(trackId)
            if (!hasTrackPoints) {
                Log.w("ViewModelLog", "Cannot save waypoint: Track $trackId has no GPS points")
                return@launch
            }

            // مقادیری که باید در دیتابیس ذخیره شوند
            val values = ContentValues().apply {
                put(TrackContentProvider.Schema.COL_TRACK_ID, trackId)
                put(TrackContentProvider.Schema.COL_LATITUDE, latitude)
                put(TrackContentProvider.Schema.COL_LONGITUDE, longitude)
                put(TrackContentProvider.Schema.COL_NAME, "تخلف ثبت شده")
                put(TrackContentProvider.Schema.COL_LINK, violationIdFromServer) // ذخیره ID سرور در ستون لینک
                put(TrackContentProvider.Schema.COL_TIMESTAMP, System.currentTimeMillis())
                put(TrackContentProvider.Schema.COL_NBSATELLITES, 0) // Default value to satisfy NOT NULL constraint
            }

            try {
                // استفاده از application context برای دسترسی به ContentResolver
                val correctUri = TrackContentProvider.waypointsUri(trackId)
                val waypointUri = application.contentResolver.insert(correctUri, values)

                if (waypointUri != null) {
                    Log.i("ViewModelLog", "Waypoint saved successfully for violation ID: $violationIdFromServer")
                } else {
                    Log.e("ViewModelLog", "Failed to save waypoint, content resolver returned null URI.")
                }
            } catch (e: Exception) {
                // مدیریت خطاهای احتمالی در زمان ذخیره در دیتابیس
                Log.e("ViewModelLog", "Error saving waypoint to database.", e)
            }
        }
    }

    /**
     * Check if the track has any GPS points
     * @param trackId The track ID to check
     * @return true if track has GPS points, false otherwise
     */
    private fun checkTrackHasPoints(trackId: Long): Boolean {
        if (trackId == -1L) {
            Log.d("ViewModelLog", "Track ID is -1, no track selected")
            return false
        }

        // First check if the track is active (GPS tracking is running)
        val isTrackActive = checkTrackActive(trackId)
        if (!isTrackActive) {
            Log.d("ViewModelLog", "Track $trackId is not active (GPS tracking not started)")
            return false
        }

        val cursor = application.contentResolver.query(
            TrackContentProvider.trackPointsUri(trackId),
            arrayOf(TrackContentProvider.Schema.COL_ID),
            null,
            null,
            null
        )

        cursor?.use {
            val hasPoints = it.count > 0
            Log.d("ViewModelLog", "Track $trackId has ${it.count} GPS points")
            return hasPoints
        }

        Log.d("ViewModelLog", "No cursor returned for track $trackId")
        return false
    }

    /**
     * Check if the track is active (GPS tracking is running)
     * @param trackId The track ID to check
     * @return true if track is active, false otherwise
     */
    private fun checkTrackActive(trackId: Long): Boolean {
        // Use a direct database query to avoid the JOIN issue
        val dbHelper = Ir.co.tfs.farazaman.data.db.DatabaseHelper(application)
        val db = dbHelper.readableDatabase
        
        val cursor = db.query(
            TrackContentProvider.Schema.TBL_TRACK,
            arrayOf(TrackContentProvider.Schema.COL_ACTIVE),
            "${TrackContentProvider.Schema.COL_ID} = ?",
            arrayOf(trackId.toString()),
            null,
            null,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val isActive = it.getInt(it.getColumnIndex(TrackContentProvider.Schema.COL_ACTIVE)) == TrackContentProvider.Schema.VAL_TRACK_ACTIVE
                Log.d("ViewModelLog", "Track $trackId active status: $isActive")
                return isActive
            }
        }

        Log.d("ViewModelLog", "No track found for ID: $trackId")
        return false
    }
    fun cancelFormDataLoading() {
        formDataJob?.cancel()
        formDataJob = null
        _isLoading.value = false
    }

    fun cancelSubmission() {
        if (!isSubmitting) return
        submitJob?.cancel()
        submitJob = null
        isSubmitting = false
        _submissionState.value = null
    }

    fun cancelActiveRequests() {
        cancelFormDataLoading()
        cancelSubmission()
    }

    fun fetchFormData() {
        formDataJob?.cancel()
        _isLoading.value = true
        _error.value = null

        formDataJob = viewModelScope.launch {
            try {
                val response = repository.getFormData()
                if (response.isSuccessful) {
                    response.body()?.let { formData ->
                        _formData.value = formData
                    } ?: run {
                        _error.value = "Empty response from server"
                    }
                } else {
                    _error.value = parseFormDataError(response)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                _error.value = e.message ?: "Access token not available"
            } catch (e: Exception) {
                _error.value = "Network error: ${e.message}"
            } finally {
                if (isActive) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun fetchFormDataWithRequest(updatedRequest: FormDataRequest) {
        formDataJob?.cancel()
        _isLoading.value = true
        _error.value = null

        formDataJob = viewModelScope.launch {
            try {
                val response = repository.getFormData(updatedRequest)
                if (response.isSuccessful) {
                    response.body()?.let { formData ->
                        _formData.value = formData
                        currentFormDataRequest = updatedRequest
                    } ?: run {
                        _error.value = "Empty response from server"
                    }
                } else {
                    _error.value = parseFormDataError(response)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                _error.value = e.message ?: "Access token not available"
            } catch (e: Exception) {
                _error.value = "Network error: ${e.message}"
            } finally {
                if (isActive) {
                    _isLoading.value = false
                }
            }
        }
    }
    
    fun updateOrganSelection(organId: Int) {
        val updatedRequest = currentFormDataRequest.copy(
            organId = organId,
            organIds = listOf(organId)
        )
        fetchFormDataWithRequest(updatedRequest)
    }
    
    fun updateContractSelection(contractId: Int) {
        val updatedRequest = currentFormDataRequest.copy(
            contractId = contractId,
            contractIds = listOf(contractId)
        )
        fetchFormDataWithRequest(updatedRequest)
    }
    
    fun updateViolationGroupSelection(violationGroupId: Int) {
        selectedViolationId = null
        val updatedRequest = currentFormDataRequest.copy(
            billCleaningViolationGroupIds = listOf(violationGroupId),
            billCleaningViolationIds = emptyList(),
            billCleaningViolationId = 0,
            billCleaningItemGroupIds = selectedItemGroupId?.let { listOf(it) }
                ?: currentFormDataRequest.billCleaningItemGroupIds,
            billOriginCleaningItemIds = selectedOriginItemId?.let { listOf(it) }
                ?: currentFormDataRequest.billOriginCleaningItemIds,
        )
        fetchFormDataWithRequest(updatedRequest)
    }
    
    fun updateViolationSelection(violationId: Int) {
        val updatedRequest = currentFormDataRequest.copy(
            billCleaningViolationId = violationId,
            billCleaningViolationIds = listOf(violationId)
        )
        fetchFormDataWithRequest(updatedRequest)
    }
    
    fun updateItemGroupSelection(itemGroupId: Int) {
        val updatedRequest = currentFormDataRequest.copy(
            billCleaningItemGroupIds = listOf(itemGroupId)
        )
        fetchFormDataWithRequest(updatedRequest)
    }
    
    fun updateOriginItemSelection(originItemId: Int) {
        val updatedRequest = currentFormDataRequest.copy(
            billOriginCleaningItemIds = listOf(originItemId)
        )
        fetchFormDataWithRequest(updatedRequest)
    }
    
    fun getOrgans(): List<DropdownItem> {
        return _formData.value?.response?.organs ?: emptyList()
    }
    
    fun getContracts(): List<DropdownItem> {
        return _formData.value?.response?.contracts ?: emptyList()
    }
    
    fun getViolationGroups(): List<DropdownItem> {
        return _formData.value?.response?.billCleaningViolationGroups ?: emptyList()
    }
    
    fun getViolations(): List<DropdownItem> {
        return _formData.value?.response?.billCleaningViolations ?: emptyList()
    }
    
    fun getItemGroups(): List<DropdownItem> {
        return _formData.value?.response?.billCleaningItemGroups ?: emptyList()
    }
    
    fun getOriginItems(): List<DropdownItem> {
        return _formData.value?.response?.billOriginCleaningItems ?: emptyList()
    }

    fun onOrganSelected(organId: Int) {
        selectedOrganId = organId
        // منطق فیلتر کردن قراردادها...
    }

    fun onContractSelected(contractId: Int) {
        selectedContractId = contractId
        // ...
    }
    fun onViolationGroupIdSelected(organId: Int) {
        selectedViolationGroupId = organId
        // منطق فیلتر کردن قراردادها...
    }

    fun onViolationIdSelected(contractId: Int) {
        selectedViolationId = contractId
        // ...
    }
    fun onItemGroupIdSelected(organId: Int) {
        selectedItemGroupId = organId
        // منطق فیلتر کردن قراردادها...
    }

    fun onOriginItemIdSelected(contractId: Int) {
        selectedOriginItemId = contractId
        // ...
    }
    fun submitViolation(
        visitDate: String,
        number: Int,
        address: String,
        description: String,
        imageUris: List<Uri>, // برای آپلود عکس‌ها در آینده
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        requireItemSelection: Boolean = true
    ) {
        if (isSubmitting) {
            Log.w("FormDataViewModel", "Submit already in progress, ignoring duplicate call")
            return
        }

        Log.d("FormDataViewModel", "=== VIOLATION FORM SUBMISSION STARTED ===")
        Log.d("FormDataViewModel", "Form Data Received:")
        Log.d("FormDataViewModel", "  - Visit Date: $visitDate")
        Log.d("FormDataViewModel", "  - Number: $number")
        Log.d("FormDataViewModel", "  - Address: $address")
        Log.d("FormDataViewModel", "  - Description: $description")
        Log.d("FormDataViewModel", "  - Images Count: ${imageUris.size}")
        Log.d("FormDataViewModel", "  - Image URIs: ${imageUris.map { it.toString() }}")
        Log.d("FormDataViewModel", "  - GPS Coordinates: Lat=$latitude, Lon=$longitude")
        
        Log.d("FormDataViewModel", "Selected IDs from ViewModel:")
        Log.d("FormDataViewModel", "  - Selected Organ ID: $selectedOrganId")
        Log.d("FormDataViewModel", "  - Selected Contract ID: $selectedContractId")
        Log.d("FormDataViewModel", "  - Selected Violation ID: $selectedViolationId")
        Log.d("FormDataViewModel", "  - Selected Violation Group ID: $selectedViolationGroupId")
        Log.d("FormDataViewModel", "  - Selected Item Group ID: $selectedItemGroupId")
        Log.d("FormDataViewModel", "  - Selected Origin Item ID: $selectedOriginItemId")

        // اعتبارسنجی: مطمئن شوید تمام ID های لازم انتخاب شده‌اند
        if (selectedOrganId == null) {
            Log.e("FormDataViewModel", "Validation failed: Organ ID is null")
            _submissionState.value = SubmissionState.Error("لطفاً کارفرما را انتخاب کنید.")
            return
        }
        
        if (selectedContractId == null) {
            Log.e("FormDataViewModel", "Validation failed: Contract ID is null")
            _submissionState.value = SubmissionState.Error("لطفاً قرارداد را انتخاب کنید.")
            return
        }
        
        if (selectedViolationId == null) {
            Log.e("FormDataViewModel", "Validation failed: Violation ID is null")
            _submissionState.value = SubmissionState.Error("لطفاً عنوان نقص را انتخاب کنید.")
            return
        }
        
        if (selectedViolationGroupId == null) {
            Log.e("FormDataViewModel", "Validation failed: Violation Group ID is null")
            _submissionState.value = SubmissionState.Error("لطفاً گروه نقص را انتخاب کنید.")
            return
        }
        
        if (requireItemSelection && selectedItemGroupId == null) {
            Log.e("FormDataViewModel", "Validation failed: Item Group ID is null")
            _submissionState.value = SubmissionState.Error("لطفاً گروه آیتم را انتخاب کنید.")
            return
        }
        
        if (requireItemSelection && selectedOriginItemId == null) {
            Log.e("FormDataViewModel", "Validation failed: Origin Item ID is null")
            _submissionState.value = SubmissionState.Error("لطفاً آیتم اصلی را انتخاب کنید.")
            return
        }

        // ساخت آبجکت درخواست با استفاده از ID های واقعی انتخاب شده
        val violationData = ViolationData(
            organIds = listOf(selectedOrganId!!), // Use actual selected organ ID
            contractIds = listOf(selectedContractId!!), // Use actual selected contract ID
            billCleaningViolationGroupIds = listOf(selectedViolationGroupId ?: 0),
            billCleaningViolationIds = listOf(selectedViolationId!!),
            billCleaningItemGroupIds = listOf(selectedItemGroupId ?: 0),
            billCleaningItemIds = emptyList(),
            visitDate = visitDate, // Use actual visit date from form
            billOriginCleaningItemIds = listOf(selectedOriginItemId ?: 0),
            number = number, // Use actual number from form
            visitedFault = description, // Use actual description from form
            address = address, // Use actual address from form
            billCleaningViolationId = selectedViolationId!!, // Use actual selected violation ID
            contractId = selectedContractId!!, // Use actual selected contract ID
            organId = selectedOrganId!!, // Use actual selected organ ID
            billCleaningViolationGroupId = selectedViolationGroupId ?: 0, // Use actual selected violation group ID
            billCleaningItemGroupId = selectedItemGroupId ?: 0, // Use actual selected item group ID
            billOriginCleaningItemId = selectedOriginItemId ?: 0, // Use actual selected origin item ID
            tenantId = 1,
            billOriginViEventGeoLocation = createGeoLocationData(visitDate, latitude, longitude)
        )
        
        Log.d("FormDataViewModel", "=== VIOLATION DATA OBJECT CREATED ===")
        Log.d("FormDataViewModel", "ViolationData Details (NEW API FORMAT):")
        Log.d("FormDataViewModel", "  - organIds: ${violationData.organIds}")
        Log.d("FormDataViewModel", "  - contractIds: ${violationData.contractIds}")
        Log.d("FormDataViewModel", "  - billCleaningViolationGroupIds: ${violationData.billCleaningViolationGroupIds}")
        Log.d("FormDataViewModel", "  - billCleaningViolationIds: ${violationData.billCleaningViolationIds}")
        Log.d("FormDataViewModel", "  - billCleaningItemGroupIds: ${violationData.billCleaningItemGroupIds}")
        Log.d("FormDataViewModel", "  - billCleaningItemIds: ${violationData.billCleaningItemIds}")
        Log.d("FormDataViewModel", "  - visitDate: ${violationData.visitDate}")
        Log.d("FormDataViewModel", "  - billOriginCleaningItemIds: ${violationData.billOriginCleaningItemIds}")
        Log.d("FormDataViewModel", "  - number: ${violationData.number}")
        Log.d("FormDataViewModel", "  - visitedFault: ${violationData.visitedFault}")
        Log.d("FormDataViewModel", "  - address: ${violationData.address}")
        Log.d("FormDataViewModel", "  - billCleaningViolationId: ${violationData.billCleaningViolationId}")
        Log.d("FormDataViewModel", "  - contractId: ${violationData.contractId}")
        Log.d("FormDataViewModel", "  - organId: ${violationData.organId}")
        Log.d("FormDataViewModel", "  - billCleaningViolationGroupId: ${violationData.billCleaningViolationGroupId}")
        Log.d("FormDataViewModel", "  - billCleaningItemGroupId: ${violationData.billCleaningItemGroupId}")
        Log.d("FormDataViewModel", "  - billOriginCleaningItemId: ${violationData.billOriginCleaningItemId}")
        Log.d("FormDataViewModel", "  - tenantId: ${violationData.tenantId}")
        Log.d("FormDataViewModel", "  - BillOriginViEventGeoLocation: INCLUDED WITH GPS DATA")

        // Additional validation for required fields
        Log.d("FormDataViewModel", "=== VALIDATING FORM DATA ===")
        if (violationData.number <= 0) {
            Log.e("FormDataViewModel", "Validation failed: Number is <= 0")
            _submissionState.value = SubmissionState.Error("لطفاً تعداد را وارد کنید.")
            return
        }

        if (violationData.address.isBlank()) {
            Log.e("FormDataViewModel", "Validation failed: Address is blank")
            _submissionState.value = SubmissionState.Error("لطفاً آدرس را وارد کنید.")
            return
        }

        if (violationData.visitedFault.isBlank()) {
            Log.e("FormDataViewModel", "Validation failed: Description is blank")
            _submissionState.value = SubmissionState.Error("لطفاً توضیحات را وارد کنید.")
            return
        }
        
        Log.d("FormDataViewModel", "All validations passed successfully")

        isSubmitting = true
        _submissionState.value = SubmissionState.Loading

        submitJob = viewModelScope.launch {
            try {
                Log.d("FormDataViewModel", "=== STARTING API REQUEST ===")

                // مرحله ۱: تبدیل آبجکت درخواست به رشته JSON
                val gson = Gson()
                val submitViolationRequest = SubmitViolationRequest(violationData)
                val modelJson = gson.toJson(submitViolationRequest)
                
                Log.d("FormDataViewModel", "=== JSON CONVERSION ===")
                Log.d("FormDataViewModel", "SubmitViolationRequest object created successfully")
                Log.d("FormDataViewModel", "JSON string length: ${modelJson.length}")
                Log.d("FormDataViewModel", "JSON content: $modelJson")
                
                // Log the request details with form data
                Log.d("API_REQUEST", "=== VIOLATION SUBMISSION REQUEST ===")
                Log.d("API_REQUEST", "Endpoint: POST /api/BillOriginViEvent/Create")
                Log.d("API_REQUEST", "Authorization: Will be added automatically by AuthInterceptor")
                Log.d("API_REQUEST", "Form Data:")
                Log.d("API_REQUEST", "  - Organ ID: ${selectedOrganId}")
                Log.d("API_REQUEST", "  - Contract ID: ${selectedContractId}")
                Log.d("API_REQUEST", "  - Violation ID: ${selectedViolationId}")
                Log.d("API_REQUEST", "  - Violation Group ID: ${selectedViolationGroupId}")
                Log.d("API_REQUEST", "  - Item Group ID: ${selectedItemGroupId}")
                Log.d("API_REQUEST", "  - Origin Item ID: ${selectedOriginItemId}")
                Log.d("API_REQUEST", "  - Visit Date: $visitDate")
                Log.d("API_REQUEST", "  - Number: $number")
                Log.d("API_REQUEST", "  - Address: $address")
                Log.d("API_REQUEST", "  - Description: $description")
                Log.d("API_REQUEST", "  - Images Count: ${imageUris.size}")
                Log.d("API_REQUEST", "Request Body (JSON): $modelJson")
                Log.d("API_REQUEST", "Form Parameters:")
                Log.d("API_REQUEST", "  - model: [MultipartBody.Part]")
                Log.d("API_REQUEST", "=== END REQUEST LOG ===")

                // مرحله ۲: ساخت MultipartBody.Part
                val modelPart = MultipartBody.Part.createFormData("model", modelJson)

                // مرحله ۳: تبدیل تصاویر به MultipartBody.Part
                Log.d("FormDataViewModel", "=== PROCESSING IMAGES ===")
                Log.d("FormDataViewModel", "Total images to process: ${imageUris.size}")
                val fileParts = mutableListOf<MultipartBody.Part>()
                imageUris.forEachIndexed { index, uri ->
                    try {
                        Log.d("FormDataViewModel", "Processing image $index: $uri")
                        val inputStream = application.contentResolver.openInputStream(uri)
                        val fileName = "image_$index.jpg"
                        val requestBody = inputStream?.let { stream ->
                            val bytes = stream.readBytes()
                            stream.close()
                            Log.d("FormDataViewModel", "Image $index processed successfully, size: ${bytes.size} bytes")
                            bytes.toRequestBody("image/*".toMediaTypeOrNull())
                        }
                        
                        requestBody?.let { body ->
                            val filePart = MultipartBody.Part.createFormData("files", fileName, body)
                            fileParts.add(filePart)
                            Log.d("FormDataViewModel", "File part created for image $index: $fileName")
                        } ?: run {
                            Log.e("FormDataViewModel", "Failed to create request body for image $index")
                        }
                    } catch (e: Exception) {
                        Log.e("FormDataViewModel", "Error processing image $index: ${e.message}", e)
                    }
                }
                Log.d("FormDataViewModel", "Total file parts created: ${fileParts.size}")

                // مرحله ۴: فراخوانی سرویس Retrofit (AuthInterceptor خودکار توکن را اضافه می‌کند)
                Log.d("FormDataViewModel", "=== MAKING API CALL ===")
                Log.d("FormDataViewModel", "Calling violationApiService.createViolation...")
                Log.d("FormDataViewModel", "Number of files to upload: ${fileParts.size}")
                Log.d("FormDataViewModel", "Model part created successfully")
                Log.d("API_REQUEST", "Making API call to violationApiService.createViolation...")
                Log.d("API_REQUEST", "Number of files to upload: ${fileParts.size}")
                val response = violationApiService.createViolation(modelPart, fileParts)

                // Log the response
                Log.d("FormDataViewModel", "=== API RESPONSE RECEIVED ===")
                Log.d("FormDataViewModel", "Response Code: ${response.code()}")
                Log.d("FormDataViewModel", "Response Message: ${response.message()}")
                Log.d("FormDataViewModel", "Is Successful: ${response.isSuccessful}")
                
                Log.d("API_RESPONSE", "=== VIOLATION SUBMISSION RESPONSE ===")
                Log.d("API_RESPONSE", "Response Code: ${response.code()}")
                Log.d("API_RESPONSE", "Response Message: ${response.message()}")
                Log.d("API_RESPONSE", "Is Successful: ${response.isSuccessful}")
                
                if (response.body() != null) {
                    val responseBodyJson = gson.toJson(response.body())
                    Log.d("FormDataViewModel", "Response Body: $responseBodyJson")
                    Log.d("API_RESPONSE", "Response Body: $responseBodyJson")
                } else {
                    Log.d("FormDataViewModel", "Response Body: null")
                    Log.d("API_RESPONSE", "Response Body: null")
                }
                
                // Store error body string once to avoid stream consumption issues
                val errorBodyString = response.errorBody()?.string()
                if (errorBodyString != null) {
                    Log.d("FormDataViewModel", "Error Body: $errorBodyString")
                    Log.d("API_RESPONSE", "Error Body: $errorBodyString")
                }
                Log.d("API_RESPONSE", "=== END RESPONSE LOG ===")

                // مرحله ۵: مدیریت پاسخ سرور
                Log.d("FormDataViewModel", "=== PROCESSING RESPONSE ===")
                if (response.isSuccessful && response.body() != null) {
                    // اگر پاسخ موفقیت آمیز بود و شامل ID بود
                    val successBody = response.body()!!
                    val newId = successBody.billOriginEventId.toString()
                    Log.d("FormDataViewModel", "=== SUCCESS ===")
                    Log.d("FormDataViewModel", "Violation submitted successfully!")
                    Log.d("FormDataViewModel", "Response body: ${gson.toJson(successBody)}")
                    Log.d("FormDataViewModel", "Generated violation ID: $newId")
                    Log.d("API_SUCCESS", "Violation submitted successfully with ID: $newId")
                    _submissionState.value = SubmissionState.Success("تخلف با موفقیت ثبت شد", newId)

                } else {
                    // اگر پاسخ سرور شامل پیام خطا بود
                    Log.d("FormDataViewModel", "=== ERROR RESPONSE ===")
                    Log.d("FormDataViewModel", "Response was not successful or body is null")
                    Log.d("FormDataViewModel", "Response code: ${response.code()}")
                    Log.d("FormDataViewModel", "Response message: ${response.message()}")
                    
                    var errorMessage = when (response.code()) {
                        401 -> {
                            Log.e("FormDataViewModel", "401 UNAUTHORIZED - Token refresh should have handled this")
                            Log.e("FormDataViewModel", "This indicates either:")
                            Log.e("FormDataViewModel", "  1. Refresh token is invalid/expired")
                            Log.e("FormDataViewModel", "  2. AuthInterceptor is not properly configured")
                            Log.e("FormDataViewModel", "  3. Token refresh failed")
                            "خطای احراز هویت. لطفاً دوباره وارد شوید."
                        }
                        403 -> "دسترسی مجاز نیست"
                        404 -> "سرویس مورد نظر یافت نشد"
                        500 -> "خطای سرور داخلی"
                        else -> "خطای نامشخص از سرور (کد: ${response.code()})"
                    }
                    if (errorBodyString != null && errorBodyString.isNotEmpty()) {
                        try {
                            Log.d("FormDataViewModel", "Raw error body: $errorBodyString")
                            Log.d("API_ERROR_DETAIL", "Raw error body: $errorBodyString")
                            
                            // Try to parse with the new error response structure
                            val errorResponse = gson.fromJson(errorBodyString, ApiErrorResponse::class.java)
                            if (errorResponse != null && !errorResponse.message.isNullOrEmpty()) {
                                errorMessage = errorResponse.message
                                Log.d("FormDataViewModel", "Parsed error message: $errorMessage")
                                Log.d("API_ERROR_PARSED", "Parsed error message: $errorMessage")
                            } else {
                                Log.e("FormDataViewModel", "Error response is null or message is empty")
                                Log.e("API_ERROR_PARSE", "Error response is null or message is empty")
                                errorMessage = "پاسخ سرور قابل پردازش نیست. کد خطا: ${response.code()}"
                            }
                        } catch (e: Exception) {
                            Log.e("FormDataViewModel", "Error parsing error response", e)
                            Log.e("API_ERROR_PARSE", "Error parsing error response", e)
                            errorMessage = "پاسخ سرور قابل پردازش نیست. کد خطا: ${response.code()}"
                        }
                    } else {
                        Log.e("FormDataViewModel", "Error body is null or empty")
                        Log.e("API_ERROR_PARSE", "Error body is null or empty")
                        errorMessage = "پاسخ سرور قابل پردازش نیست. کد خطا: ${response.code()}"
                    }
                    Log.e("FormDataViewModel", "Violation submission failed: $errorMessage")
                    Log.e("API_ERROR", "Violation submission failed: $errorMessage")
                    _submissionState.value = SubmissionState.Error(errorMessage)                }

            } catch (e: CancellationException) {
                Log.d("FormDataViewModel", "Violation submission cancelled")
                _submissionState.value = null
                throw e
            } catch (e: Exception) {
                // مدیریت خطاهای کلی مانند خطای شبکه
                Log.e("FormDataViewModel", "=== EXCEPTION OCCURRED ===")
                Log.e("FormDataViewModel", "Exception during violation submission", e)
                Log.e("FormDataViewModel", "Exception message: ${e.message}")
                Log.e("FormDataViewModel", "Exception type: ${e.javaClass.simpleName}")
                Log.e("API_EXCEPTION", "Exception during violation submission", e)
                _submissionState.value = SubmissionState.Error("خطا در ارتباط با سرور: " + e.message)
            } finally {
                isSubmitting = false
                submitJob = null
            }
        }
    }
    /**
     * Create GPS location data for the violation submission
     * For now, using fixed values similar to the example, but this should be updated
     * to use actual GPS data when available
     */
    private fun createGeoLocationData(visitDate: String, lat: Double, lon: Double): BillOriginViEventGeoLocation {
        // Use actual GPS coordinates if provided, otherwise use default values
        val latitude = if (lat != 0.0) lat else 37.28
        val longitude = if (lon != 0.0) lon else 49.589
        val elevation = 12.5
        val accuracy = 5.0
        val altitudeAccuracy = 2.0
        val heading = 180.0
        val speed = 0.0
        val cBaro = 1013.25
        val satelliteNumber = 7
        
        Log.d("FormDataViewModel", "=== CREATING GPS LOCATION DATA ===")
        Log.d("FormDataViewModel", "Latitude: $latitude, Longitude: $longitude")
        Log.d("FormDataViewModel", "Elevation: $elevation, Accuracy: $accuracy")
        
        val geoJson = "{\"type\":\"Point\",\"coordinates\":[$longitude,$latitude]}"
        val wellKnownText = "POINT($longitude $latitude)"
        
        val gisGeolocationTrack = GisGeolocationTrack(
            latitude = latitude,
            longitude = longitude,
            elevation = elevation,
            time = visitDate, // Use the same date/time as visit date
            magneticVariant = null,
            cBaro = cBaro,
            accuracy = accuracy,
            altitudeAccuracy = altitudeAccuracy,
            heading = heading,
            speed = speed,
            type = "ViEvent",
            satelliteNumber = satelliteNumber
        )
        
        val geoLocation = BillOriginViEventGeoLocation(
            geoJson = geoJson,
            wellKnownText = wellKnownText,
            gisGeolocationTrack = gisGeolocationTrack
        )
        
        Log.d("FormDataViewModel", "Generated GeoJSON: $geoJson")
        Log.d("FormDataViewModel", "Generated WellKnownText: $wellKnownText")
        Log.d("FormDataViewModel", "GisGeolocationTrack - Time: $visitDate, Type: ViEvent")
        Log.d("FormDataViewModel", "===================================")
        
        return geoLocation
    }

    fun clearError() {
        _error.value = null
    }

    private fun parseFormDataError(response: Response<FormDataResponse>): String {
        val errorBody = try {
            response.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        if (!errorBody.isNullOrBlank()) {
            try {
                val json = org.json.JSONObject(errorBody)
                val message = json.optString("message")
                    .ifBlank { json.optString("error_description") }
                    .ifBlank { json.optString("error") }
                if (message.isNotBlank()) {
                    return message
                }
            } catch (_: Exception) {
            }
        }
        return "خطا در دریافت اطلاعات فرم (کد ${response.code()})"
    }

    fun clearSubmissionState() {
        _submissionState.value = null
    }

    // یک کلاس sealed برای مدیریت وضعیت‌ها تعریف کنید
    sealed class SubmissionState {
        object Loading : SubmissionState()
        data class Success(val message: String, val violationId: String) : SubmissionState()
        data class Error(val message: String) : SubmissionState()
    }
} 
