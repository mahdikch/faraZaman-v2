package Ir.co.tfs.farazaman.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import Ir.co.tfs.farazaman.OSMTracker
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.data.model.RoadData
import Ir.co.tfs.farazaman.service.remote.RoadService
import Ir.co.tfs.farazaman.presentation.viewmodel.SubmitViolationViewModel
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import javax.inject.Inject
import Ir.co.tfs.farazaman.data.db.TrackContentProvider
import android.database.Cursor
import android.graphics.Color

@AndroidEntryPoint
class SubmitViolationActivity : AppCompatActivity() {
    private lateinit var locationManager: LocationManager
    private lateinit var prefs: SharedPreferences
    private lateinit var osmView: MapView
    private lateinit var osmViewController: IMapController
    private var userLocationMarker: Marker? = null
    private var centerToGpsPos = true
    private var currentPosition = GeoPoint(35.7627, 51.3353)
    private var zoomedToTrackAlready = false
    private var trackId: Long = -1
    private var trackPolyline: Polyline? = null

    @Inject
    lateinit var roadService: RoadService
    
    // ViewModel
    private val viewModel: SubmitViolationViewModel by viewModels()

    private lateinit var roadNameTextView: TextView
    private lateinit var roadTypeTextView: TextView
    private lateinit var speedLimitTextView: TextView

    companion object {
        private const val DEFAULT_ZOOM = 16
        private const val CURRENT_SCROLL_X = "currentScrollX"
        private const val CURRENT_CENTER_TO_GPS_POS = "currentCenterToGpsPos"
        private const val CURRENT_SCROLL_Y = "currentScrollY"
        private const val CENTER_DEFAULT_ZOOM_LEVEL = 18.0
        private const val CURRENT_ZOOM = "currentZoom"
        private const val CURRENT_ZOOMED_TO_TRACK = "currentZoomedToTrack"
        private const val LAST_ZOOM = "lastZoomLevel"
        private const val ANIMATION_DURATION_MS = 1000L
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        const val EXTRA_TRACK_ID = "track_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_submit_violation)

        trackId = intent.getLongExtra(EXTRA_TRACK_ID, -1)

        initializeViews()
        initializeLocationManager()
        initializePreferences()
        initializeMap(savedInstanceState)
        setupSubmitButton()
        setupZoomControls()
        setupViewModelObservers()
        
        if (trackId != -1L) {
            displayTrackPoints()
        }
    }

    private fun initializeViews() {
        roadNameTextView = findViewById(R.id.road_name)
        roadTypeTextView = findViewById(R.id.road_type)
        speedLimitTextView = findViewById(R.id.speed_limit)
    }

    private fun initializeLocationManager() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (hasLocationPermission()) {
            startGettingLocation()
        } else {
            requestLocationPermission()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun initializePreferences() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
    }

    private fun initializeMap(savedInstanceState: Bundle?) {
        Configuration.getInstance().load(this, prefs)
        osmView = findViewById(R.id.map_view)
        osmView.apply {
            setMultiTouchControls(true)
            getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setKeepScreenOn(
                prefs.getBoolean(
                    OSMTracker.Preferences.KEY_UI_DISPLAY_KEEP_ON,
                    OSMTracker.Preferences.VAL_UI_DISPLAY_KEEP_ON
                )
            )
        }

        osmViewController = osmView.controller

        if (savedInstanceState != null) {
            osmViewController.setZoom(savedInstanceState.getInt(CURRENT_ZOOM, DEFAULT_ZOOM))
            osmView.scrollTo(
                savedInstanceState.getInt(CURRENT_SCROLL_X, 0),
                savedInstanceState.getInt(CURRENT_SCROLL_Y, 0)
            )
            centerToGpsPos = savedInstanceState.getBoolean(CURRENT_CENTER_TO_GPS_POS, centerToGpsPos)
            zoomedToTrackAlready = savedInstanceState.getBoolean(CURRENT_ZOOMED_TO_TRACK, zoomedToTrackAlready)
        } else {
            getPreferences(MODE_PRIVATE).getInt(LAST_ZOOM, DEFAULT_ZOOM).let {
                osmViewController.setZoom(it)
            }
        }

        selectTileSource()
        setTileDpiScaling()
    }

    private fun setupSubmitButton() {
        findViewById<Button>(R.id.submit_violation).setOnClickListener {
            startActivity(Intent(this, SubmitViolationFormActivity::class.java))
        }
    }

    private fun setupZoomControls() {
        findViewById<View>(R.id.displaytrackmap_imgZoomIn).setOnClickListener {
            osmViewController.zoomIn()
        }
        findViewById<View>(R.id.displaytrackmap_imgZoomOut).setOnClickListener {
            osmViewController.zoomOut()
        }
        findViewById<View>(R.id.displaytrackmap_imgZoomCenter).setOnClickListener {
            centerToGpsPos = true
            currentPosition?.let { position ->
                osmViewController.animateTo(position, CENTER_DEFAULT_ZOOM_LEVEL, ANIMATION_DURATION_MS)
            }
        }
    }

    private fun startGettingLocation() {
        try {
            if (hasLocationPermission()) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1f,
                    locationListener
                )
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    1f,
                    locationListener
                )
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentPosition = GeoPoint(location.latitude, location.longitude)
            
            if (userLocationMarker == null) {
                userLocationMarker = Marker(osmView).apply {
                    position = currentPosition
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "موقعیت فعلی"
                }
                osmView.overlays.add(userLocationMarker)
            } else {
                userLocationMarker?.position = currentPosition
            }

            if (centerToGpsPos) {
                osmViewController.animateTo(currentPosition, CENTER_DEFAULT_ZOOM_LEVEL, ANIMATION_DURATION_MS)
            }

            // Fetch road data for the current location
            fetchRoadData(location.latitude, location.longitude, location.accuracy)

            osmView.invalidate()
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    /**
     * Setup ViewModel observers for MVVM architecture
     */
    private fun setupViewModelObservers() {
        // Observe road data
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.roadData.collect { roadData ->
                    roadData?.let { updateRoadInfo(it) }
                }
            }
        }
        
        // Observe loading state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoadingRoadData.collect { isLoading ->
                    if (isLoading) {
                        roadNameTextView.text = "در حال دریافت..."
                        roadTypeTextView.text = ""
                        speedLimitTextView.text = ""
                    }
                }
            }
        }
        
        // Observe error messages
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorMessage.collect { error ->
                    error?.let {
                        Log.e("SubmitViolationActivity", "ViewModel error: $it")
                        roadNameTextView.text = "خطا در دریافت اطلاعات"
                        roadTypeTextView.text = ""
                        speedLimitTextView.text = ""
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    private fun fetchRoadData(latitude: Double, longitude: Double, accuracyMeters: Float) {
        val buffer = Ir.co.tfs.farazaman.util.RoadQueryBuffer.forAccuracy(
            accuracyMeters.takeIf { it > 0f },
        )
        viewModel.fetchRoadData(latitude, longitude, buffer)
    }

    private fun updateRoadInfo(roadData: RoadData) {
        roadNameTextView.text = roadData.name
        roadTypeTextView.text = roadData.fclass
        speedLimitTextView.text = "${roadData.maxspeed} km/h"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startGettingLocation()
            } else {
                Log.w("TAG", "Location permission denied")
            }
        }
    }

    private fun selectTileSource() {
        val mapTile = prefs.getString(
            OSMTracker.Preferences.KEY_UI_MAP_TILE,
            OSMTracker.Preferences.VAL_UI_MAP_TILE_MAPNIK
        )
        Log.e("TileMapName active", mapTile ?: "")
        osmView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
    }

    private fun setTileDpiScaling() {
        osmView.setTilesScaledToDpi(true)
    }

    override fun onPause() {
        super.onPause()
//        if (hasLocationPermission()) {
//            locationManager.removeUpdates(locationListener)
//        }
    }

    override fun onResume() {
        super.onResume()
//        if (hasLocationPermission()) {
//            startGettingLocation()
//        }
    }

    private fun displayTrackPoints() {
        val cursor = contentResolver.query(
            TrackContentProvider.trackPointsUri(trackId),
            null,
            null,
            null,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val points = mutableListOf<GeoPoint>()
                do {
                    val lat = it.getDouble(it.getColumnIndexOrThrow(TrackContentProvider.Schema.COL_LATITUDE))
                    val lon = it.getDouble(it.getColumnIndexOrThrow(TrackContentProvider.Schema.COL_LONGITUDE))
                    points.add(GeoPoint(lat, lon))
                } while (it.moveToNext())

                if (points.isNotEmpty()) {
                    trackPolyline = Polyline(osmView).apply {
                        outlinePaint.color = Color.RED
                        outlinePaint.strokeWidth = 5f
                        setPoints(points)
                    }
                    osmView.overlays.add(trackPolyline)
                    osmView.invalidate()

                    // Center map on first point
                    osmViewController.animateTo(points.first(), CENTER_DEFAULT_ZOOM_LEVEL, ANIMATION_DURATION_MS)
                }
            }
        }
    }
} 
