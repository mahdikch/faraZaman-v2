package Ir.co.tfs.farazaman.activity

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import Ir.co.tfs.farazaman.OSMTracker
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.data.db.DataHelper
import Ir.co.tfs.farazaman.data.db.TrackContentProvider
import Ir.co.tfs.farazaman.layout.GpsStatusRecord
import Ir.co.tfs.farazaman.layout.UserDefinedLayout
import Ir.co.tfs.farazaman.listener.PressureListener
import Ir.co.tfs.farazaman.listener.SensorListener
import Ir.co.tfs.farazaman.receiver.MediaButtonReceiver
import Ir.co.tfs.farazaman.service.gps.GPSLogger
import Ir.co.tfs.farazaman.service.gps.GPSLoggerServiceConnection
import Ir.co.tfs.farazaman.util.CustomLayoutsUtils
import Ir.co.tfs.farazaman.util.FileSystemUtils
import Ir.co.tfs.farazaman.util.ThemeValidator
import Ir.co.tfs.farazaman.view.TextNoteDialog
import Ir.co.tfs.farazaman.view.VoiceRecDialog
import java.io.File
import java.util.Date
import java.util.HashSet
import java.util.UUID

class TrackLogger : Activity() {
    companion object {
        private val TAG = TrackLogger::class.java.simpleName
        const val STATE_IS_TRACKING = "isTracking"
        const val TAG_SEPARATOR = ","
        const val STATE_BUTTONS_ENABLED = "buttonsEnabled"
        const val DIALOG_TEXT_NOTE = 1
        const val DIALOG_VOICE_RECORDING = 2
        private const val REQCODE_IMAGE_CAPTURE = 0
        private const val REQCODE_GALLERY_CHOSEN = 1
    }

    private val RC_STORAGE_AUDIO_PERMISSIONS = 1
    private var gpsLogger: GPSLogger? = null
    private var gpsLoggerServiceIntent: Intent? = null
    private var mainLayout: UserDefinedLayout? = null
    private var checkGPSFlag = true
    private var currentPhotoFile: File? = null
    private var currentTrackId: Long = 0
    private var gpsLoggerConnection: ServiceConnection = GPSLoggerServiceConnection(this)
    private var prefs: SharedPreferences? = null
    private var buttonsEnabled = false
    private var sensorListener: SensorListener? = null
    private var pressureListener: PressureListener? = null
    private var mAudioManager: AudioManager? = null
    private var mediaButtonReceiver: ComponentName? = null
    private val layoutNameTags = HashSet<String>()

    fun getButtonsEnabled(): Boolean = buttonsEnabled

    override fun onCreate(savedInstanceState: Bundle?) {
        currentTrackId = intent.extras?.getLong(TrackContentProvider.Schema.COL_TRACK_ID) ?: 0L
        Log.v(TAG, "Starting for track id $currentTrackId")
        val layoutName = CustomLayoutsUtils.getCurrentLayoutName(applicationContext)
        layoutNameTags.add(layoutName)
        gpsLoggerServiceIntent = Intent(this, GPSLogger::class.java).apply {
            putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
        }
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        setTheme(resources.getIdentifier(ThemeValidator.getValidTheme(prefs, resources), null, null))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tracklogger)
        findViewById<View>(R.id.tracklogger_root).keepScreenOn = prefs?.getBoolean(OSMTracker.Preferences.KEY_UI_DISPLAY_KEEP_ON, OSMTracker.Preferences.VAL_UI_DISPLAY_KEEP_ON) == true
        if (savedInstanceState != null) {
            buttonsEnabled = savedInstanceState.getBoolean(STATE_BUTTONS_ENABLED, false)
        }
        sensorListener = SensorListener()
        pressureListener = PressureListener()
        mAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mediaButtonReceiver = ComponentName(this, MediaButtonReceiver::class.java.name)
    }

    private fun saveTagsForTrack() {
        val trackUri = ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, currentTrackId)
        val values = ContentValues()
        val tagsToSave = HashSet<String>()
        val cursor = contentResolver.query(trackUri, null, null, null, null)
        cursor?.use {
            val tagsIndex = it.getColumnIndex(TrackContentProvider.Schema.COL_TAGS)
            var previouslySavedTags: String? = null
            while (it.moveToNext()) {
                if (it.getString(tagsIndex) != null) {
                    previouslySavedTags = it.getString(tagsIndex)
                }
            }
            previouslySavedTags?.split(TAG_SEPARATOR)?.forEach { tag -> tagsToSave.add(tag) }
        }
        for (layoutFileName in layoutNameTags) {
            if (layoutFileName != OSMTracker.Preferences.VAL_UI_BUTTONS_LAYOUT) {
                tagsToSave.add(CustomLayoutsUtils.convertFileName(layoutFileName))
            }
        }
        tagsToSave.add("osmtracker")
        val tagsString = StringBuilder()
        for (tag in tagsToSave) {
            tagsString.append(tag).append(TAG_SEPARATOR)
        }
        if (tagsString.isNotEmpty()) tagsString.deleteCharAt(tagsString.length - 1)
        values.put(TrackContentProvider.Schema.COL_TAGS, tagsString.toString())
        contentResolver.update(trackUri, values, null, null)
    }

    override fun onResume() {
        title = getString(R.string.tracklogger) + ": #$currentTrackId"
        findViewById<View>(R.id.tracklogger_root).keepScreenOn = prefs?.getBoolean(OSMTracker.Preferences.KEY_UI_DISPLAY_KEEP_ON, OSMTracker.Preferences.VAL_UI_DISPLAY_KEEP_ON) == true
        val preferredOrientation = prefs?.getString(OSMTracker.Preferences.KEY_UI_ORIENTATION, OSMTracker.Preferences.VAL_UI_ORIENTATION)
        when (preferredOrientation) {
            OSMTracker.Preferences.VAL_UI_ORIENTATION_PORTRAIT -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OSMTracker.Preferences.VAL_UI_ORIENTATION_LANDSCAPE -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        try {
            val userLayout = prefs?.getString(OSMTracker.Preferences.KEY_UI_BUTTONS_LAYOUT, OSMTracker.Preferences.VAL_UI_BUTTONS_LAYOUT)
            mainLayout = if (OSMTracker.Preferences.VAL_UI_BUTTONS_LAYOUT == userLayout) {
                UserDefinedLayout(this, currentTrackId, null)
            } else {
                val layoutFile = File(getExternalFilesDir(null), OSMTracker.Preferences.VAL_STORAGE_DIR + File.separator + Preferences.LAYOUTS_SUBDIR + File.separator + userLayout)
                UserDefinedLayout(this, currentTrackId, layoutFile)
            }
            (findViewById<ViewGroup>(R.id.tracklogger_root)).removeAllViews()
            (findViewById<ViewGroup>(R.id.tracklogger_root)).addView(mainLayout)
        } catch (e: Exception) {
            Log.e(TAG, "Error while inflating UserDefinedLayout", e)
            Toast.makeText(this, R.string.error_userlayout_parsing, Toast.LENGTH_SHORT).show()
        }
        if (checkGPSFlag && prefs?.getBoolean(OSMTracker.Preferences.KEY_GPS_CHECKSTARTUP, OSMTracker.Preferences.VAL_GPS_CHECKSTARTUP) == true) {
            checkGPSProvider()
        }
        (findViewById<View>(R.id.gpsStatus) as GpsStatusRecord).requestLocationUpdates(true)
        startService(gpsLoggerServiceIntent)
        gpsLoggerServiceIntent?.let { bindService(it, gpsLoggerConnection, 0) }
        sensorListener?.register(this)
        pressureListener?.register(this, prefs?.getBoolean(OSMTracker.Preferences.KEY_USE_BAROMETER, OSMTracker.Preferences.VAL_USE_BAROMETER) == true)
        setEnabledActionButtons(buttonsEnabled)
        if (!buttonsEnabled) {
            Toast.makeText(this, R.string.tracklogger_waiting_gps, Toast.LENGTH_LONG).show()
        }
        mAudioManager?.registerMediaButtonEventReceiver(mediaButtonReceiver)
        val layoutName = CustomLayoutsUtils.getCurrentLayoutName(applicationContext)
        layoutNameTags.add(layoutName)
        super.onResume()
    }

    private fun checkGPSProvider() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.tracklogger_gps_disabled)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(getString(R.string.tracklogger_gps_disabled_hint))
                .setCancelable(true)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton(android.R.string.no) { dialog, _ ->
                    dialog.cancel()
                }
                .create().show()
            checkGPSFlag = false
        }
    }
    // کد اصلاح شده برای TrackLogger.kt -> onPause()
    override fun onPause() {
        super.onPause()
        (findViewById<View>(R.id.gpsStatus) as GpsStatusRecord).requestLocationUpdates(false)

        // فقط از سرویس جدا شو، آن را متوقف نکن
        gpsLogger?.let {
            unbindService(gpsLoggerConnection)
        }

        sensorListener?.unregister()
        pressureListener?.unregister()
        mAudioManager?.unregisterMediaButtonEventReceiver(mediaButtonReceiver)
    }
//    override fun onPause() {
//        (findViewById<View>(R.id.gpsStatus) as GpsStatusRecord).requestLocationUpdates(false)
//        gpsLogger?.let {
//            if (!it.isTracking) {
//                Log.v(TAG, "Service is not tracking, trying to stopService()")
//                unbindService(gpsLoggerConnection)
//                stopService(gpsLoggerServiceIntent)
//            } else {
//                unbindService(gpsLoggerConnection)
//            }
//        }
//        sensorListener?.unregister()
//        pressureListener?.unregister()
//        mAudioManager?.unregisterMediaButtonEventReceiver(mediaButtonReceiver)
//        super.onPause()
//    }

    override fun onSaveInstanceState(outState: Bundle) {
        gpsLogger?.let { outState.putBoolean(STATE_IS_TRACKING, it.isTracking) }
        outState.putBoolean(STATE_BUTTONS_ENABLED, buttonsEnabled)
        super.onSaveInstanceState(outState)
    }

    fun onGpsDisabled() {
        setEnabledActionButtons(false)
    }

    fun onGpsEnabled() {
        if (gpsLogger?.isTracking == true) {
            setEnabledActionButtons(true)
        }
    }

    fun setEnabledActionButtons(enabled: Boolean) {
        mainLayout?.let {
            buttonsEnabled = enabled
            it.setEnabled(enabled)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.tracklogger_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var i: Intent? = null
        when (item.itemId) {
            R.id.tracklogger_menu_stoptracking -> {
                if (gpsLogger?.isTracking == true) {
                    saveTagsForTrack()
                    val intent = Intent(OSMTracker.INTENT_STOP_TRACKING)
                    intent.setPackage(packageName)
                    sendBroadcast(intent)
                    (findViewById<View>(R.id.gpsStatus) as GpsStatusRecord).manageRecordingIndicator(false)
                    finish()
                }
            }
            R.id.tracklogger_menu_settings -> {
                startActivity(Intent(this, Preferences::class.java))
            }
            R.id.tracklogger_menu_waypointlist -> {
                i = Intent(this, WaypointList::class.java)
                i.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
                startActivity(i)
            }
            R.id.tracklogger_menu_about -> {
                startActivity(Intent(this, About::class.java))
            }
            R.id.tracklogger_menu_displaytrack -> {
                val useOpenStreetMapBackground = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(OSMTracker.Preferences.KEY_UI_DISPLAYTRACK_OSM, OSMTracker.Preferences.VAL_UI_DISPLAYTRACK_OSM)
                i = if (useOpenStreetMapBackground) {
                    Intent(this, DisplayTrackMap::class.java)
                } else {
                    Intent(this, DisplayTrack::class.java)
                }
                i.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
                startActivity(i)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (event.repeatCount == 0) {
                    if (mainLayout != null && mainLayout!!.getStackSize() > 1) {
                        mainLayout!!.pop()
                        return true
                    }
                }
            }
            KeyEvent.KEYCODE_CAMERA -> {
                Log.d(TAG, "click on camera button")
                if (gpsLogger?.isTracking == true) {
                    requestStillImage()
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if ((gpsLogger?.isTracking == true) && (event.eventTime - event.downTime) > OSMTracker.LONG_PRESS_TIME) {
                    showDialog(DIALOG_VOICE_RECORDING)
                    return true
                }
            }
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                if (gpsLogger?.isTracking == true) {
                    showDialog(DIALOG_VOICE_RECORDING)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    fun requestStillImage() {
        if (gpsLogger?.isTracking == true) {
            val pictureSource = prefs?.getString(OSMTracker.Preferences.KEY_UI_PICTURE_SOURCE, OSMTracker.Preferences.VAL_UI_PICTURE_SOURCE)
            when (pictureSource) {
                OSMTracker.Preferences.VAL_UI_PICTURE_SOURCE_CAMERA -> startCamera()
                OSMTracker.Preferences.VAL_UI_PICTURE_SOURCE_GALLERY -> startGallery()
                else -> {
                    val getImageFrom = AlertDialog.Builder(this)
                    getImageFrom.setTitle("Select:")
                    val opsChars = arrayOf(getString(R.string.tracklogger_camera), getString(R.string.tracklogger_gallery))
                    getImageFrom.setItems(opsChars) { dialog, which ->
                        when (which) {
                            0 -> startCamera()
                            1 -> startGallery()
                        }
                        dialog.dismiss()
                    }
                    getImageFrom.show()
                }
            }
        } else {
            Toast.makeText(baseContext, getString(R.string.error_externalstorage_not_writable), Toast.LENGTH_SHORT).show()
        }
    }

    fun getRealPathFromURI(contentUri: Uri): String? {
        val cursor = contentResolver.query(contentUri, null, null, null, null)
        val columnIndex = cursor?.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATA) ?: return null
        cursor.moveToFirst()
        val result = cursor.getString(columnIndex)
        cursor.close()
        return result
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.v(TAG, "Activity result: $requestCode, resultCode=$resultCode, Intent=$data")
        when (requestCode) {
            REQCODE_IMAGE_CAPTURE -> {
                if (resultCode == RESULT_OK) {
                    if (currentPhotoFile != null && currentPhotoFile!!.exists()) {
                        val intent = Intent(OSMTracker.INTENT_TRACK_WP)
                        intent.putExtra(OSMTracker.INTENT_KEY_UUID, UUID.randomUUID().toString())
                        intent.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
                        intent.putExtra(OSMTracker.INTENT_KEY_NAME, getString(R.string.wpt_stillimage))
                        intent.putExtra(OSMTracker.INTENT_KEY_LINK, currentPhotoFile!!.name)
                        intent.setPackage(packageName)
                        sendBroadcast(intent)
                    } else {
                        Log.e(TAG, "Cannot get image path from camera intent")
                    }
                }
            }
            REQCODE_GALLERY_CHOSEN -> {
                if (resultCode == RESULT_OK) {
                    val imagePath = getRealPathFromURI(data?.data ?: return)
                    val imageFile = File(imagePath ?: "")
                    if (imageFile.exists()) {
                        val destFile = createImageFile()
                        Log.d(TAG, "Copying gallery file '$imagePath' into '${destFile?.absolutePath}'")
                        FileSystemUtils.copyFile(destFile?.parentFile, File(imagePath), destFile?.name)
                        val intent = Intent(OSMTracker.INTENT_TRACK_WP)
                        intent.putExtra(OSMTracker.INTENT_KEY_UUID, UUID.randomUUID().toString())
                        intent.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, currentTrackId)
                        intent.putExtra(OSMTracker.INTENT_KEY_NAME, getString(R.string.wpt_stillimage))
                        intent.putExtra(OSMTracker.INTENT_KEY_LINK, destFile?.name)
                        intent.setPackage(packageName)
                        sendBroadcast(intent)
                    } else {
                        Log.e(TAG, "Cannot get image path from gallery intent")
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun getGpsLogger(): GPSLogger? = gpsLogger
    fun setGpsLogger(l: GPSLogger) { gpsLogger = l }

    fun createImageFile(): File? {
        val trackDir = DataHelper.getTrackDirectory(currentTrackId, this)
        if (!trackDir.exists() && !trackDir.mkdirs()) {
            Log.w(TAG, "Directory [${trackDir.absolutePath}] does not exist and cannot be created")
            return null
        }
        if (trackDir.exists() && trackDir.canWrite()) {
            val imageFile = File(trackDir, DataHelper.FILENAME_FORMATTER.format(Date()) + DataHelper.EXTENSION_JPG)
            Log.d(TAG, "New Image File: $imageFile")
            return imageFile
        }
        Log.w(TAG, "The directory [${trackDir.absolutePath}] will not allow files to be created")
        return null
    }

    override fun onCreateDialog(id: Int): Dialog? {
        return when (id) {
            DIALOG_TEXT_NOTE -> TextNoteDialog(this, currentTrackId)
            DIALOG_VOICE_RECORDING -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                        Log.w(TAG, "we should explain why we need write and record audio permission")
                    } else {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RC_STORAGE_AUDIO_PERMISSIONS)
                    }
                    null
                } else {
                    VoiceRecDialog(this, currentTrackId)
                }
            }
            else -> super.onCreateDialog(id)
        }
    }

    override fun onPrepareDialog(id: Int, dialog: Dialog) {
        when (id) {
            DIALOG_TEXT_NOTE -> (dialog as? TextNoteDialog)?.resetValues()
        }
        super.onPrepareDialog(id, dialog)
    }

    override fun onNewIntent(newIntent: Intent) {
        newIntent.extras?.let {
            if (it.containsKey(TrackContentProvider.Schema.COL_TRACK_ID)) {
                currentTrackId = it.getLong(TrackContentProvider.Schema.COL_TRACK_ID)
                intent = newIntent
            }
            if (newIntent.hasExtra("mediaButton") && gpsLogger?.isTracking == true) {
                showDialog(DIALOG_VOICE_RECORDING)
            }
        }
        super.onNewIntent(newIntent)
    }

    fun getCurrentTrackId(): Long = currentTrackId

    private fun startCamera() {
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        currentPhotoFile = createImageFile()
        if (currentPhotoFile == null) {
            Log.e(TAG, "imageFile is NULL in startCamera")
            return
        }
        val imageUriContent = FileProvider.getUriForFile(this, DataHelper.FILE_PROVIDER_AUTHORITY, currentPhotoFile!!)
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(MediaStore.EXTRA_OUTPUT, imageUriContent)
        }
        startActivityForResult(cameraIntent, REQCODE_IMAGE_CAPTURE)
    }

    private fun startGallery() {
        val galleryIntent = Intent().apply {
            type = DataHelper.MIME_TYPE_IMAGE
            action = Intent.ACTION_GET_CONTENT
        }
        startActivityForResult(galleryIntent, REQCODE_GALLERY_CHOSEN)
    }

    override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<String>, @NonNull grantResults: IntArray) {
        when (requestCode) {
            RC_STORAGE_AUDIO_PERMISSIONS -> {
                if (grantResults.size > 1) {
                    VoiceRecDialog(this, currentTrackId)
                } else {
                    Log.v(TAG, "Voice recording permission is denied.")
                }
                return
            }
        }
    }
} 
