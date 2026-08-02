package Ir.co.tfs.farazaman.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import Ir.co.tfs.farazaman.OSMTracker
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.data.db.DataHelper
import Ir.co.tfs.farazaman.data.db.TrackContentProvider
import Ir.co.tfs.farazaman.gpx.ZipHelper
import Ir.co.tfs.farazaman.service.gps.GPSLogger
import Ir.co.tfs.farazaman.util.LoadingDialog
import Ir.co.tfs.farazaman.util.FileSystemUtils
import Ir.co.tfs.farazaman.util.TokenManager
import Ir.co.tfs.farazaman.util.UserManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import saman.zamani.persiandate.PersianDate
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class DriverActivity : AppCompatActivity() {

    companion object {
        private val TAG = DriverActivity::class.java.simpleName
        private const val TRACK_ID_NO_TRACK = -1L
        private const val REFRESH_INTERVAL_MS = 3000L
        private const val LOCATION_PERMISSION_REQUEST_CODE = 6
    }

    @Inject
    lateinit var tokenManager: TokenManager
    
    @Inject
    lateinit var okHttpClient: OkHttpClient
    
    @Inject
    lateinit var userManager: UserManager

    private var currentTrackId: Long = TRACK_ID_NO_TRACK
    private var pendingTrackId: Long = TRACK_ID_NO_TRACK
    private var currentZoneWorkVehicleId: Int = -1
    private var currentZoneInfoSummary: String = ""

    private lateinit var missionCard: MaterialCardView
    private lateinit var emptyView: View
    private lateinit var startMissionBtn: MaterialButton
    private lateinit var stopOrResumeBtn: MaterialButton
    private lateinit var endMissionBtn: MaterialButton
    private lateinit var waypointsCount: TextView
    private lateinit var trackpointsCount: TextView
    private lateinit var zoneInfoSummary: TextView
    private lateinit var uploadProgressBar: ProgressBar
    private lateinit var missionOptions: ImageButton

    private var refreshHandler: android.os.Handler? = null
    private var refreshRunnable: Runnable? = null
    private var loadingDialog: LoadingDialog? = null
    
    private fun selectedBaseUrl(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val base = prefs.getString("BASE_URL", "https://app.tfs.co.ir") ?: "https://app.tfs.co.ir"
        return if (base.endsWith("/")) base else "$base/"
    }

    private fun buildUrl(path: String): String {
        val base = selectedBaseUrl()
        return if (path.startsWith("/")) base + path.substring(1) else base + path
    }
    
    /**
     * Generate filename for driver track upload
     * Format: {تاریخ شمسی}_{userName}_راننده
     */
    private fun generateDriverFileName(trackId: Long): String {
        val userName = userManager.getUserName() ?: "unknown"
        val persianDate = DataHelper.PERSIAN_FILENAME_FORMATTER.format(saman.zamani.persiandate.PersianDate())
        
        // Clean the filename by replacing invalid file system characters
        val fileName = "${persianDate}_${userName}_راننده_$trackId"
        return fileName.replace("[/\\\\:*?\"<>|]".toRegex(), "_")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        missionCard = findViewById(R.id.mission_card)
        emptyView = findViewById(R.id.empty_view)
        startMissionBtn = findViewById(R.id.start_mission_btn)
        stopOrResumeBtn = findViewById(R.id.stop_or_resume)
        endMissionBtn = findViewById(R.id.end_mission)
        waypointsCount = findViewById(R.id.waypoints_count)
        trackpointsCount = findViewById(R.id.trackpoints_count)
        zoneInfoSummary = findViewById(R.id.zone_info_summary)
        uploadProgressBar = findViewById(R.id.upload_progressbar)
        missionOptions = findViewById(R.id.mission_options)

        // Set driver mode flag in SharedPreferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putBoolean("is_driver_mode", true).apply()

        startMissionBtn.setOnClickListener {
            startNewMission()
        }

        stopOrResumeBtn.setOnClickListener {
            handleStopOrResume()
        }

        endMissionBtn.setOnClickListener {
            handleEndMission()
        }

        missionOptions.setOnClickListener {
            showOptionsMenu()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        startPeriodicRefresh()
    }

    override fun onPause() {
        super.onPause()
        stopPeriodicRefresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear driver mode flag
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putBoolean("is_driver_mode", false).apply()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateUI() {
        // Only update currentTrackId if it's not set yet
        if (currentTrackId == TRACK_ID_NO_TRACK) {
            currentTrackId = DataHelper.getActiveTrackId(contentResolver, "driver")
            
            // Also load zone info from SharedPreferences if track exists
            if (currentTrackId != TRACK_ID_NO_TRACK) {
                loadZoneInfoFromPreferences()
            }
        }
        
        if (currentTrackId != TRACK_ID_NO_TRACK) {
            // Show mission card
            missionCard.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            startMissionBtn.visibility = View.GONE
            
            // Update zone info summary
            if (currentZoneInfoSummary.isNotEmpty()) {
                zoneInfoSummary.text = currentZoneInfoSummary
                zoneInfoSummary.visibility = View.VISIBLE
            } else {
                zoneInfoSummary.visibility = View.GONE
            }
            
            // Update counts
            updateTrackCounts()
            
            // Check if tracking is active
            if (isTrackActive(currentTrackId)) {
                stopOrResumeBtn.text = "توقف"
                stopOrResumeBtn.backgroundTintList = ContextCompat.getColorStateList(this, R.color.brand_green)
            } else {
                stopOrResumeBtn.text = getString(R.string.trackmgr_contextmenu_resume)
                stopOrResumeBtn.backgroundTintList = ContextCompat.getColorStateList(this, R.color.brand_green_dark)
            }
        } else {
            // Show empty view
            missionCard.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            startMissionBtn.visibility = View.VISIBLE
        }
    }

    private fun updateTrackCounts() {
        if (currentTrackId == TRACK_ID_NO_TRACK) return
        
        // Get waypoints count
        val waypointsCursor = contentResolver.query(
            TrackContentProvider.waypointsUri(currentTrackId),
            null, null, null, null
        )
        val wpCount = waypointsCursor?.count ?: 0
        waypointsCursor?.close()
        
        // Get trackpoints count
        val trackpointsCursor = contentResolver.query(
            TrackContentProvider.trackPointsUri(currentTrackId),
            null, null, null, null
        )
        val tpCount = trackpointsCursor?.count ?: 0
        trackpointsCursor?.close()
        
        waypointsCount.text = wpCount.toString()
        trackpointsCount.text = tpCount.toString()
    }

    private fun startNewMission() {
        // Show zone selection bottom sheet
        val bottomSheet = ZoneSelectionBottomSheet.newInstance { selectedZone ->
            Log.d(TAG, "Zone selected in callback: ${selectedZone.zoneWorkVehicleID}")
            currentZoneWorkVehicleId = selectedZone.zoneWorkVehicleID
            currentZoneInfoSummary = selectedZone.getShortSummary()
            
            // Now start mission with selected zone
            if (checkLocationPermissions()) {
                createAndStartMission()
            } else {
                requestLocationPermissions(-1L)
            }
        }
        bottomSheet.show(supportFragmentManager, "ZoneSelectionBottomSheet")
    }

    private fun createAndStartMission() {
        try {
            currentTrackId = createNewTrack()
            
            // Save zone info to SharedPreferences
            saveZoneInfoToPreferences()
            
            startGPSTracking(currentTrackId)
            updateUI()
            Toast.makeText(this, "ماموریت شروع شد", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در شروع ماموریت: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun saveZoneInfoToPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().apply {
            putInt("current_zone_work_vehicle_id", currentZoneWorkVehicleId)
            putString("current_zone_info_summary", currentZoneInfoSummary)
            putLong("current_track_id_for_zone", currentTrackId)
            apply()
        }
        Log.d(TAG, "Saved zone info to preferences: zoneId=$currentZoneWorkVehicleId, trackId=$currentTrackId")
    }
    
    private fun loadZoneInfoFromPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val savedTrackId = prefs.getLong("current_track_id_for_zone", -1L)
        
        // Only load if the saved track ID matches current track ID
        if (savedTrackId == currentTrackId) {
            currentZoneWorkVehicleId = prefs.getInt("current_zone_work_vehicle_id", -1)
            currentZoneInfoSummary = prefs.getString("current_zone_info_summary", "") ?: ""
            Log.d(TAG, "Loaded zone info from preferences: zoneId=$currentZoneWorkVehicleId, summary=$currentZoneInfoSummary")
        }
    }
    
    private fun clearZoneInfoFromPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().apply {
            remove("current_zone_work_vehicle_id")
            remove("current_zone_info_summary")
            remove("current_track_id_for_zone")
            apply()
        }
        Log.d(TAG, "Cleared zone info from preferences")
    }

    private fun createNewTrack(): Long {
        val values = ContentValues()
        values.put(TrackContentProvider.Schema.COL_NAME, "driver_" + DataHelper.PERSIAN_FILENAME_FORMATTER.format(PersianDate()))
        values.put(TrackContentProvider.Schema.COL_START_DATE, Date().time)
        values.put(TrackContentProvider.Schema.COL_ACTIVE, TrackContentProvider.Schema.VAL_TRACK_ACTIVE)
        values.put(TrackContentProvider.Schema.COL_ROLE, "driver") // Set role as driver
        val trackUri = contentResolver.insert(TrackContentProvider.CONTENT_URI_TRACK, values)
        val trackId = ContentUris.parseId(trackUri!!)
        return trackId
    }

    private fun handleStopOrResume() {
        if (currentTrackId == TRACK_ID_NO_TRACK) return
        
        if (isTrackActive(currentTrackId)) {
            // Stop tracking
            stopActiveTrack()
            Toast.makeText(this, "ماموریت متوقف شد", Toast.LENGTH_SHORT).show()
        } else {
            // Resume tracking
            if (checkLocationPermissions()) {
                startGPSTracking(currentTrackId)
                Toast.makeText(this, "ماموریت از سر گرفته شد", Toast.LENGTH_SHORT).show()
            } else {
                requestLocationPermissions(currentTrackId)
            }
        }
        updateUI()
    }

    private fun handleEndMission() {
        if (currentTrackId == TRACK_ID_NO_TRACK) return
        
        MaterialAlertDialogBuilder(this)
            .setTitle("اتمام و آپلود ماموریت")
            .setMessage("با آپلود، این ماموریت از لیست شما حذف خواهد شد. آیا ادامه می‌دهید؟")
            .setCancelable(true)
            .setPositiveButton("تایید و آپلود") { dialog, _ ->
                startUploadProcess(currentTrackId)
                dialog.dismiss()
            }
            .setNegativeButton("لغو") { dialog, _ -> dialog.cancel() }
            .show()
    }

    private fun startUploadProcess(trackId: Long) {
        stopActiveTrack()
        val dataHelper = DataHelper(this)
        dataHelper.stopTracking(trackId)
        
        loadingDialog = LoadingDialog.show(this, "در حال آپلود...")
        UploadTrackTask(this, trackId).execute()
    }

    private fun checkLocationPermissions(): Boolean {
        val hasFineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasForegroundService = ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED
        return hasFineLocation && hasCoarseLocation && hasForegroundService
    }

    private fun requestLocationPermissions(trackId: Long) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE
        )
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), LOCATION_PERMISSION_REQUEST_CODE)
        pendingTrackId = trackId
    }

    private fun startGPSTracking(trackId: Long) {
        // Set track as active
        val values = ContentValues()
        values.put(TrackContentProvider.Schema.COL_ACTIVE, TrackContentProvider.Schema.VAL_TRACK_ACTIVE)
        contentResolver.update(TrackContentProvider.CONTENT_URI_TRACK, values, TrackContentProvider.Schema.COL_ID + " = ?", arrayOf(trackId.toString()))
        currentTrackId = trackId
        
        // Start the GPS service
        val startTrackingIntent = Intent(this, GPSLogger::class.java).apply {
            action = OSMTracker.INTENT_START_TRACKING
            putExtra(TrackContentProvider.Schema.COL_TRACK_ID, trackId)
        }
        startService(startTrackingIntent)
    }

    private fun stopActiveTrack() {
        if (currentTrackId != TRACK_ID_NO_TRACK) {
            // Stop GPS tracking service
            val intent = Intent(OSMTracker.INTENT_STOP_TRACKING)
            intent.setPackage(this.packageName)
            sendBroadcast(intent)
            
            // Set track as inactive but DON'T reset currentTrackId
            // This allows the mission card to remain visible and user can resume later
            val values = ContentValues()
            values.put(TrackContentProvider.Schema.COL_ACTIVE, TrackContentProvider.Schema.VAL_TRACK_INACTIVE)
            contentResolver.update(
                TrackContentProvider.CONTENT_URI_TRACK, 
                values, 
                TrackContentProvider.Schema.COL_ID + " = ?", 
                arrayOf(currentTrackId.toString())
            )
        }
    }

    private fun startPeriodicRefresh() {
        stopPeriodicRefresh()
        
        refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
        refreshRunnable = object : Runnable {
            override fun run() {
                val activeTrackId = DataHelper.getActiveTrackId(contentResolver, "driver")
                if (activeTrackId != TRACK_ID_NO_TRACK && activeTrackId == currentTrackId) {
                    updateTrackCounts()
                }
                refreshHandler?.postDelayed(this, REFRESH_INTERVAL_MS)
            }
        }
        refreshHandler?.post(refreshRunnable!!)
    }

    private fun stopPeriodicRefresh() {
        refreshRunnable?.let { refreshHandler?.removeCallbacks(it) }
        refreshHandler = null
        refreshRunnable = null
    }

    private fun isTrackActive(trackId: Long): Boolean {
        // Use a direct database query to check if track is active
        val dbHelper = Ir.co.tfs.farazaman.data.db.DatabaseHelper(this)
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
                val isActive = it.getInt(it.getColumnIndexOrThrow(TrackContentProvider.Schema.COL_ACTIVE)) == TrackContentProvider.Schema.VAL_TRACK_ACTIVE
                return isActive
            }
        }

        return false
    }

    private fun showOptionsMenu() {
        if (currentTrackId == TRACK_ID_NO_TRACK) return
        
        val popupMenu = PopupMenu(this, missionOptions)
        popupMenu.menuInflater.inflate(R.menu.trackmgr_contextmenu, popupMenu.menu)
        
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.trackmgr_contextmenu_display -> {
                    displayTrackOnMap()
                    true
                }
                R.id.trackmgr_contextmenu_delete -> {
                    confirmDeleteTrack()
                    true
                }
                R.id.trackmgr_contextmenu_export_gpx -> {
                    exportTrackToGpx()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun displayTrackOnMap() {
        if (currentTrackId == TRACK_ID_NO_TRACK) return
        
        val intent = Intent(this, DisplayTrackMap::class.java)
        intent.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
        intent.putExtra("playback_mode", false) // Live mode
        intent.putExtra("disable_gps_service", true) // Prevent GPS service from starting
        intent.putExtra("from_driver", true) // Flag to indicate we came from DriverActivity
        startActivity(intent)
    }

    private fun confirmDeleteTrack() {
        if (currentTrackId == TRACK_ID_NO_TRACK) return
        
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.trackmgr_contextmenu_delete)
            .setMessage(resources.getString(R.string.trackmgr_delete_confirm))
            .setCancelable(true)
            .setPositiveButton("بله") { dialog, _ ->
                deleteCurrentTrack()
                dialog.dismiss()
            }
            .setNegativeButton("خیر") { dialog, _ -> dialog.cancel() }
            .show()
    }

    private fun deleteCurrentTrack() {
        if (currentTrackId == TRACK_ID_NO_TRACK) return
        
        // Stop tracking if active
        stopActiveTrack()
        
        // Delete track from database
        contentResolver.delete(
            ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, currentTrackId),
            null,
            null
        )
        
        // Delete track files
        val trackStorageDirectory = DataHelper.getTrackDirectory(currentTrackId, this)
        if (trackStorageDirectory.exists()) {
            FileSystemUtils.delete(trackStorageDirectory, true)
        }
        
        // Clear zone info
        clearZoneInfoFromPreferences()
        
        // Reset current track ID and zone info
        currentTrackId = TRACK_ID_NO_TRACK
        currentZoneWorkVehicleId = -1
        currentZoneInfoSummary = ""
        
        // Update UI
        updateUI()
        
        Toast.makeText(this, "ماموریت حذف شد", Toast.LENGTH_SHORT).show()
    }

    private fun exportTrackToGpx() {
        if (currentTrackId == TRACK_ID_NO_TRACK) return
        
        try {
            // Create GPX file
            val gpxString = try {
                val file = File.createTempFile("driver_track_" + currentTrackId, ".gpx", cacheDir)
                val writer = FileWriter(file)
                Ir.co.tfs.farazaman.util.GpxWriter(writer).write(this, currentTrackId)
                writer.flush()
                writer.close()
                file.readText()
            } catch (e: Exception) {
                Log.e(TAG, "Error creating GPX file", e)
                "<gpx><trk><name>Track $currentTrackId</name></trk></gpx>"
            }
            
            // Save to external storage
            val dir = File(android.os.Environment.getExternalStorageDirectory(), "faraZaman")
            if (!dir.exists()) dir.mkdirs()
            
            val gpxFile = File(dir, "driver_track_${currentTrackId}.gpx")
            FileWriter(gpxFile).use { it.write(gpxString) }
            
            if (gpxFile.exists() && gpxFile.length() > 0) {
                Toast.makeText(
                    this,
                    "فایل GPX با موفقیت ذخیره شد:\n${gpxFile.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this, "خطا: فایل GPX ذخیره نشد.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting GPX", e)
            Toast.makeText(this, "خطا در ذخیره GPX: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    if (pendingTrackId != TRACK_ID_NO_TRACK) {
                        startGPSTracking(pendingTrackId)
                        pendingTrackId = TRACK_ID_NO_TRACK
                    } else {
                        createAndStartMission()
                    }
                } else {
                    Toast.makeText(this, "دسترسی موقعیت مکانی برای شروع ردیابی GPS لازم است.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private inner class UploadTrackTask(val context: Context, val trackId: Long) : AsyncTask<Void, Void, Boolean>() {
        private var errorMessage: String = "خطای نامشخص"
        
        override fun onPreExecute() {
            super.onPreExecute()
            uploadProgressBar.visibility = View.VISIBLE
        }
        
        override fun doInBackground(vararg params: Void?): Boolean {
            Log.i(TAG, "=== Starting upload process for Driver track: $trackId ===")
            
            // Step 1: Create GPX file with custom name
            val gpxFile: File = try {
                Log.d(TAG, "Creating GPX file for Driver track $trackId...")
                val fileName = generateDriverFileName(trackId)
                val file = File(context.cacheDir, "$fileName.gpx")
                
                // Create the file if it doesn't exist
                if (!file.exists()) {
                    file.createNewFile()
                    Log.d(TAG, "GPX file created: ${file.absolutePath}")
                }
                
                val writer = FileWriter(file)
                Ir.co.tfs.farazaman.util.GpxWriter(writer).write(context, trackId)
                writer.flush()
                writer.close()
                Log.d(TAG, "GPX file created successfully: ${file.absolutePath}, Size: ${file.length()} bytes")
                file
            } catch (e: IOException) {
                Log.e(TAG, "Failed to create GPX file for Driver track $trackId", e)
                Log.e(TAG, "IOException details: ${e.message}")
                errorMessage = "خطا در ساخت فایل GPX: ${e.message}"
                return false
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error creating GPX file for Driver track $trackId", e)
                errorMessage = "خطا در ساخت فایل GPX: ${e.message}"
                return false
            }
            
            if (!gpxFile.exists()) {
                Log.e(TAG, "GPX file does not exist after creation: ${gpxFile.absolutePath}")
                errorMessage = "فایل GPX پس از ساخت یافت نشد."
                return false
            }
            
            // Step 2: Create ZIP file
            Log.d(TAG, "Creating ZIP file from GPX and multimedia files...")
            val zipFile = ZipHelper.zipCacheFiles(context, trackId, gpxFile)
            if (zipFile == null) {
                Log.e(TAG, "ZipHelper.zipCacheFiles returned null for Driver track $trackId")
                errorMessage = "خطا در ساخت فایل فشرده."
                return false
            }
            if (!zipFile.exists()) {
                Log.e(TAG, "ZIP file does not exist after creation: ${zipFile.absolutePath}")
                errorMessage = "فایل فشرده پس از ساخت یافت نشد."
                return false
            }
            Log.d(TAG, "ZIP file created successfully: ${zipFile.absolutePath}, Size: ${zipFile.length()} bytes")
            
            // Step 3: Prepare HTTP client and request
            // Use injected OkHttpClient which has AuthInterceptor for automatic token refresh
            Log.d(TAG, "Preparing HTTP client and request...")
            Log.d(TAG, "Using OkHttpClient with AuthInterceptor - it will handle token refresh automatically")
            
            val fileBody = RequestBody.create("application/zip".toMediaTypeOrNull(), zipFile)
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", zipFile.name, fileBody)
                .build()
            
            val uploadUrl = buildUrl("/api/GisGeolocation/upload")
            Log.i(TAG, "Upload URL: $uploadUrl")
            Log.d(TAG, "ZIP filename: ${zipFile.name}")
            Log.d(TAG, "Request body size: ${requestBody.contentLength()} bytes")
            
            // AuthInterceptor will automatically add Authorization header with current/refreshed token
            val request = Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .build()
            
            // Step 5: Execute request
            return try {
                Log.i(TAG, "Executing upload request for Driver track $trackId...")
                val response = okHttpClient.newCall(request).execute()
                val responseCode = response.code
                val responseMessage = response.message
                
                Log.d(TAG, "Response received - Code: $responseCode, Message: $responseMessage")
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.i(TAG, "Upload successful for Driver track $trackId")
                    Log.d(TAG, "Response body: $responseBody")
                    true
                } else {
                    val responseBody = response.body?.string()
                    Log.e(TAG, "Upload failed for Driver track $trackId")
                    Log.e(TAG, "Response code: $responseCode")
                    Log.e(TAG, "Response message: $responseMessage")
                    Log.e(TAG, "Response body: ${responseBody ?: "No response body"}")
                    Log.e(TAG, "Response headers: ${response.headers}")
                    
                    // Parse error message
                    errorMessage = try {
                        if (responseCode == 400 && responseBody != null) {
                            val jsonError = org.json.JSONObject(responseBody)
                            Log.d(TAG, "Parsing 400 error JSON: $jsonError")
                            jsonError.optString("message", "")
                                .takeIf { it.isNotEmpty() }
                                ?: jsonError.optString("error", "")
                                .takeIf { it.isNotEmpty() }
                                ?: jsonError.optString("errorMessage", "")
                                .takeIf { it.isNotEmpty() }
                                ?: "خطای 400: درخواست نامعتبر"
                        } else if (responseCode == 401) {
                            Log.e(TAG, "401 Unauthorized - Token may be invalid or expired")
                            "خطای احراز هویت (401): لطفاً دوباره وارد شوید"
                        } else if (responseCode == 403) {
                            Log.e(TAG, "403 Forbidden - Access denied")
                            "خطای دسترسی (403): شما مجوز انجام این عملیات را ندارید"
                        } else if (responseCode == 413) {
                            Log.e(TAG, "413 Payload Too Large - File size too big")
                            "خطا (413): حجم فایل بیش از حد مجاز است"
                        } else if (responseCode == 500) {
                            Log.e(TAG, "500 Internal Server Error")
                            "خطای سرور (500): خطای داخلی سرور"
                        } else {
                            "خطای سرور: $responseCode $responseMessage"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing response body", e)
                        "خطای سرور: $responseCode $responseMessage"
                    }
                    false
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error during upload for Driver track $trackId", e)
                Log.e(TAG, "IOException type: ${e.javaClass.simpleName}")
                Log.e(TAG, "IOException message: ${e.message}")
                Log.e(TAG, "IOException cause: ${e.cause}")
                errorMessage = "خطای شبکه: ${e.message}"
                false
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during upload for Driver track $trackId", e)
                Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Exception message: ${e.message}")
                errorMessage = "خطای غیرمنتظره: ${e.message}"
                false
            }
        }
        
        override fun onPostExecute(success: Boolean) {
            super.onPostExecute(success)
            uploadProgressBar.visibility = View.GONE
            loadingDialog?.dismiss()
            loadingDialog = null
            
            if (success) {
                Toast.makeText(context, "ماموریت با موفقیت آپلود شد.", Toast.LENGTH_SHORT).show()
                // Delete the track
                contentResolver.delete(ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId), null, null)
                
                // Clear zone info
                clearZoneInfoFromPreferences()
                
                currentTrackId = TRACK_ID_NO_TRACK
                currentZoneWorkVehicleId = -1
                currentZoneInfoSummary = ""
                updateUI()
            } else {
                Toast.makeText(context, "آپلود ناموفق بود: $errorMessage", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Get the current zone work vehicle ID for GPX metadata
     */
    fun getCurrentZoneWorkVehicleId(): Int {
        return currentZoneWorkVehicleId
    }
}
