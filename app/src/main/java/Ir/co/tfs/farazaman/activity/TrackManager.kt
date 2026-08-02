package Ir.co.tfs.farazaman.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import Ir.co.tfs.farazaman.OSMTracker
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.data.db.DataHelper
import Ir.co.tfs.farazaman.data.db.TrackContentProvider
import Ir.co.tfs.farazaman.data.db.model.Track
import Ir.co.tfs.farazaman.service.gps.GPSLogger
import Ir.co.tfs.farazaman.exception.CreateTrackException
import Ir.co.tfs.farazaman.gpx.ZipHelper
import Ir.co.tfs.farazaman.util.FileSystemUtils
import Ir.co.tfs.farazaman.util.GpxWriter
import Ir.co.tfs.farazaman.AppConstants
import Ir.co.tfs.farazaman.activity.DisplayTrackMap.Companion.EXTRA_LATITUDE
import Ir.co.tfs.farazaman.activity.DisplayTrackMap.Companion.EXTRA_LONGITUDE
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import saman.zamani.persiandate.PersianDate
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Date
import Ir.co.tfs.farazaman.activity.TrackListRecyclerViewAdapterListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import Ir.co.tfs.farazaman.service.remote.RoadService
import Ir.co.tfs.farazaman.util.LoadingDialog
import Ir.co.tfs.farazaman.util.TokenManager
import Ir.co.tfs.farazaman.util.UserManager
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class TrackManager : AppCompatActivity(), TrackListRecyclerViewAdapterListener {
    companion object {
        private val TAG = TrackManager::class.java.simpleName
        private const val TRACK_ID_NO_TRACK = -1L
        private const val REFRESH_INTERVAL_MS = 3000L // Refresh every 3 seconds when tracking
        private const val LOCATION_PERMISSION_REQUEST_CODE = 6
    }
    private val RC_WRITE_PERMISSIONS_UPLOAD = 4
    private val RC_WRITE_PERMISSIONS_DISPLAY_TRACK = 3

    private var currentTrackId: Long = TRACK_ID_NO_TRACK
    private var contextMenuSelectedTrackid: Long = TRACK_ID_NO_TRACK
    private var permissionRequestTrackId: Long = TRACK_ID_NO_TRACK
    private var pendingTrackId: Long = TRACK_ID_NO_TRACK

    private lateinit var recyclerView: RecyclerView
    private lateinit var recyclerViewAdapter: TrackListRVAdapter
    private lateinit var uploadProgressBar: ProgressBar
    
    private var refreshHandler: android.os.Handler? = null
    private var refreshRunnable: Runnable? = null
    
    // Loading dialog
    private var loadingDialog: LoadingDialog? = null

    @Inject
    lateinit var roadService: RoadService

    @Inject
    lateinit var tokenManager: TokenManager
    
    @Inject
    lateinit var okHttpClient: OkHttpClient
    
    @Inject
    lateinit var userManager: UserManager

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
     * Generate filename for supervisor track upload
     * Format: {قرارداد}_{کارفرما}_{تاریخ شمسی}_{userName}_ناظر
     */
    private fun generateSupervisorFileName(trackId: Long): String {
        val userName = userManager.getUserName() ?: "unknown"
        val persianDate = DataHelper.PERSIAN_FILENAME_FORMATTER.format(PersianDate())
        
        // Get contract and organ (contractor) info from SharedPreferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val missionDataKey = "mission_data_$trackId"
        val missionDataJson = prefs.getString(missionDataKey, null)
        
        var contractTitle = "قرارداد"
        var organTitle = "کارفرما"
        
        if (missionDataJson != null) {
            try {
                val missionData = org.json.JSONObject(missionDataJson)
                contractTitle = missionData.optString("contractTitle", "قرارداد")
                organTitle = missionData.optString("organTitle", "کارفرما")
                
                // Clean up titles for filename (remove special characters)
                contractTitle = contractTitle.replace("[^\\u0600-\\u06FF\\w\\s]".toRegex(), "")
                organTitle = organTitle.replace("[^\\u0600-\\u06FF\\w\\s]".toRegex(), "")
                
                Log.d(TAG, "Mission data found for track $trackId: contract=$contractTitle, organ=$organTitle")
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing mission data for track $trackId", e)
            }
        } else {
            Log.w(TAG, "No mission data found for track $trackId")
        }
        
        // Clean the filename by replacing invalid file system characters
        val fileName = "${contractTitle}_${organTitle}_${persianDate}_${userName}_ناظر"
        return fileName.replace("[/\\\\:*?\"<>|]".toRegex(), "_")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, NewSupervisorActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
        )
        finish()
    }

    @Suppress("unused")
    private fun legacyOnCreateBody(savedInstanceState: Bundle?) {
        setContentView(R.layout.trackmanager)
        val myToolbar: Toolbar = findViewById(R.id.my_toolbar)
        setSupportActionBar(myToolbar)
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(false)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.bell)
        }
        // Disable start_track button - missions are now created through NewMissionBottomSheetFragment
        // findViewById<View>(R.id.start_track).setOnClickListener { startTrackLoggerForNewTrack() }
        findViewById<View>(R.id.start_track).isEnabled = false
        findViewById<View>(R.id.start_track).alpha = 0.5f
        recyclerView = findViewById(R.id.recyclerview)
        uploadProgressBar = findViewById(R.id.upload_progressbar)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Add this for get_new_mission
        findViewById<View>(R.id.get_new_mission).setOnClickListener {
            val bottomSheet = NewMissionBottomSheetFragment()
            bottomSheet.show(supportFragmentManager, "NewMissionBottomSheet")
        }
    }

    override fun onResume() {
        super.onResume()
        setRecyclerView()
        checkEmptyViewVisibility()
        if (recyclerViewAdapter.itemCount > 0) {
            currentTrackId = DataHelper.getActiveTrackId(contentResolver, "supervisor")
            if (currentTrackId != TRACK_ID_NO_TRACK) {
                Snackbar.make(findViewById(R.id.start_track),
                    resources.getString(R.string.trackmgr_continuetrack_hint).replace("{0}", currentTrackId.toString()),
                    Snackbar.LENGTH_LONG
                ).setAction("Action", null).show()
            }
        }
        startPeriodicRefresh()
    }
    
    override fun onPause() {
        super.onPause()
        stopPeriodicRefresh()
    }

    private fun setRecyclerView() {
        val cursor = contentResolver.query(
            TrackContentProvider.CONTENT_URI_TRACK, null, null, null,
            TrackContentProvider.Schema.COL_START_DATE + " desc"
        )
        recyclerViewAdapter = TrackListRVAdapter(this, cursor, this)
        recyclerView.adapter = recyclerViewAdapter
        val itemTouchHelper = ItemTouchHelper(SwipeToDeleteCallback(this, recyclerViewAdapter))
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
    
    private fun startPeriodicRefresh() {
        stopPeriodicRefresh() // Stop any existing refresh
        
        refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
        refreshRunnable = object : Runnable {
            override fun run() {
                // Check if there's an active track for supervisor role
                val activeTrackId = DataHelper.getActiveTrackId(contentResolver, "supervisor")
                if (activeTrackId != TRACK_ID_NO_TRACK) {
                    // Refresh the adapter to show updated track point counts
                    updateTrackItemsInRecyclerView()
                }
                // Schedule next refresh
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.trackmgr_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Settings menu item is always visible in toolbar
        menu.findItem(R.id.trackmgr_menu_settings).isVisible = true
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                Toast.makeText(this, "اعلان ها در نسخه بعد اضافه میشوند.", Toast.LENGTH_SHORT).show()
                return true
            }
            R.id.trackmgr_menu_settings -> {
                startActivity(Intent(this, Preferences::class.java))
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onShowPopupMenu(popupMenu: PopupMenu, trackId: Long) {
        contextMenuSelectedTrackid = trackId
        popupMenu.menuInflater.inflate(R.menu.trackmgr_contextmenu, popupMenu.menu)
        if (currentTrackId == contextMenuSelectedTrackid) {
            popupMenu.menu.removeItem(R.id.trackmgr_contextmenu_delete)
        }
        popupMenu.setOnMenuItemClickListener { onContextItemSelected(it) }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.trackmgr_contextmenu_delete -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.trackmgr_contextmenu_delete)
                    .setMessage(resources.getString(R.string.trackmgr_delete_confirm).replace("{0}", contextMenuSelectedTrackid.toString()))
                    .setCancelable(true)
                    .setPositiveButton("بله") { dialog, which -> deleteTrack(contextMenuSelectedTrackid) }
                    .setNegativeButton("خیر") { dialog, which -> dialog.cancel() }
                    .show()
            }
            R.id.trackmgr_contextmenu_display -> {
                // Show map with live location without starting GPS tracking
                displayTrackLiveOnly(contextMenuSelectedTrackid)
            }
            R.id.trackmgr_contextmenu_export_gpx -> {
                exportTrackToGpx(contextMenuSelectedTrackid)
            }
        }
        return super.onContextItemSelected(item)
    }

    private fun tryStartTrackLogger(intent: Intent) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startActivity(intent)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 5)
        }
    }

    private fun startTrackLoggerForNewTrack() {
        try {
            currentTrackId = createNewTrack()
            val startTrackingIntent = Intent(OSMTracker.INTENT_START_TRACKING)
            startTrackingIntent.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
            sendBroadcast(startTrackingIntent)
            val i = Intent(this, DisplayTrackMap::class.java)
            i.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
            tryStartTrackLogger(i)
        } catch (cte: CreateTrackException) {
            Toast.makeText(this, resources.getString(R.string.trackmgr_newtrack_error).replace("{0}", cte.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun displayTrack(trackId: Long, playbackMode: Boolean) {
        val i = Intent(this, DisplayTrackMap::class.java)
        i.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, trackId)
        i.putExtra("playback_mode", playbackMode)
        startActivity(i)
    }

    private fun displayTrack(trackId: Long) {
        displayTrack(trackId, true)
    }

    private fun writeExternalStoragePermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            true
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onClick(trackId: Long) {
        // Always show map in live mode without starting GPS service
        displayTrackLiveOnly(trackId)
    }
    
    private fun displayTrackLiveOnly(trackId: Long) {
        val i = Intent(this, DisplayTrackMap::class.java)
        i.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, trackId)
        i.putExtra("playback_mode", false) // Live mode
        i.putExtra("disable_gps_service", true) // Prevent GPS service from starting
        startActivity(i)
    }

    override fun deleteTrackItem(trackId: Long) {
        deleteTrack(trackId)
    }

    override fun stopTrack(trackId: Long, stopOrResume: Boolean) {
        Log.d(TAG, "stopTrack called - trackId: $trackId, stopOrResume: $stopOrResume")
        
        if (stopOrResume) {
            Log.d(TAG, "Stopping active track")
            stopActiveTrack()
        } else {
            Log.d(TAG, "Starting GPS tracking for trackId: $trackId")
            // Check for required permissions before starting GPS service
            if (checkLocationPermissions()) {
                Log.d(TAG, "All permissions granted, starting GPS tracking")
                startGPSTracking(trackId)
            } else {
                Log.d(TAG, "Permissions not granted, requesting permissions")
                requestLocationPermissions(trackId)
            }
        }
    }

    private fun checkLocationPermissions(): Boolean {
        val hasFineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasForegroundService = ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED
        
        Log.d(TAG, "Permission check - Fine Location: $hasFineLocation, Coarse Location: $hasCoarseLocation, Foreground Service: $hasForegroundService")
        
        // For now, only check the essential permissions
        return hasFineLocation && hasCoarseLocation && hasForegroundService
    }

    private fun requestLocationPermissions(trackId: Long) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE
        )
        
        Log.d(TAG, "Requesting permissions: ${permissions.joinToString(", ")}")
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), LOCATION_PERMISSION_REQUEST_CODE)
        // Store trackId to start tracking after permission is granted
        pendingTrackId = trackId
    }

    private fun startGPSTracking(trackId: Long) {
        if (currentTrackId != trackId) {
            setActiveTrack(trackId)
        }
        
        // Start the GPS service properly
        val startTrackingIntent = Intent(this, GPSLogger::class.java).apply {
            action = OSMTracker.INTENT_START_TRACKING
            putExtra(TrackContentProvider.Schema.COL_TRACK_ID, trackId)
        }
        startService(startTrackingIntent)
        
        // Refresh the adapter to update button states and colors after starting tracking
        updateTrackItemsInRecyclerView()
        
        Toast.makeText(this, "ردیابی GPS برای ماموریت #$trackId در پس‌زمینه شروع شد", Toast.LENGTH_SHORT).show()
    }

    private fun checkEmptyViewVisibility() {
        val emptyView = findViewById<LinearLayout>(R.id.trackmgr_empty)
        if (!::recyclerViewAdapter.isInitialized || recyclerViewAdapter.itemCount == 0) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }

    override fun endMission(trackId: Long) {
        MaterialAlertDialogBuilder(this)
            .setTitle("اتمام و آپلود ماموریت")
            .setMessage("با آپلود، این ماموریت از لیست شما حذف خواهد شد. آیا ادامه می‌دهید؟")
            .setCancelable(true)
            .setPositiveButton("تایید و آپلود") { dialog, which ->
                startUploadProcess(trackId)
            }
            .setNegativeButton("لغو") { dialog, which -> dialog.cancel() }
            .show()
    }

    override fun formActivity(trackId: Long) {
        // Get the last location of the track
        val lastLocation = getLastLocationOfTrack(trackId)
        
        // Get mission data from SharedPreferences
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val missionDataKey = "mission_data_$trackId"
        val missionDataJson = prefs.getString(missionDataKey, null)
        
        val intent = Intent(this, SubmitViolationFormActivity::class.java).apply {
            putExtra(TrackContentProvider.Schema.COL_TRACK_ID, trackId)
            if (lastLocation != null) {
                putExtra(DisplayTrackMap.EXTRA_LATITUDE, lastLocation.latitude)
                putExtra(DisplayTrackMap.EXTRA_LONGITUDE, lastLocation.longitude)
            }
            if (missionDataJson != null) {
                putExtra("mission_data_json", missionDataJson)
            }
        }
        Log.d(TAG, "Starting SubmitViolationFormActivity (no address fetch)")
        startActivity(intent)
    }

    override fun formActivityWithMissionData(trackId: Long, organId: Int, contractId: Int, organTitle: String, contractTitle: String, visitDate: String, itemData: String?) {
        Log.d(TAG, "=== formActivityWithMissionData called ===")
        Log.d(TAG, "trackId: $trackId")
        Log.d(TAG, "organId: $organId, organTitle: $organTitle")
        Log.d(TAG, "contractId: $contractId, contractTitle: $contractTitle")
        Log.d(TAG, "visitDate: $visitDate")
        Log.d(TAG, "itemData: $itemData")
        
        // Get the last location of the track
        val lastLocation = getLastLocationOfTrack(trackId)
        
        val intent = Intent(this, SubmitViolationFormActivity::class.java).apply {
            putExtra(TrackContentProvider.Schema.COL_TRACK_ID, trackId)
            putExtra("organ_id", organId)
            putExtra("contract_id", contractId)
            putExtra("organ_title", organTitle)
            putExtra("contract_title", contractTitle)
            putExtra("visit_date", visitDate)
            putExtra("item_data", itemData)
            // Mark this as coming from icon_violation
            putExtra("from_icon_violation", true)
            if (lastLocation != null) {
                putExtra(DisplayTrackMap.EXTRA_LATITUDE, lastLocation.latitude)
                putExtra(DisplayTrackMap.EXTRA_LONGITUDE, lastLocation.longitude)
            }
        }
        Log.d(TAG, "Starting SubmitViolationFormActivity with mission data (no address fetch)")
        startActivity(intent)
    }

    private fun getLastLocationOfTrack(trackId: Long): android.location.Location? {
        // Query the last track point for this track
        val cursor = contentResolver.query(
            TrackContentProvider.trackPointsUri(trackId),
            arrayOf(
                TrackContentProvider.Schema.COL_LATITUDE,
                TrackContentProvider.Schema.COL_LONGITUDE,
                TrackContentProvider.Schema.COL_TIMESTAMP
            ),
            null,
            null,
            "${TrackContentProvider.Schema.COL_TIMESTAMP} DESC" // Get the most recent point
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                val latitude = it.getDouble(it.getColumnIndex(TrackContentProvider.Schema.COL_LATITUDE))
                val longitude = it.getDouble(it.getColumnIndex(TrackContentProvider.Schema.COL_LONGITUDE))
                
                // Create a Location object
                val location = android.location.Location("track_last_location")
                location.latitude = latitude
                location.longitude = longitude
                
                return location
            }
        }
        
        return null
    }


    private fun startUploadProcess(trackId: Long) {
        if (this.currentTrackId == trackId) {
            val intent = Intent(OSMTracker.INTENT_STOP_TRACKING)
            intent.setPackage(this.packageName)
            sendBroadcast(intent)
            this.currentTrackId = TRACK_ID_NO_TRACK
        }
        val dataHelper = DataHelper(this)
        dataHelper.stopTracking(trackId)
        updateTrackItemsInRecyclerView()
        
        // Show loading dialog
        loadingDialog = LoadingDialog.show(this, "در حال آپلود...")
        UploadTrackTask(this, trackId).execute()
    }

    private fun createNewTrack(): Long {
        val values = ContentValues()
        values.put(TrackContentProvider.Schema.COL_NAME, DataHelper.PERSIAN_FILENAME_FORMATTER.format(PersianDate()))
        values.put(TrackContentProvider.Schema.COL_START_DATE, Date().time)
        values.put(TrackContentProvider.Schema.COL_ACTIVE, TrackContentProvider.Schema.VAL_TRACK_ACTIVE)
        values.put(TrackContentProvider.Schema.COL_ROLE, "supervisor") // Set role as supervisor
        val trackUri = contentResolver.insert(TrackContentProvider.CONTENT_URI_TRACK, values)
        val trackId = ContentUris.parseId(trackUri!!)
        setActiveTrack(trackId)
        return trackId
    }

    private fun deleteTrack(id: Long) {
        // If the track to be deleted is currently being tracked, stop tracking first
        if (id == currentTrackId) {
            stopActiveTrack()
        }
        contentResolver.delete(ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, id), null, null)
        updateTrackItemsInRecyclerView()
        checkEmptyViewVisibility()
        val trackStorageDirectory = DataHelper.getTrackDirectory(id, this)
        if (trackStorageDirectory.exists()) {
            FileSystemUtils.delete(trackStorageDirectory, true)
        }
    }

    private fun updateTrackItemsInRecyclerView() {
        if (::recyclerViewAdapter.isInitialized && recyclerViewAdapter.cursorAdapter != null) {
            val newCursor = contentResolver.query(
                TrackContentProvider.CONTENT_URI_TRACK, null, null, null,
                TrackContentProvider.Schema.COL_START_DATE + " desc"
            )
            recyclerViewAdapter.cursorAdapter?.swapCursor(newCursor)
            recyclerViewAdapter.notifyDataSetChanged()
        }
    }

    private fun deleteAllTracks() {
        if (currentTrackId != TRACK_ID_NO_TRACK) {
            stopActiveTrack()
        }
        val cursor = contentResolver.query(TrackContentProvider.CONTENT_URI_TRACK, null, null, null, null)
        cursor?.use {
            val idCol = cursor.getColumnIndex(TrackContentProvider.Schema.COL_ID)
            while (cursor.moveToNext()) {
                deleteTrack(cursor.getLong(idCol))
            }
        }
    }

    private fun setActiveTrack(trackId: Long) {
        stopActiveTrack()
        val values = ContentValues()
        values.put(TrackContentProvider.Schema.COL_ACTIVE, TrackContentProvider.Schema.VAL_TRACK_ACTIVE)
        contentResolver.update(TrackContentProvider.CONTENT_URI_TRACK, values, TrackContentProvider.Schema.COL_ID + " = ?", arrayOf(trackId.toString()))
        currentTrackId = trackId
    }

    private fun stopActiveTrack() {
        if (currentTrackId != TRACK_ID_NO_TRACK) {
            val intent = Intent(OSMTracker.INTENT_STOP_TRACKING)
            intent.setPackage(this.packageName)
            sendBroadcast(intent)
            val dataHelper = DataHelper(this)
            dataHelper.stopTracking(currentTrackId)
            currentTrackId = TRACK_ID_NO_TRACK
            updateTrackItemsInRecyclerView()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                Log.d(TAG, "Location permission result - grantResults: ${grantResults.joinToString(", ")}")
                Log.d(TAG, "All permissions granted: ${grantResults.all { it == PackageManager.PERMISSION_GRANTED }}")
                
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // All location permissions granted, start GPS tracking
                    Log.d(TAG, "All location permissions granted, starting GPS tracking for trackId: $pendingTrackId")
                    if (pendingTrackId != TRACK_ID_NO_TRACK) {
                        startGPSTracking(pendingTrackId)
                        pendingTrackId = TRACK_ID_NO_TRACK
                    } else {
                        Log.w(TAG, "pendingTrackId is TRACK_ID_NO_TRACK, cannot start GPS tracking")
                    }
                } else {
                    Log.w(TAG, "Some location permissions were denied")
                    Toast.makeText(this, "دسترسی موقعیت مکانی برای شروع ردیابی GPS لازم است.", Toast.LENGTH_LONG).show()
                }
            }
            RC_WRITE_PERMISSIONS_UPLOAD -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startUploadProcess(permissionRequestTrackId)
                } else {
                    Toast.makeText(this, "دسترسی برای انجام عملیات لازم است.", Toast.LENGTH_LONG).show()
                }
            }
            RC_WRITE_PERMISSIONS_DISPLAY_TRACK -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onClick(permissionRequestTrackId)
                } else {
                    Toast.makeText(this, "دسترسی برای انجام عملیات لازم است.", Toast.LENGTH_LONG).show()
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
            Log.i(TAG, "=== Starting upload process for Supervisor track: $trackId ===")
            
            // Step 1: Create GPX file with custom name
            val gpxFile: File = try {
                Log.d(TAG, "Creating GPX file for track $trackId...")
                val fileName = generateSupervisorFileName(trackId)
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
                Log.e(TAG, "Failed to create GPX file for track $trackId", e)
                Log.e(TAG, "IOException details: ${e.message}")
                errorMessage = "خطا در ساخت فایل GPX: ${e.message}"
                return false
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error creating GPX file for track $trackId", e)
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
                Log.e(TAG, "ZipHelper.zipCacheFiles returned null for track $trackId")
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
                Log.i(TAG, "Executing upload request for Supervisor track $trackId...")
                val response = okHttpClient.newCall(request).execute()
                val responseCode = response.code
                val responseMessage = response.message
                
                Log.d(TAG, "Response received - Code: $responseCode, Message: $responseMessage")
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.i(TAG, "Upload successful for Supervisor track $trackId")
                    Log.d(TAG, "Response body: $responseBody")
                    true
                } else {
                    val responseBody = response.body?.string()
                    Log.e(TAG, "Upload failed for Supervisor track $trackId")
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
                Log.e(TAG, "Network error during upload for Supervisor track $trackId", e)
                Log.e(TAG, "IOException type: ${e.javaClass.simpleName}")
                Log.e(TAG, "IOException message: ${e.message}")
                Log.e(TAG, "IOException cause: ${e.cause}")
                errorMessage = "خطای شبکه: ${e.message}"
                false
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during upload for Supervisor track $trackId", e)
                Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Exception message: ${e.message}")
                errorMessage = "خطای غیرمنتظره: ${e.message}"
                false
            }
        }
        override fun onPostExecute(success: Boolean) {
            super.onPostExecute(success)
            uploadProgressBar.visibility = View.GONE
            // Dismiss loading dialog
            loadingDialog?.dismiss()
            loadingDialog = null
            
            if (success) {
                Toast.makeText(context, "ماموریت با موفقیت آپلود شد.", Toast.LENGTH_SHORT).show()
                deleteTrack(trackId)
            } else {
                Toast.makeText(context, "آپلود ناموفق بود: $errorMessage", Toast.LENGTH_LONG).show()
                updateTrackItemsInRecyclerView()
            }
        }
    }

    private fun exportTrackToGpx(trackId: Long) {
        // Request permission if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 2001)
            permissionRequestTrackId = trackId
            return
        }
        try {
            // Use your actual GPX writer here if available
            val gpxString = try {
                val file = File.createTempFile("track_" + trackId, ".gpx", cacheDir)
                val writer = FileWriter(file)
                Ir.co.tfs.farazaman.util.GpxWriter(writer).write(this, trackId)
                writer.flush()
                writer.close()
                file.readText()
            } catch (e: Exception) {
                // Fallback to placeholder if GpxWriter fails
                "<gpx><trk><name>Track $trackId</name></trk></gpx>"
            }
            val dir = File(android.os.Environment.getExternalStorageDirectory(), "faraZaman")
            if (!dir.exists()) dir.mkdirs()
            val gpxFile = File(dir, "track_${trackId}.gpx")
            FileWriter(gpxFile).use { it.write(gpxString) }
            if (gpxFile.exists() && gpxFile.length() > 0) {
                Toast.makeText(this, "فایل GPX با موفقیت ذخیره شد:\n${gpxFile.absolutePath}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "خطا: فایل GPX ذخیره نشد.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در ذخیره GPX: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun refreshTrackList() {
        setRecyclerView()
        checkEmptyViewVisibility()
    }

    private class SwipeToDeleteCallback(val context: Context, val adapter: TrackListRVAdapter) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
        private val deleteIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_delete)
        private val background: Drawable? = ContextCompat.getDrawable(context, R.drawable.delete_background)
        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.adapterPosition
            adapter.deleteTrack(position) { adapter.notifyItemChanged(position) }
        }
        override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            val itemView = viewHolder.itemView
            if (deleteIcon != null && background != null) {
                val iconMargin = (itemView.height - deleteIcon.intrinsicHeight) / 2
                val iconTop = itemView.top + iconMargin
                val iconBottom = iconTop + deleteIcon.intrinsicHeight
                if (dX > 0) {
                    val iconLeft = itemView.left + iconMargin
                    val iconRight = iconLeft + deleteIcon.intrinsicWidth
                    deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                } else {
                    background.setBounds(0, 0, 0, 0)
                }
                background.draw(c)
                deleteIcon.draw(c)
            }
        }
    }
} 
