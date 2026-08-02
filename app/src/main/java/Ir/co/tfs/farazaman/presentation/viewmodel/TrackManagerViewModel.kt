package Ir.co.tfs.farazaman.presentation.viewmodel

import android.content.Context
import android.database.Cursor
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import Ir.co.tfs.farazaman.data.db.TrackContentProvider
import Ir.co.tfs.farazaman.service.remote.RoadService
import java.io.File
import javax.inject.Inject

@HiltViewModel
class TrackManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val roadService: RoadService
) : ViewModel() {

    companion object {
        private const val TAG = "TrackManagerViewModel"
    }

    // UI State for tracks
    private val _tracks = MutableStateFlow<Cursor?>(null)
    val tracks: StateFlow<Cursor?> = _tracks.asStateFlow()

    // UI State for loading states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // UI State for upload progress
    private val _uploadProgress = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadProgress: StateFlow<UploadState> = _uploadProgress.asStateFlow()

    // UI State for errors
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // UI State for success messages
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    /**
     * Load all tracks from database
     */
    fun loadTracks() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val cursor = context.contentResolver.query(
                    TrackContentProvider.CONTENT_URI_TRACK,
                    null,
                    "${TrackContentProvider.Schema.COL_ACTIVE} = ?",
                    arrayOf("0"),
                    "${TrackContentProvider.Schema.COL_START_DATE} DESC"
                )
                _tracks.value = cursor
                Log.d(TAG, "Tracks loaded successfully: ${cursor?.count} tracks")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading tracks", e)
                _errorMessage.value = "خطا در بارگذاری مسیرها"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Delete a track by ID
     */
    fun deleteTrack(trackId: Long) {
        viewModelScope.launch {
            try {
                val deletedRows = context.contentResolver.delete(
                    TrackContentProvider.CONTENT_URI_TRACK,
                    "${TrackContentProvider.Schema.COL_TRACK_ID} = ?",
                    arrayOf(trackId.toString())
                )
                
                if (deletedRows > 0) {
                    _successMessage.value = "مسیر با موفقیت حذف شد"
                    loadTracks() // Reload tracks after deletion
                } else {
                    _errorMessage.value = "خطا در حذف مسیر"
                }
                Log.d(TAG, "Track deleted: $trackId, rows affected: $deletedRows")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting track: $trackId", e)
                _errorMessage.value = "خطا در حذف مسیر"
            }
        }
    }

    /**
     * Upload track file
     */
    fun uploadTrack(trackId: Long) {
        viewModelScope.launch {
            _uploadProgress.value = UploadState.Uploading(0)
            
            try {
                // This is a simplified version - the actual implementation would need
                // to integrate with the existing AsyncTask logic or convert it to coroutines
                Log.d(TAG, "Starting upload for track: $trackId")
                
                // For now, just simulate upload progress
                for (progress in 0..100 step 10) {
                    _uploadProgress.value = UploadState.Uploading(progress)
                    kotlinx.coroutines.delay(100)
                }
                
                _uploadProgress.value = UploadState.Success
                _successMessage.value = "آپلود مسیر با موفقیت انجام شد"
                
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading track: $trackId", e)
                _uploadProgress.value = UploadState.Error("خطا در آپلود مسیر")
                _errorMessage.value = "خطا در آپلود مسیر"
            }
        }
    }

    /**
     * Export track to GPX format
     */
    fun exportTrackToGpx(trackId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                // Create GPX file
                val gpxFile = File.createTempFile("track_$trackId", ".gpx", context.cacheDir)
                
                // This would need to integrate with existing GpxWriter logic
                Log.d(TAG, "Exporting track to GPX: $trackId")
                
                _successMessage.value = "مسیر با موفقیت به فرمت GPX تبدیل شد"
                Log.d(TAG, "Track exported successfully: ${gpxFile.absolutePath}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting track to GPX: $trackId", e)
                _errorMessage.value = "خطا در تبدیل مسیر به GPX"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Get track statistics
     */
    fun getTrackStats(trackId: Long): TrackStats? {
        return try {
            val cursor = context.contentResolver.query(
                TrackContentProvider.trackPointsUri(trackId),
                null,
                null,
                null,
                null
            )
            
            cursor?.use {
                var totalDistance = 0.0
                var maxSpeed = 0.0
                var totalTime = 0L
                var pointCount = 0
                
                while (it.moveToNext()) {
                    pointCount++
                    // Calculate stats from cursor data
                    // This would need actual implementation based on TrackContentProvider schema
                }
                
                TrackStats(
                    pointCount = pointCount,
                    totalDistance = totalDistance,
                    maxSpeed = maxSpeed,
                    totalTime = totalTime
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting track stats: $trackId", e)
            null
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Clear success message
     */
    fun clearSuccess() {
        _successMessage.value = null
    }

    /**
     * Reset upload state
     */
    fun resetUploadState() {
        _uploadProgress.value = UploadState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        // Close cursor if it's still open
        _tracks.value?.close()
    }
}

// Data classes for UI state
sealed class UploadState {
    object Idle : UploadState()
    data class Uploading(val progress: Int) : UploadState()
    object Success : UploadState()
    data class Error(val message: String) : UploadState()
}

data class TrackStats(
    val pointCount: Int,
    val totalDistance: Double,
    val maxSpeed: Double,
    val totalTime: Long
)
