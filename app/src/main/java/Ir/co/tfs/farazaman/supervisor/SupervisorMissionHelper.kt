package Ir.co.tfs.farazaman.supervisor

import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
import Ir.co.tfs.farazaman.activity.NewSupervisorActivity
import Ir.co.tfs.farazaman.activity.SupervisorWorkAreaSelectionActivity
import Ir.co.tfs.farazaman.data.db.DataHelper
import Ir.co.tfs.farazaman.data.db.TrackContentProvider

object SupervisorMissionHelper {

    fun hasExistingMission(context: Context): Boolean {
        val trackId = resolveSupervisorTrackId(context)
        if (trackId == SupervisorTrackController.TRACK_ID_NONE) return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString("mission_data_$trackId", null) != null
    }

    fun launchSupervisor(context: Context) {
        val target = if (hasExistingMission(context)) {
            NewSupervisorActivity::class.java
        } else {
            SupervisorWorkAreaSelectionActivity::class.java
        }
        context.startActivity(Intent(context, target))
    }

    fun openWorkAreaSelection(context: Context) {
        context.startActivity(Intent(context, SupervisorWorkAreaSelectionActivity::class.java))
    }

    fun resolveSupervisorTrackId(context: Context): Long {
        val activeId = DataHelper.getActiveTrackId(context.contentResolver, "supervisor")
        if (activeId != SupervisorTrackController.TRACK_ID_NONE) return activeId
        return findLatestSupervisorTrackId(context)
    }

    private fun findLatestSupervisorTrackId(context: Context): Long {
        val trackIdProjection = arrayOf(
            "${TrackContentProvider.Schema.TBL_TRACK}.${TrackContentProvider.Schema.COL_ID} as ${TrackContentProvider.Schema.COL_ID}",
        )
        val cursor = context.contentResolver.query(
            TrackContentProvider.CONTENT_URI_TRACK,
            trackIdProjection,
            "${TrackContentProvider.Schema.TBL_TRACK}.${TrackContentProvider.Schema.COL_ROLE} = ?",
            arrayOf("supervisor"),
            "${TrackContentProvider.Schema.TBL_TRACK}.${TrackContentProvider.Schema.COL_START_DATE} DESC",
        ) ?: return SupervisorTrackController.TRACK_ID_NONE
        cursor.use {
            if (it.moveToFirst()) {
                val col = it.getColumnIndex(TrackContentProvider.Schema.COL_ID)
                if (col >= 0) return it.getLong(col)
            }
        }
        return SupervisorTrackController.TRACK_ID_NONE
    }
}
