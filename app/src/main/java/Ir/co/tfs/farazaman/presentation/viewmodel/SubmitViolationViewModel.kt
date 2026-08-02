package Ir.co.tfs.farazaman.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import Ir.co.tfs.farazaman.data.model.RoadData
import Ir.co.tfs.farazaman.service.remote.RoadService
import Ir.co.tfs.farazaman.util.RoadQueryBuffer
import javax.inject.Inject

@HiltViewModel
class SubmitViolationViewModel @Inject constructor(
    private val roadService: RoadService
) : ViewModel() {

    companion object {
        private const val TAG = "SubmitViolationViewModel"
    }

    // UI State for road data
    private val _roadData = MutableStateFlow<RoadData?>(null)
    val roadData: StateFlow<RoadData?> = _roadData.asStateFlow()

    // UI State for loading
    private val _isLoadingRoadData = MutableStateFlow(false)
    val isLoadingRoadData: StateFlow<Boolean> = _isLoadingRoadData.asStateFlow()

    // UI State for errors
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Fetch road data for the given coordinates
     */
    fun fetchRoadData(
        latitude: Double,
        longitude: Double,
        buffer: Int = RoadQueryBuffer.BUFFER_DEFAULT,
    ) {
        viewModelScope.launch {
            _isLoadingRoadData.value = true
            _errorMessage.value = null
            
            try {
                Log.d(TAG, "Fetching road data for: $latitude, $longitude (buffer=$buffer)")
                val roadDataList = roadService.getRoadData(latitude, longitude, buffer)
                
                if (roadDataList.isNotEmpty()) {
                    _roadData.value = roadDataList[0]
                    Log.d(TAG, "Road data fetched successfully: ${roadDataList[0].name}")
                } else {
                    _roadData.value = null
                    Log.d(TAG, "No road data found for location")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching road data", e)
                _errorMessage.value = "خطا در دریافت اطلاعات جاده"
                _roadData.value = null
            } finally {
                _isLoadingRoadData.value = false
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
     * Clear road data
     */
    fun clearRoadData() {
        _roadData.value = null
    }
}

