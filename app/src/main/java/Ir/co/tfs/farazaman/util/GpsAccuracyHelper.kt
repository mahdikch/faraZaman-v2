package Ir.co.tfs.farazaman.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import Ir.co.tfs.farazaman.data.db.TrackContentProvider

object GpsAccuracyHelper {

    fun lastTrackPointAccuracy(context: Context, trackId: Long): Float? {
        if (trackId <= 0L) return null
        context.contentResolver.query(
            TrackContentProvider.trackPointsUri(trackId),
            arrayOf(TrackContentProvider.Schema.COL_ACCURACY),
            null,
            null,
            "${TrackContentProvider.Schema.COL_TIMESTAMP} DESC",
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val index = cursor.getColumnIndex(TrackContentProvider.Schema.COL_ACCURACY)
            if (index < 0 || cursor.isNull(index)) return null
            return cursor.getFloat(index).takeIf { it > 0f }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    fun currentFixAccuracy(context: Context): Float? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )
        return providers.mapNotNull { provider ->
            locationManager.getLastKnownLocation(provider)
        }
            .filter { it.hasAccuracy() && it.accuracy > 0f }
            .minByOrNull { it.accuracy }
            ?.accuracy
    }

    fun bestAvailableAccuracy(context: Context, trackId: Long): Float? {
        val candidates = listOfNotNull(
            currentFixAccuracy(context),
            lastTrackPointAccuracy(context, trackId),
        )
        return candidates.minOrNull()
    }

    fun resolveBuffer(context: Context, trackId: Long): Int {
        return RoadQueryBuffer.forAccuracy(bestAvailableAccuracy(context, trackId))
    }
}
