package Ir.co.tfs.farazaman.service.gps;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import Ir.co.tfs.farazaman.OSMTracker;
import Ir.co.tfs.farazaman.R;
import Ir.co.tfs.farazaman.activity.DisplayTrackMap;
import Ir.co.tfs.farazaman.activity.TrackLogger;
import Ir.co.tfs.farazaman.data.db.TrackContentProvider;
import Ir.co.tfs.farazaman.layout.GpsStatusRecordDisplay;

/**
 * Handles the bind to the GPS Logger service
 * 
 * @author Nicolas Guillaumin
 *
 */
public class GPSLoggerServiceConnectionDisplay implements ServiceConnection {

	/**
	 * Reference to TrackLogger activity
	 */
	private DisplayTrackMap activity;

	public GPSLoggerServiceConnectionDisplay(DisplayTrackMap tl) {
		activity = tl;
	}
	
	@Override
	public void onServiceDisconnected(ComponentName name) {
		Log.d("GPSLoggerServiceConnection", "Service disconnected");
//		activity.setEnabledActionButtons(false);
		activity.setGpsLogger(null);
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {

//		activity.setGpsLogger( ((GPSLogger.GPSLoggerBinder) service).getService());
//
//		// Update record status regarding of current tracking state
//		GpsStatusRecord gpsStatusRecord = (GpsStatusRecord) activity.findViewById(R.id.gpsStatus);
//		if (gpsStatusRecord != null) {
//			gpsStatusRecord.manageRecordingIndicator(activity.getGpsLogger().isTracking());
//		}
//
//		// If not already tracking, start tracking
//		if (!activity.getGpsLogger().isTracking()) {
//			activity.setEnabledActionButtons(false);
//			Intent intent = new Intent(OSMTracker.INTENT_START_TRACKING);
//			intent.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, activity.getCurrentTrackId());
//			intent.setPackage(activity.getPackageName());
//			activity.sendBroadcast(intent);
//		}
		Log.d("GPSLoggerServiceConnection", "Service connected, binding to GPSLogger");
		try {
			activity.setGpsLogger(((GPSLogger.GPSLoggerBinder) service).getService());
			Log.d("GPSLoggerServiceConnection", "gpsLogger set, isTracking: " + activity.getGpsLogger().isTracking());
		} catch (Exception e) {
			Log.e("GPSLoggerServiceConnection", "Error setting gpsLogger", e);
		}

		GpsStatusRecordDisplay gpsStatusRecord = (GpsStatusRecordDisplay) activity.findViewById(R.id.gpsStatus);
		if (gpsStatusRecord != null) {
			gpsStatusRecord.manageRecordingIndicator(activity.getGpsLogger().isTracking());
			Log.d("GPSLoggerServiceConnection", "Updated GpsStatusRecord, isTracking: " + activity.getGpsLogger().isTracking());
		} else {
			Log.w("GPSLoggerServiceConnection", "gpsStatusRecord is null");
		}

		// Check if GPS service should be disabled
		boolean disableGpsService = activity.getIntent().getBooleanExtra("disable_gps_service", false);
		
		if (!activity.getGpsLogger().isTracking() && !disableGpsService) {
			Log.d("GPSLoggerServiceConnection", "Not tracking and GPS service not disabled, sending INTENT_START_TRACKING");
//			activity.setEnabledActionButtons(false);
			Intent intent = new Intent(OSMTracker.INTENT_START_TRACKING);
			intent.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, activity.getCurrentTrackId());
			intent.setPackage(activity.getPackageName());
			activity.sendBroadcast(intent);
		} else if (disableGpsService) {
			Log.d("GPSLoggerServiceConnection", "GPS service disabled, not starting tracking");
		} else {
			Log.d("GPSLoggerServiceConnection", "Already tracking, checking GPS status");
			if (activity.getGpsLogger().isGpsEnabled()) {
//				activity.setEnabledActionButtons(true);
				Log.d("GPSLoggerServiceConnection", "Enabling buttons because GPS is enabled");
			}
		}
	}

}
