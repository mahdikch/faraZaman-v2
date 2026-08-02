package Ir.co.tfs.farazaman.activity

import android.content.ContentUris
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.database.ContentObserver
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import Ir.co.tfs.farazaman.OSMTracker
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.presentation.viewmodel.DisplayTrackMapViewModel
import Ir.co.tfs.farazaman.data.db.TrackContentProvider
import Ir.co.tfs.farazaman.layout.GpsStatusRecordDisplay
import Ir.co.tfs.farazaman.listener.SensorListener
import Ir.co.tfs.farazaman.overlay.WayPointsOverlay
import Ir.co.tfs.farazaman.service.gps.GPSLogger
import Ir.co.tfs.farazaman.service.gps.GPSLoggerServiceConnectionDisplay
import Ir.co.tfs.farazaman.service.remote.RoadService
import Ir.co.tfs.farazaman.util.GpsAccuracyHelper
import Ir.co.tfs.farazaman.service.remote.ApiService
import com.google.gson.Gson
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import javax.inject.Inject
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.decode.SvgDecoder
import android.graphics.drawable.Drawable
import java.io.File
import java.io.FileOutputStream
import android.content.ContentValues
import Ir.co.tfs.farazaman.data.model.GisLayerIndexResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull

@AndroidEntryPoint
class DisplayTrackMap : AppCompatActivity() {

    companion object {
        private const val TAG = "DisplayTrackMap"
        private const val KEY_PLAYBACK_MODE = "playback_mode"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_FROM_NEW_SUPERVISOR = "from_new_supervisor"
        private const val ROAD_INFO_UPDATE_INTERVAL_MS = 5000L
    }

    private lateinit var osmView: MapView
    private lateinit var osmViewController: IMapController
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private lateinit var polyline: Polyline
    private lateinit var wayPointsOverlay: WayPointsOverlay
    private lateinit var prefs: SharedPreferences
    private var currentTrackId: Long = 0
    private var zoomedToTrackAlready = false
    private var isPlaybackMode = false
    private var disableGpsService = false
    private lateinit var trackpointContentObserver: ContentObserver
    private var gpsLoggerServiceIntent: Intent? = null
    private var sensorListener: SensorListener? = null
    private var gpsLogger: GPSLogger? = null
    private var gpsLoggerConnection: ServiceConnection = GPSLoggerServiceConnectionDisplay(this)

    @Inject
    lateinit var roadService: RoadService
    @Inject
    lateinit var apiService: ApiService
    @Inject
    lateinit var formDataApiService: Ir.co.tfs.farazaman.data.api.FormDataApiService
    
    // ViewModel
    private val viewModel: DisplayTrackMapViewModel by viewModels()
    private lateinit var roadNameTextView: TextView
    private lateinit var layerItemsContainer: LinearLayout
    private lateinit var btnLayersToggle: ImageButton
    private val roadInfoUpdateHandler = Handler(Looper.getMainLooper())
    private lateinit var roadInfoRunnable: Runnable
    private var layersExpanded = false
    private var cachedLayers: List<Ir.co.tfs.farazaman.presentation.viewmodel.SpeedDialLayerItem> = emptyList()

    private var isFromDriver: Boolean = false
    private var isFromNewSupervisor: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        Configuration.getInstance().load(this, prefs)
        setContentView(R.layout.displaytrackmap)
        supportActionBar?.hide()

        // Setup ViewModel observers
        setupViewModelObservers()

        isPlaybackMode = intent.getBooleanExtra(KEY_PLAYBACK_MODE, false)
        currentTrackId = intent.extras?.getLong(TrackContentProvider.Schema.COL_TRACK_ID) ?: 0
        disableGpsService = intent.getBooleanExtra("disable_gps_service", false)
        isFromDriver = intent.getBooleanExtra("from_driver", false)
        isFromNewSupervisor = intent.getBooleanExtra(EXTRA_FROM_NEW_SUPERVISOR, false)
        
        // Check if we have zone encryption for zone display
        val zoneEncryption = intent.getStringExtra("zone_encryption")
        Log.d(TAG, "Zone encryption from intent: $zoneEncryption")
        if (zoneEncryption != null && zoneEncryption.isNotEmpty()) {
            fetchAndDisplayZone(zoneEncryption)
        } else {
            Log.w(TAG, "No zone encryption provided in intent")
        }

        if (currentTrackId <= 0) {
            finish()
            return
        }

        if (!isPlaybackMode && !disableGpsService) {
            gpsLoggerServiceIntent = Intent(this, GPSLogger::class.java).apply {
                action = OSMTracker.INTENT_START_TRACKING
                putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
            }
            sensorListener = SensorListener()
        }

        title = when {
            isPlaybackMode -> "نمایش مسیر: #$currentTrackId"
            disableGpsService -> "نمایش نقشه و موقعیت زنده: #$currentTrackId"
            else -> "ردیابی مسیر: #$currentTrackId"
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateBackToTrackManager()
            }
        })

        initializeViews()
        initializeMap()
        setupSubmitButton()
        initializeRoadInfoUpdater()

        fetchMapLayersFromApi()
    }

    /**
     * Setup ViewModel observers for MVVM architecture
     */
    private fun setupViewModelObservers() {
        // Observe road information
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.roadInfo.collect { roadInfo ->
                    updateRoadInfoUI(roadInfo)
                }
            }
        }
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.gisLayers.collect { layers ->
                    updateLayerControlsWithLayers(layers)
                }
            }
        }
        
        // Observe loading states
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoadingRoadInfo.collect { isLoading ->
                    if (isLoading) {
                        roadNameTextView.text = getString(R.string.map_loading_address)
                    }
                }
            }
        }
        
        // Observe error messages
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorMessage.collect { error ->
                    error?.let {
                        Toast.makeText(this@DisplayTrackMap, it, Toast.LENGTH_SHORT).show()
                        viewModel.clearError()
                    }
                }
            }
        }
        
        // Observe mission details
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.missionDetails.collect { details ->
                    details?.let {
                        val gson = com.google.gson.Gson()
                        val responseJson = gson.toJson(it)
                        parseAndDisplayZone(responseJson)
                        viewModel.clearMissionDetails()
                    }
                }
            }
        }
    }
    
    /**
     * Update road info UI elements
     */
    private fun updateRoadInfoUI(roadInfo: Ir.co.tfs.farazaman.presentation.viewmodel.RoadInfo) {
        roadNameTextView.text = roadInfo.name.ifBlank { getString(R.string.map_address_unknown) }
    }

    private fun updateLayerControlsWithLayers(
        layers: List<Ir.co.tfs.farazaman.presentation.viewmodel.SpeedDialLayerItem>
    ) {
        cachedLayers = layers
        rebuildLayerItems()
    }

    private fun rebuildLayerItems() {
        layerItemsContainer.removeAllViews()
        if (cachedLayers.isEmpty()) return

        lifecycleScope.launch {
            cachedLayers.forEach { layer ->
                val row = layoutInflater.inflate(R.layout.item_map_layer_control, layerItemsContainer, false)
                row.findViewById<TextView>(R.id.layerLabel).text = layer.name

                val button = row.findViewById<ImageButton>(R.id.layerButton)
                var iconDrawable: Drawable? = null
                if (!layer.iconUrl.isNullOrBlank()) {
                    iconDrawable = svgStringToDrawable(layer.iconUrl)
                }
                if (iconDrawable != null) {
                    button.setImageDrawable(iconDrawable)
                } else {
                    button.setImageResource(R.drawable.ic_fab_add_track)
                    button.setColorFilter(resources.getColor(R.color.supervisor_header_green, theme))
                }

                button.setOnClickListener {
                    val location = myLocationOverlay.myLocation
                    if (location != null) {
                        saveWaypointToTrack(
                            trackId = currentTrackId,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            name = layer.name,
                            link = layer.id.toString()
                        )
                    } else {
                        Toast.makeText(this@DisplayTrackMap, "موقعیت مکانی یافت نشد", Toast.LENGTH_SHORT).show()
                    }
                }
                layerItemsContainer.addView(row)
            }
            layerItemsContainer.visibility = if (layersExpanded) View.VISIBLE else View.GONE
        }
    }

    private fun setLayersExpanded(expanded: Boolean) {
        layersExpanded = expanded
        layerItemsContainer.visibility = if (expanded && cachedLayers.isNotEmpty()) View.VISIBLE else View.GONE
        btnLayersToggle.setImageResource(if (expanded) R.drawable.ic_close else R.drawable.ic_map_plus)
    }

//    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
//        menuInflater.inflate(R.menu.displaytrackmap_menu, menu)
//        return true
//    }
//
//    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
//        return when (item.itemId) {
//            R.id.menu_export_gpx -> {
//                exportTrackToGpx(currentTrackId)
//                true
//            }
//            else -> super.onOptionsItemSelected(item)
//        }
//    }

    private fun initializeViews() {
        roadNameTextView = findViewById(R.id.road_name)
        layerItemsContainer = findViewById(R.id.layerItemsContainer)
        btnLayersToggle = findViewById(R.id.btnLayersToggle)

        findViewById<ImageButton>(R.id.fab_back_to_track_manager).setOnClickListener {
            navigateBackToTrackManager()
        }
    }

    private fun navigateBackToTrackManager() {
        when {
            isFromNewSupervisor -> finish()
            isFromDriver -> {
                val intent = Intent(this, DriverActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
            else -> {
                val intent = Intent(this, NewSupervisorActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
        }
    }

    // این متد حالا مسئول شروع تایمر است
    fun setGpsLogger(l: GPSLogger) {
        gpsLogger = l
        // بعد از اینکه سرویس متصل شد، بررسی می‌کنیم که آیا باید تایمر را روشن کنیم
        if (!isPlaybackMode && !disableGpsService && gpsLogger?.isTracking == true) {
            // هر فراخوانی قبلی را حذف می‌کنیم تا تایمر چندبار اجرا نشود
            roadInfoUpdateHandler.removeCallbacks(roadInfoRunnable)
            // تایمر را برای اولین بار فعال می‌کنیم
            roadInfoUpdateHandler.post(roadInfoRunnable)
        } else if (!isPlaybackMode && disableGpsService) {
            // Live mode without GPS tracking - start road info updates immediately
            roadInfoUpdateHandler.removeCallbacks(roadInfoRunnable)
            roadInfoUpdateHandler.post(roadInfoRunnable)
        }
    }

    fun getGpsLogger(): GPSLogger? = gpsLogger
    fun getCurrentTrackId(): Long = currentTrackId

    private fun initializeMap() {
        osmView = findViewById(R.id.displaytrackmap_osmView)
        osmView.setTileSource(TileSourceFactory.MAPNIK)
        osmView.setMultiTouchControls(true)
        osmView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        osmView.keepScreenOn = prefs.getBoolean(OSMTracker.Preferences.KEY_UI_DISPLAY_KEEP_ON, true)
        osmViewController = osmView.controller
        osmViewController.setZoom(16.0)
        createOverlays()
        setupMapButtons()
        trackpointContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                pathChanged()
            }
        }
    }

    private fun setupMapButtons() {
        val myLocationButton = findViewById<View>(R.id.displaytrackmap_imgZoomCenter)
        myLocationButton.setOnClickListener {
            if (!isPlaybackMode) {
                myLocationOverlay.myLocation?.let {
                    osmViewController.animateTo(it)
                    osmViewController.setZoom(18.0)
                } ?: Toast.makeText(this, "در حال یافتن موقعیت مکانی...", Toast.LENGTH_SHORT).show()
            }
        }
        if (isPlaybackMode) myLocationButton.visibility = View.GONE

        findViewById<ImageButton>(R.id.btnZoomIn).setOnClickListener { osmViewController.zoomIn() }
        findViewById<ImageButton>(R.id.btnZoomOut).setOnClickListener { osmViewController.zoomOut() }

        btnLayersToggle.setOnClickListener {
            if (cachedLayers.isEmpty()) {
                Toast.makeText(this, "لایه‌ای برای نمایش وجود ندارد", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            setLayersExpanded(!layersExpanded)
        }
    }

    private fun initializeRoadInfoUpdater() {
        roadInfoRunnable = Runnable {
            if (!isPlaybackMode) {
                val loc = myLocationOverlay.myLocation
                if (loc != null) {
                    Log.d(TAG, "RoadInfoUpdater tick: lat=${loc.latitude}, lon=${loc.longitude}")
                    fetchAndDisplayRoadInfo(loc.latitude, loc.longitude)
                } else {
                    Log.d(TAG, "RoadInfoUpdater tick: location is null; will retry")
                }
                // Continue updating road info in both live mode and tracking mode
                roadInfoUpdateHandler.postDelayed(roadInfoRunnable, ROAD_INFO_UPDATE_INTERVAL_MS)
            }
        }
        // Ensure the updater starts even if GPSLogger hasn't posted it yet
        if (!isPlaybackMode) {
            roadInfoUpdateHandler.removeCallbacks(roadInfoRunnable)
            roadInfoUpdateHandler.post(roadInfoRunnable)
            Log.d(TAG, "RoadInfoUpdater: initial post() from initializeRoadInfoUpdater")
        }
    }

    private fun fetchAndDisplayRoadInfo(lat: Double, lon: Double) {
        val buffer = GpsAccuracyHelper.resolveBuffer(this, currentTrackId)
        viewModel.fetchRoadInfo(lat, lon, buffer)
    }

    private fun roadQueryBuffer(): Int = GpsAccuracyHelper.resolveBuffer(this, currentTrackId)

    private fun createOverlays() {
        polyline = Polyline().apply {
            outlinePaint.color = Color.BLUE
            outlinePaint.strokeWidth = 8.0f
        }
        osmView.overlayManager.add(polyline)
        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), osmView).apply {
            enableMyLocation()
            enableFollowLocation()
            // Enable location overlay for both live mode and full tracking mode
            isEnabled = !isPlaybackMode
        }
        osmView.overlays.add(myLocationOverlay)
        wayPointsOverlay = WayPointsOverlay(this, currentTrackId)
        osmView.overlays.add(wayPointsOverlay)
    }

    override fun onResume() {
        super.onResume()
        osmView.onResume()
        
        if (isPlaybackMode) {
            // Playback mode - show track only, no GPS
            findViewById<View>(R.id.gpsStatus).visibility = View.GONE
            myLocationOverlay.disableMyLocation()
        } else if (disableGpsService) {
            // Live mode without GPS tracking - show current location but don't start tracking service
            findViewById<GpsStatusRecordDisplay>(R.id.gpsStatus).requestLocationUpdates(true)
            myLocationOverlay.enableMyLocation()
            // Don't start GPS service or bind to it
        } else {
            // Full tracking mode - start GPS service and tracking
            findViewById<GpsStatusRecordDisplay>(R.id.gpsStatus).requestLocationUpdates(true)
            myLocationOverlay.enableMyLocation()
            startService(gpsLoggerServiceIntent)
            gpsLoggerServiceIntent?.let { bindService(it, gpsLoggerConnection, 0) }
            sensorListener?.register(this)
        }
        
        contentResolver.registerContentObserver(TrackContentProvider.trackPointsUri(currentTrackId), true, trackpointContentObserver)
        zoomedToTrackAlready = false
        pathChanged()
        wayPointsOverlay.refresh()
    }

    override fun onPause() {
        super.onPause()
        osmView.onPause()
        roadInfoUpdateHandler.removeCallbacks(roadInfoRunnable)
        contentResolver.unregisterContentObserver(trackpointContentObserver)
        
        if (isPlaybackMode) {
            // Playback mode - nothing to clean up
        } else if (disableGpsService) {
            // Live mode without GPS tracking - just disable location updates
            myLocationOverlay.disableMyLocation()
            findViewById<GpsStatusRecordDisplay>(R.id.gpsStatus).requestLocationUpdates(false)
        } else {
            // Full tracking mode - clean up GPS service
            myLocationOverlay.disableMyLocation()
            try {
                if (gpsLogger != null) unbindService(gpsLoggerConnection)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Service was not registered or already unbound.")
            }
            findViewById<GpsStatusRecordDisplay>(R.id.gpsStatus).requestLocationUpdates(false)
            sensorListener?.unregister()
        }
    }

    private fun pathChanged() {
        if (isFinishing) return

        val points = mutableListOf<GeoPoint>()
        var minLat = 91.0
        var minLon = 181.0
        var maxLat = -91.0
        var maxLon = -181.0

        val projection = arrayOf(TrackContentProvider.Schema.COL_LATITUDE, TrackContentProvider.Schema.COL_LONGITUDE)
        contentResolver.query(
            TrackContentProvider.trackPointsUri(currentTrackId),
            projection, null, null, "${TrackContentProvider.Schema.COL_ID} asc"
        )?.use { c ->
            if (c.moveToFirst()) {
                val latCol = c.getColumnIndex(TrackContentProvider.Schema.COL_LATITUDE)
                val lonCol = c.getColumnIndex(TrackContentProvider.Schema.COL_LONGITUDE)
                do {
                    val lat = c.getDouble(latCol)
                    val lon = c.getDouble(lonCol)
                    points.add(GeoPoint(lat, lon))

                    if (lat < minLat) minLat = lat
                    if (lon < minLon) minLon = lon
                    if (lat > maxLat) maxLat = lat
                    if (lon > maxLon) maxLon = lon
                } while (c.moveToNext())
            }
        }
        polyline.setPoints(points)
        osmView.invalidate()

        if (!zoomedToTrackAlready && points.isNotEmpty()) {
            if (points.size > 1) {
                osmView.post {
                    val boundingBox = BoundingBox(maxLat, maxLon, minLat, minLon)
                    osmView.zoomToBoundingBox(boundingBox, true, 50)
                    zoomedToTrackAlready = true
                }
            } else {
                osmViewController.animateTo(points.first())
                osmViewController.setZoom(18.0)
                zoomedToTrackAlready = true
            }
        }
    }

    private fun setupSubmitButton() {
        val submitButton = findViewById<Button>(R.id.submit_violation)
        android.util.Log.d("DisplayTrackMap", "setupSubmitButton called, playback mode: $isPlaybackMode")
        if (isPlaybackMode) {
            submitButton.visibility = View.GONE
        } else {
            submitButton.setOnClickListener {
                android.util.Log.d("DisplayTrackMap", "=== SUBMIT_VIOLATION BUTTON CLICKED ===")
                android.util.Log.d("DisplayTrackMap", "currentTrackId = $currentTrackId")
                
                // دریافت موقعیت مکانی فعلی از لایه نقشه
                val currentLocation: GeoPoint? = myLocationOverlay.myLocation
                android.util.Log.d("DisplayTrackMap", "currentLocation = $currentLocation")

                if (currentLocation != null) {
                    // Fetch address from RoadService for the current location
                    fetchAddressForViolation(currentLocation.latitude, currentLocation.longitude) { address ->
                        android.util.Log.d("DisplayTrackMap", "Address fetched: $address")
                        
                        // Get mission data from SharedPreferences for this track
                        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this)
                        val missionDataKey = "mission_data_$currentTrackId"
                        val missionDataJson = prefs.getString(missionDataKey, null)
                        
                        android.util.Log.d("DisplayTrackMap", "Looking for mission data with key: $missionDataKey")
                        android.util.Log.d("DisplayTrackMap", "Mission data JSON: $missionDataJson")
                        
                        // ارسال هر سه مقدار به اکتیویتی فرم
                        val intent = Intent(this, SubmitViolationFormActivity::class.java).apply {
                            android.util.Log.d("DisplayTrackMap", "Creating intent with currentTrackId: $currentTrackId")
                            putExtra(EXTRA_LATITUDE, currentLocation.latitude)
                            putExtra(EXTRA_LONGITUDE, currentLocation.longitude)
                            putExtra("extra_address", address) // ارسال آدرس به عنوان یک رشته
                            putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
                            android.util.Log.d("DisplayTrackMap", "Added TRACK_ID extra: $currentTrackId")
                            
                            // Add mission data if available (organId, contractId, etc.)
                            if (missionDataJson != null) {
                                try {
                                    val missionData = org.json.JSONObject(missionDataJson)
                                    val organId = missionData.optInt("organId", 0)
                                    val contractId = missionData.optInt("contractId", 0)
                                    val organTitle = missionData.optString("organTitle", "")
                                    val contractTitle = missionData.optString("contractTitle", "")
                                    val visitDate = missionData.optString("visitDate", "")
                                    
                                    putExtra("organ_id", organId)
                                    putExtra("contract_id", contractId)
                                    putExtra("organ_title", organTitle)
                                    putExtra("contract_title", contractTitle)
                                    putExtra("visit_date", visitDate)
                                    
                                    android.util.Log.d("DisplayTrackMap", "✓ Added mission data: organId=$organId, contractId=$contractId")
                                } catch (e: Exception) {
                                    android.util.Log.e("DisplayTrackMap", "✗ Error parsing mission data: ${e.message}", e)
                                }
                            } else {
                                android.util.Log.w("DisplayTrackMap", "✗ No mission data found for track $currentTrackId")
                            }
                            
                            // Mark this as coming from submit_violation button in map
                            putExtra("from_submit_violation_card", true)
                            android.util.Log.d("DisplayTrackMap", "Added from_submit_violation_card flag")
                        }
                        
                        android.util.Log.d("DisplayTrackMap", "=== FINAL INTENT SUMMARY ===")
                        android.util.Log.d("DisplayTrackMap", "Track ID: ${intent.getLongExtra(TrackContentProvider.Schema.COL_TRACK_ID, -999)}")
                        android.util.Log.d("DisplayTrackMap", "organ_id: ${intent.getIntExtra("organ_id", -999)}")
                        android.util.Log.d("DisplayTrackMap", "contract_id: ${intent.getIntExtra("contract_id", -999)}")
                        android.util.Log.d("DisplayTrackMap", "from_submit_violation_card: ${intent.getBooleanExtra("from_submit_violation_card", false)}")
                        android.util.Log.d("DisplayTrackMap", "Starting SubmitViolationFormActivity...")
                        
                        startActivity(intent)
                    }
                } else {
                    Toast.makeText(this, "موقعیت مکانی هنوز یافت نشده است.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchAddressForViolation(lat: Double, lon: Double, onAddressFetched: (String) -> Unit) {
        viewModel.fetchAddressForViolation(lat, lon, roadQueryBuffer(), onAddressFetched)
    }

    private suspend fun svgStringToDrawable(svgString: String): Drawable? {
        // Save SVG string to a temporary file
        val tempFile = File.createTempFile("icon_", ".svg", cacheDir)
        FileOutputStream(tempFile).use { it.write(svgString.toByteArray()) }
        val loader = ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .build()
        val request = ImageRequest.Builder(this)
            .data(tempFile)
            .size(96, 96) // Set explicit size for SVG rendering
            .build()
        val result = loader.execute(request)
        tempFile.delete()
        val drawable = (result as? SuccessResult)?.drawable
        // Set proper bounds for the drawable
        drawable?.setBounds(0, 0, 96, 96)
        return drawable
    }

    private fun fetchMapLayersFromApi() {
        viewModel.fetchGisLayers()
    }

    private fun saveWaypointToTrack(trackId: Long, latitude: Double, longitude: Double, name: String, link: String? = null) {
        val values = ContentValues().apply {
            put(Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_TRACK_ID, trackId)
            put(Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_LATITUDE, latitude)
            put(Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_LONGITUDE, longitude)
            put(Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_NAME, name)
            put(Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_LINK, link)
            put(Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_TIMESTAMP, System.currentTimeMillis())
            put(Ir.co.tfs.farazaman.data.db.TrackContentProvider.Schema.COL_NBSATELLITES, 0)
        }
        val uri = contentResolver.insert(Ir.co.tfs.farazaman.data.db.TrackContentProvider.waypointsUri(trackId), values)
        if (uri != null) {
            Toast.makeText(this, "نقطه ثبت شد", Toast.LENGTH_SHORT).show()
            wayPointsOverlay.refresh()
        } else {
            Toast.makeText(this, "خطا در ثبت نقطه", Toast.LENGTH_SHORT).show()
        }
    }

//    private fun exportTrackToGpx(trackId: Long) {
//        // Request permission if needed
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
//            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
//            requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001)
//            return
//        }
//        try {
//            val gpxString = generateGpxForTrack(trackId)
//            val dir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "faraZaman")
//            if (!dir.exists()) dir.mkdirs()
//            val gpxFile = java.io.File(dir, "track_${trackId}.gpx")
//            java.io.FileWriter(gpxFile).use { it.write(gpxString) }
//            Toast.makeText(this, "GPX ذخیره شد: ${gpxFile.absolutePath}", Toast.LENGTH_LONG).show()
//        } catch (e: Exception) {
//            Toast.makeText(this, "خطا در ذخیره GPX: ${e.message}", Toast.LENGTH_LONG).show()
//        }
//    }

    private fun generateGpxForTrack(trackId: Long): String {
        // Use your existing GPX writer logic here
        // This is a placeholder. Replace with actual GPX generation.
        // For example, use GpxWriter or similar from your codebase.
        return "<gpx><trk><name>Track $trackId</name></trk></gpx>"
    }

    private fun fetchAndDisplayZone(encryption: String) {
        Log.d(TAG, "Fetching zone data for encryption: $encryption")
        // Use ViewModel instead of direct API call
        viewModel.fetchMissionDetails(encryption)
    }

    private fun parseAndDisplayZone(responseBody: String) {
        try {
            Log.d(TAG, "Parsing response: $responseBody")
            val jsonResponse = org.json.JSONObject(responseBody)
            
            // Check if response has the expected structure
            if (!jsonResponse.has("dataModel")) {
                Log.e(TAG, "Response does not contain 'dataModel' field")
                Toast.makeText(this, "پاسخ سرور دارای ساختار نامعتبر است", Toast.LENGTH_SHORT).show()
                return
            }
            
            val dataModel = jsonResponse.getJSONObject("dataModel")
            
            if (!dataModel.has("gisLayersData")) {
                Log.e(TAG, "dataModel does not contain 'gisLayersData' field")
                Toast.makeText(this, "داده‌های نقشه در پاسخ یافت نشد", Toast.LENGTH_SHORT).show()
                return
            }
            
            val gisLayersData = dataModel.getJSONArray("gisLayersData")
            Log.d(TAG, "Found ${gisLayersData.length()} GIS layers")
            
            var polygonsDisplayed = 0
            for (i in 0 until gisLayersData.length()) {
                val layerData = gisLayersData.getJSONObject(i)
                Log.d(TAG, "Processing layer $i: ${layerData.toString()}")
                
                if (layerData.has("gisLayersDataWellKnownText")) {
                    val wellKnownText = layerData.getJSONObject("gisLayersDataWellKnownText")
                    if (wellKnownText.has("wellKnownText")) {
                        val wkt = wellKnownText.getString("wellKnownText")
                        Log.d(TAG, "WKT data: $wkt")
                        
                        // Parse WKT and display on map
                        if (displayPolygonOnMap(wkt)) {
                            polygonsDisplayed++
                        }
                    } else {
                        Log.w(TAG, "gisLayersDataWellKnownText does not contain 'wellKnownText' field")
                    }
                } else {
                    Log.w(TAG, "Layer does not contain 'gisLayersDataWellKnownText' field")
                }
            }
            
            if (polygonsDisplayed == 0) {
                Toast.makeText(this, "هیچ منطقه‌ای برای نمایش یافت نشد", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "$polygonsDisplayed منطقه روی نقشه نمایش داده شد", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing zone data", e)
            Toast.makeText(this, "خطا در پردازش اطلاعات منطقه: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayPolygonOnMap(wkt: String): Boolean {
        try {
            Log.d(TAG, "Displaying polygon with WKT: $wkt")
            
            // Parse WKT POLYGON format: "POLYGON ((lon1 lat1, lon2 lat2, ...))"
            if (wkt.startsWith("POLYGON ((") && wkt.endsWith("))")) {
                val coordinates = wkt.substring(10, wkt.length - 2) // Remove "POLYGON ((" and "))"
                Log.d(TAG, "Coordinates string: $coordinates")
                
                val points = coordinates.split(",").map { coord ->
                    val parts = coord.trim().split(" ")
                    if (parts.size >= 2) {
                        val lon = parts[0].toDouble()
                        val lat = parts[1].toDouble()
                        Log.d(TAG, "Parsed point: lat=$lat, lon=$lon")
                        org.osmdroid.util.GeoPoint(lat, lon)
                    } else {
                        Log.w(TAG, "Invalid coordinate format: $coord")
                        null
                    }
                }.filterNotNull()

                Log.d(TAG, "Parsed ${points.size} valid points")
                
                if (points.isNotEmpty()) {
                    // Create polygon overlay
                    val polygon = org.osmdroid.views.overlay.Polygon()
                    polygon.setPoints(points)
                    polygon.fillColor = android.graphics.Color.argb(50, 0, 255, 0) // Semi-transparent green
                    polygon.strokeColor = android.graphics.Color.GREEN
                    polygon.strokeWidth = 3f
                    
                    // Add to map
                    osmView.overlays.add(polygon)
                    osmView.invalidate()
                    
                    // Zoom to polygon bounds
                    val bounds = org.osmdroid.util.BoundingBox.fromGeoPoints(points)
                    osmView.zoomToBoundingBox(bounds, true, 100)
                    
                    Log.d(TAG, "Polygon successfully displayed on map")
                    return true
                } else {
                    Log.w(TAG, "No valid points found in WKT")
                    return false
                }
            } else {
                Log.w(TAG, "WKT does not match expected POLYGON format")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying polygon on map", e)
            return false
        }
    }
}
