package Ir.co.tfs.farazaman.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import Ir.co.tfs.farazaman.data.api.FormDataApiService
import Ir.co.tfs.farazaman.data.model.RoadData
import Ir.co.tfs.farazaman.service.remote.RoadService
import Ir.co.tfs.farazaman.service.remote.ApiService
import Ir.co.tfs.farazaman.util.RoadQueryBuffer
import Ir.co.tfs.farazaman.data.model.GisLayerIndexResponse
import com.google.gson.Gson
import javax.inject.Inject

@HiltViewModel
class DisplayTrackMapViewModel @Inject constructor(
    private val roadService: RoadService,
    private val apiService: ApiService,
    private val formDataApiService: FormDataApiService
) : ViewModel() {

    companion object {
        private const val TAG = "DisplayTrackMapViewModel"
    }

    // UI State for road information
    private val _roadInfo = MutableStateFlow(RoadInfo())
    val roadInfo: StateFlow<RoadInfo> = _roadInfo.asStateFlow()

    // UI State for GIS layers
    private val _gisLayers = MutableStateFlow<List<SpeedDialLayerItem>>(emptyList())
    val gisLayers: StateFlow<List<SpeedDialLayerItem>> = _gisLayers.asStateFlow()

    // UI State for loading states
    private val _isLoadingRoadInfo = MutableStateFlow(false)
    val isLoadingRoadInfo: StateFlow<Boolean> = _isLoadingRoadInfo.asStateFlow()

    private val _isLoadingGisLayers = MutableStateFlow(false)
    val isLoadingGisLayers: StateFlow<Boolean> = _isLoadingGisLayers.asStateFlow()

    // UI State for errors
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // UI State for mission details
    private val _missionDetails = MutableStateFlow<Any?>(null)
    val missionDetails: StateFlow<Any?> = _missionDetails.asStateFlow()

    /**
     * Fetch road information for the given coordinates
     */
    fun fetchRoadInfo(
        latitude: Double,
        longitude: Double,
        buffer: Int = RoadQueryBuffer.BUFFER_DEFAULT,
    ) {
        viewModelScope.launch {
            _isLoadingRoadInfo.value = true
            _errorMessage.value = null
            
            try {
                Log.d(TAG, "Fetching road info for: $latitude, $longitude (buffer=$buffer)")
                val roadDataList = roadService.getRoadData(
                    latitude = latitude,
                    longitude = longitude,
                    buffer = buffer,
                )
                
                if (roadDataList.isNotEmpty()) {
                    val firstRoadResult = roadDataList[0]
                    _roadInfo.value = RoadInfo(
                        name = firstRoadResult.name,
                        fclass = firstRoadResult.fclass,
                        maxspeed = firstRoadResult.maxspeed.toString(),
                        isValid = true
                    )
                    Log.d(TAG, "Road info fetched successfully: ${firstRoadResult.name}")
                } else {
                    _roadInfo.value = RoadInfo(
                        name = "آدرسی یافت نشد",
                        fclass = "",
                        maxspeed = "",
                        isValid = false
                    )
                    Log.d(TAG, "No road data found for location")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch road data", e)
                _roadInfo.value = RoadInfo(
                    name = "خطا در دریافت اطلاعات",
                    fclass = "",
                    maxspeed = "",
                    isValid = false
                )
                _errorMessage.value = "خطا در دریافت اطلاعات جاده"
            } finally {
                _isLoadingRoadInfo.value = false
            }
        }
    }

    /**
     * Fetch address for violation reporting
     */
    fun fetchAddressForViolation(
        latitude: Double,
        longitude: Double,
        buffer: Int = RoadQueryBuffer.BUFFER_DEFAULT,
        callback: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val roadDataList = roadService.getRoadData(
                    latitude = latitude,
                    longitude = longitude,
                    buffer = buffer,
                )
                if (roadDataList.isNotEmpty()) {
                    val address = roadDataList[0].name
                    Log.d(TAG, "Address fetched for violation: $address")
                    callback(address)
                } else {
                    Log.d(TAG, "No road data found for location: $latitude, $longitude")
                    callback("")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch address for violation", e)
                callback("")
            }
        }
    }

    /**
     * Fetch GIS layer index for speed dial
     */
    fun fetchGisLayers() {
        viewModelScope.launch {
            _isLoadingGisLayers.value = true
            _errorMessage.value = null
            
            try {
                val response = apiService.getGisLayerIndex()
                if (response.isSuccessful) {
                    val body = response.body()?.string()
                    if (body != null) {
                        val gisLayerIndex = Gson().fromJson(body, GisLayerIndexResponse::class.java)
                        val layers = gisLayerIndex.data.filter { it.pickUpByApp == true }.map { layer ->
                            SpeedDialLayerItem(
                                id = layer.gisLayersId,
                                name = layer.name,
                                iconUrl = layer.icon
                            )
                        }
                        
                        _gisLayers.value = layers
                        Log.d(TAG, "GIS layers fetched successfully: ${layers.size} layers")
                    }
                } else {
                    Log.e(TAG, "Failed to fetch GIS layers: ${response.code()}")
                    _errorMessage.value = "خطا در دریافت لایه‌های نقشه"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while fetching GIS layers", e)
                _errorMessage.value = "خطا در دریافت لایه‌های نقشه"
            } finally {
                _isLoadingGisLayers.value = false
            }
        }
    }

    /**
     * Fetch mission details by encryption
     */
    fun fetchMissionDetails(encryption: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Fetching mission details for encryption: $encryption")
                val response = formDataApiService.getMissionDetails(encryption)
                
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    Log.d(TAG, "Mission details fetched successfully")
                    _missionDetails.value = responseBody
                } else {
                    val errorBody = response.errorBody()?.string() ?: "No error body"
                    Log.e(TAG, "Failed to fetch mission details: ${response.code()} - $errorBody")
                    _errorMessage.value = "خطا در دریافت اطلاعات منطقه: ${response.code()}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while fetching mission details", e)
                _errorMessage.value = "خطا در دریافت اطلاعات منطقه"
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Clear mission details
     */
    fun clearMissionDetails() {
        _missionDetails.value = null
    }
}

// Data classes for UI state
data class RoadInfo(
    val name: String = "",
    val fclass: String = "",
    val maxspeed: String = "",
    val isValid: Boolean = false
)

data class SpeedDialLayerItem(
    val id: Int,
    val name: String,
    val iconUrl: String?
)
