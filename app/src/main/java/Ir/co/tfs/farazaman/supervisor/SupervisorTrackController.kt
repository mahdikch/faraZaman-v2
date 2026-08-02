package Ir.co.tfs.farazaman.supervisor

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Build
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.activity.DisplayTrackMap
import Ir.co.tfs.farazaman.activity.EndMissionBottomSheet
import Ir.co.tfs.farazaman.activity.CommitmentsSubmitViolationFormActivity
import Ir.co.tfs.farazaman.OSMTracker
import Ir.co.tfs.farazaman.data.db.DataHelper
import Ir.co.tfs.farazaman.data.db.TrackContentProvider
import Ir.co.tfs.farazaman.gpx.ZipHelper
import Ir.co.tfs.farazaman.service.gps.GPSLogger
import Ir.co.tfs.farazaman.util.FileSystemUtils
import Ir.co.tfs.farazaman.util.GpxWriter
import Ir.co.tfs.farazaman.util.LoadingDialog
import Ir.co.tfs.farazaman.util.UserManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import saman.zamani.persiandate.PersianDate
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Date

class SupervisorTrackController(
    private val activity: AppCompatActivity,
    private val okHttpClient: OkHttpClient,
    private val userManager: UserManager,
    private val onStateChanged: () -> Unit,
) {
    companion object {
        private const val TAG = "SupervisorTrackCtrl"
        const val TRACK_ID_NONE = -1L
        private const val LOCATION_PERMISSION_REQUEST_CODE = 71
        private const val RC_WRITE_PERMISSIONS_UPLOAD = 72
    }

    var currentTrackId: Long = TRACK_ID_NONE
    private var pendingTrackId: Long = TRACK_ID_NONE
    private var permissionRequestTrackId: Long = TRACK_ID_NONE
    private var loadingDialog: LoadingDialog? = null

    fun loadState() {
        currentTrackId = findLatestSupervisorTrackId()
        val activeId = DataHelper.getActiveTrackId(activity.contentResolver, "supervisor")
        if (activeId != TRACK_ID_NONE) {
            currentTrackId = activeId
        }
    }

    fun hasMission(): Boolean = currentTrackId != TRACK_ID_NONE &&
        missionDataJson(currentTrackId) != null

    fun isTracking(): Boolean {
        val activeId = DataHelper.getActiveTrackId(activity.contentResolver, "supervisor")
        return activeId != TRACK_ID_NONE && activeId == currentTrackId
    }

    fun missionDataJson(trackId: Long): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        return prefs.getString("mission_data_$trackId", null)
    }

    fun missionInfo(trackId: Long): MissionInfo? {
        val json = missionDataJson(trackId) ?: return null
        return try {
            val obj = JSONObject(json)
            MissionInfo(
                organTitle = obj.optString("organTitle", "-"),
                contractTitle = obj.optString("contractTitle", "-"),
                contractId = obj.optInt("contractId", 0),
                organId = obj.optInt("organId", 0),
                visitDate = obj.optString("visitDate", "-"),
                districtLabel = obj.optString("districtLabel", obj.optString("zoneLabel", "-")),
                planningCount = obj.optJSONArray("planningItems")?.length() ?: 0,
                systemCount = obj.optJSONArray("systemItems")?.length() ?: 0,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun trackPointCounts(trackId: Long): Pair<Int, Int> {
        var tps = 0
        var wps = 0
        activity.contentResolver.query(
            TrackContentProvider.trackPointsUri(trackId),
            arrayOf(TrackContentProvider.Schema.COL_ID),
            null,
            null,
            null,
        )?.use { tps = it.count }
        activity.contentResolver.query(
            TrackContentProvider.waypointsUri(trackId),
            arrayOf(TrackContentProvider.Schema.COL_ID),
            null,
            null,
            null,
        )?.use { wps = it.count }
        return Pair(tps, wps)
    }

    fun receiveProgram() {
        val sheet = Ir.co.tfs.farazaman.activity.NewMissionBottomSheetFragment()
        sheet.show(activity.supportFragmentManager, "NewMissionBottomSheet")
    }

    fun startMission() {
        if (currentTrackId == TRACK_ID_NONE) return
        if (checkLocationPermissions()) {
            startGpsTracking(currentTrackId)
        } else {
            pendingTrackId = currentTrackId
            requestLocationPermissions()
        }
    }

    fun stopMission() {
        if (currentTrackId == TRACK_ID_NONE) return
        val intent = Intent(OSMTracker.INTENT_STOP_TRACKING).apply { setPackage(activity.packageName) }
        activity.sendBroadcast(intent)
        DataHelper(activity).stopTracking(currentTrackId)
        onStateChanged()
        Toast.makeText(activity, "ردیابی متوقف شد", Toast.LENGTH_SHORT).show()
    }

    fun confirmEndMission() {
        if (currentTrackId == TRACK_ID_NONE) return
        val trackId = currentTrackId
        EndMissionBottomSheet.newInstance {
            startUpload(trackId)
        }.show(activity.supportFragmentManager, "end_mission")
    }

    fun openViolationForm() {
        if (currentTrackId == TRACK_ID_NONE) {
            Toast.makeText(activity, "ابتدا برنامه را دریافت کنید", Toast.LENGTH_SHORT).show()
            return
        }
        val trackId = currentTrackId
        val json = missionDataJson(trackId)
        val lastLocation = getLastLocationOfTrack(trackId)
        val intent = Intent(activity, CommitmentsSubmitViolationFormActivity::class.java).apply {
            putExtra("from_submit_violation_card", true)
            putExtra(TrackContentProvider.Schema.COL_TRACK_ID, trackId)
            lastLocation?.let {
                putExtra(DisplayTrackMap.EXTRA_LATITUDE, it.latitude)
                putExtra(DisplayTrackMap.EXTRA_LONGITUDE, it.longitude)
            }
            if (json != null) {
                try {
                    val m = JSONObject(json)
                    putExtra("organ_id", m.optInt("organId", 0))
                    putExtra("contract_id", m.optInt("contractId", 0))
                    putExtra("organ_title", m.optString("organTitle", ""))
                    putExtra("contract_title", m.optString("contractTitle", ""))
                    putExtra("visit_date", m.optString("visitDate", ""))
                } catch (_: Exception) {
                }
            }
        }
        activity.startActivity(intent)
    }

    fun openMissionMap() {
        if (currentTrackId == TRACK_ID_NONE) {
            Toast.makeText(activity, "ابتدا برنامه را دریافت کنید", Toast.LENGTH_SHORT).show()
            return
        }
        TodayVisitMissionActions.openMap(activity, currentTrackId, encryption = null)
    }

    fun confirmDeleteMission() {
        if (currentTrackId == TRACK_ID_NONE) {
            Toast.makeText(activity, "برنامه‌ای برای حذف وجود ندارد", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.supervisor_delete_mission_title)
            .setMessage(R.string.supervisor_delete_mission_message)
            .setPositiveButton(R.string.supervisor_delete_mission_confirm) { _, _ ->
                deleteMission()
            }
            .setNegativeButton(R.string.end_mission_cancel, null)
            .show()
    }

    fun deleteMission() {
        val trackId = currentTrackId
        if (trackId == TRACK_ID_NONE) return

        if (isTracking()) {
            val intent = Intent(OSMTracker.INTENT_STOP_TRACKING).apply { setPackage(activity.packageName) }
            activity.sendBroadcast(intent)
            DataHelper(activity).stopTracking(trackId)
        }

        val dir = DataHelper.getTrackDirectory(trackId, activity)
        if (dir.exists()) FileSystemUtils.delete(dir, true)
        activity.contentResolver.delete(
            ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId),
            null,
            null,
        )

        clearMissionLocalData(trackId)
        currentTrackId = TRACK_ID_NONE
        onStateChanged()
        Toast.makeText(activity, R.string.supervisor_delete_mission_done, Toast.LENGTH_SHORT).show()
    }

    fun clearMissionLocalData(trackId: Long) {
        PreferenceManager.getDefaultSharedPreferences(activity).edit()
            .remove("mission_data_$trackId")
            .remove("section_expanded_${trackId}_planning")
            .remove("section_expanded_${trackId}_system")
            .apply()
        SupervisorViolationHistoryStore.removeForTrack(activity, trackId)
    }

    private fun getLastLocationOfTrack(trackId: Long): android.location.Location? {
        val cursor = activity.contentResolver.query(
            TrackContentProvider.trackPointsUri(trackId),
            arrayOf(
                TrackContentProvider.Schema.COL_LATITUDE,
                TrackContentProvider.Schema.COL_LONGITUDE,
                TrackContentProvider.Schema.COL_TIMESTAMP,
            ),
            null,
            null,
            "${TrackContentProvider.Schema.COL_TIMESTAMP} DESC",
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val latIdx = it.getColumnIndex(TrackContentProvider.Schema.COL_LATITUDE)
                val lonIdx = it.getColumnIndex(TrackContentProvider.Schema.COL_LONGITUDE)
                if (latIdx >= 0 && lonIdx >= 0) {
                    return android.location.Location("track_last_location").apply {
                        latitude = it.getDouble(latIdx)
                        longitude = it.getDouble(lonIdx)
                    }
                }
            }
        }
        return null
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    if (pendingTrackId != TRACK_ID_NONE) {
                        startGpsTracking(pendingTrackId)
                        pendingTrackId = TRACK_ID_NONE
                    }
                } else {
                    Toast.makeText(activity, "دسترسی موقعیت مکانی لازم است.", Toast.LENGTH_LONG).show()
                }
            }
            RC_WRITE_PERMISSIONS_UPLOAD -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startUpload(permissionRequestTrackId)
                }
            }
        }
    }

    private fun startGpsTracking(trackId: Long) {
        setActiveTrack(trackId)
        val startTrackingIntent = Intent(activity, GPSLogger::class.java).apply {
            action = OSMTracker.INTENT_START_TRACKING
            putExtra(TrackContentProvider.Schema.COL_TRACK_ID, trackId)
        }
        activity.startService(startTrackingIntent)
        onStateChanged()
        Toast.makeText(activity, "ماموریت شروع شد", Toast.LENGTH_SHORT).show()
    }

    private fun setActiveTrack(trackId: Long) {
        val activeId = DataHelper.getActiveTrackId(activity.contentResolver, "supervisor")
        if (activeId != TRACK_ID_NONE && activeId != trackId) {
            DataHelper(activity).stopTracking(activeId)
        }
        val values = ContentValues().apply {
            put(TrackContentProvider.Schema.COL_ACTIVE, TrackContentProvider.Schema.VAL_TRACK_ACTIVE)
        }
        activity.contentResolver.update(
            TrackContentProvider.CONTENT_URI_TRACK,
            values,
            "${TrackContentProvider.Schema.COL_ID} = ?",
            arrayOf(trackId.toString()),
        )
        currentTrackId = trackId
    }

    private fun startUpload(trackId: Long) {
        if (isTracking()) {
            val intent = Intent(OSMTracker.INTENT_STOP_TRACKING).apply { setPackage(activity.packageName) }
            activity.sendBroadcast(intent)
        }
        DataHelper(activity).stopTracking(trackId)
        loadingDialog = LoadingDialog.show(activity, "در حال آپلود...")
        UploadTask(trackId).execute()
    }

    private fun findLatestSupervisorTrackId(): Long {
        val trackIdProjection = arrayOf(
            "${TrackContentProvider.Schema.TBL_TRACK}.${TrackContentProvider.Schema.COL_ID} as ${TrackContentProvider.Schema.COL_ID}",
        )
        val cursor = activity.contentResolver.query(
            TrackContentProvider.CONTENT_URI_TRACK,
            trackIdProjection,
            "${TrackContentProvider.Schema.TBL_TRACK}.${TrackContentProvider.Schema.COL_ROLE} = ?",
            arrayOf("supervisor"),
            "${TrackContentProvider.Schema.TBL_TRACK}.${TrackContentProvider.Schema.COL_START_DATE} DESC",
        ) ?: return TRACK_ID_NONE
        cursor.use {
            if (it.moveToFirst()) {
                val col = it.getColumnIndex(TrackContentProvider.Schema.COL_ID)
                if (col >= 0) return it.getLong(col)
            }
        }
        return TRACK_ID_NONE
    }

    private fun checkLocationPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val fg = ContextCompat.checkSelfPermission(activity, Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED
        return fine && coarse && fg
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.FOREGROUND_SERVICE,
            ),
            LOCATION_PERMISSION_REQUEST_CODE,
        )
    }

    private fun selectedBaseUrl(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val base = prefs.getString("BASE_URL", "https://app.tfs.co.ir") ?: "https://app.tfs.co.ir"
        return if (base.endsWith("/")) base else "$base/"
    }

    private fun generateFileName(trackId: Long): String {
        val userName = userManager.getUserName() ?: "unknown"
        val persianDate = DataHelper.PERSIAN_FILENAME_FORMATTER.format(PersianDate())
        var contractTitle = "قرارداد"
        var organTitle = "کارفرما"
        missionDataJson(trackId)?.let {
            try {
                val m = JSONObject(it)
                contractTitle = m.optString("contractTitle", contractTitle)
                organTitle = m.optString("organTitle", organTitle)
            } catch (_: Exception) {
            }
        }
        return "${contractTitle}_${organTitle}_${persianDate}_${userName}_ناظر"
            .replace("[/\\\\:*?\"<>|]".toRegex(), "_")
    }

    @SuppressLint("StaticFieldLeak")
    private inner class UploadTask(private val trackId: Long) : AsyncTask<Void, Void, Boolean>() {
        private var errorMessage = "خطای نامشخص"

        override fun doInBackground(vararg params: Void?): Boolean {
            return try {
                val fileName = generateFileName(trackId)
                val gpxFile = File(activity.cacheDir, "$fileName.gpx").apply { if (!exists()) createNewFile() }
                FileWriter(gpxFile).use { GpxWriter(it).write(activity, trackId) }
                val zipFile = ZipHelper.zipCacheFiles(activity, trackId, gpxFile) ?: return false
                uploadZip(zipFile)
            } catch (e: Exception) {
                errorMessage = e.message ?: errorMessage
                false
            }
        }

        private fun uploadZip(zipFile: File): Boolean {
            val url = selectedBaseUrl() + "api/GisGeolocation/upload"
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", zipFile.name, RequestBody.create("application/zip".toMediaTypeOrNull(), zipFile))
                .build()
            val request = Request.Builder().url(url).post(body).build()
            val response: Response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                errorMessage = "خطا در آپلود: ${response.code}"
                return false
            }
            val exportValues = ContentValues().apply {
                put(TrackContentProvider.Schema.COL_EXPORT_DATE, Date().time)
                put(TrackContentProvider.Schema.COL_ACTIVE, TrackContentProvider.Schema.VAL_TRACK_INACTIVE)
            }
            activity.contentResolver.update(
                ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId),
                exportValues,
                null,
                null,
            )
            val dir = DataHelper.getTrackDirectory(trackId, activity)
            if (dir.exists()) FileSystemUtils.delete(dir, true)
            activity.contentResolver.delete(
                ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId),
                null,
                null,
            )
            clearMissionLocalData(trackId)
            return true
        }

        override fun onPostExecute(success: Boolean) {
            loadingDialog?.dismiss()
            loadingDialog = null
            if (success) {
                currentTrackId = TRACK_ID_NONE
                Toast.makeText(activity, "ماموریت با موفقیت بارگذاری شد", Toast.LENGTH_LONG).show()
                SupervisorMissionHelper.openWorkAreaSelection(activity)
                activity.finish()
            } else {
                Toast.makeText(activity, "خطا: $errorMessage", Toast.LENGTH_LONG).show()
            }
        }
    }

    data class MissionInfo(
        val organTitle: String,
        val contractTitle: String,
        val contractId: Int,
        val organId: Int,
        val visitDate: String,
        val districtLabel: String,
        val planningCount: Int,
        val systemCount: Int,
    )
}
