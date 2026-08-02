package Ir.co.tfs.farazaman.service.gps;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import Ir.co.tfs.farazaman.OSMTracker;
import Ir.co.tfs.farazaman.R;
import Ir.co.tfs.farazaman.activity.DisplayTrackMap;
import Ir.co.tfs.farazaman.data.db.DataHelper;
import Ir.co.tfs.farazaman.data.db.TrackContentProvider;
import Ir.co.tfs.farazaman.listener.PressureListener;
import Ir.co.tfs.farazaman.listener.SensorListener;

public class GPSLogger extends Service implements LocationListener {

	private static final String TAG = GPSLogger.class.getSimpleName();
	private DataHelper dataHelper;
	private boolean isTracking = false;
	private boolean isGpsEnabled = false;

	private static final int NOTIFICATION_ID = 1;
	private static final String CHANNEL_ID = "GPSLogger_Channel";

	private Location lastLoggedLocation;
	private Location mostRecentLocation;

	private LocationManager lmgr_gps;
	private LocationManager lmgr_network;

	private long currentTrackId = -1;
	private long gpsLoggingMinDistance;

	private static final float WALKING_MAX_ACCEPTABLE_ACCURACY = 30.0f;
	private static final float DRIVING_MAX_ACCEPTABLE_ACCURACY = 50.0f;
	private static final float WALKING_SPEED_THRESHOLD_MS = 3.0f;

	private final Handler guaranteedLogHandler = new Handler(Looper.getMainLooper());
	private static final long GUARANTEED_LOG_INTERVAL_MS = 10000;

	private final Runnable guaranteedLogRunnable = () -> {
		if (!isTracking) return;
		if (mostRecentLocation != null) {
			logPoint(mostRecentLocation, "Timer Heartbeat");
		} else {
			guaranteedLogHandler.postDelayed(this.guaranteedLogRunnable, GUARANTEED_LOG_INTERVAL_MS);
		}
	};

	private final SensorListener sensorListener = new SensorListener();
	private final PressureListener pressureListener = new PressureListener();

	private final BroadcastReceiver stopReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (OSMTracker.INTENT_STOP_TRACKING.equals(intent.getAction())) {
				stopTrackingAndSave();
			}
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();
		dataHelper = new DataHelper(this);
		gpsLoggingMinDistance = Long.parseLong(PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext()).getString(
				OSMTracker.Preferences.KEY_GPS_LOGGING_MIN_DISTANCE, OSMTracker.Preferences.VAL_GPS_LOGGING_MIN_DISTANCE));

		// Receiver فقط برای دستور توقف
		IntentFilter filter = new IntentFilter(OSMTracker.INTENT_STOP_TRACKING);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
		} else {
			registerReceiver(stopReceiver, filter);
		}

		lmgr_gps = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		lmgr_network = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			lmgr_gps.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);
			lmgr_network.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1, this);
		}
		sensorListener.register(this);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		createNotificationChannel();

		if (intent != null && OSMTracker.INTENT_START_TRACKING.equals(intent.getAction())) {
			long trackId = intent.getLongExtra(TrackContentProvider.Schema.COL_TRACK_ID, -1);
			if (trackId != -1) {
				// فقط در صورتی شروع کن که در حال ردیابی همین ماموریت نباشی
				if (!isTracking || currentTrackId != trackId) {
					startTracking(trackId);
				}
			}
		}

		startForeground(NOTIFICATION_ID, getNotification(currentTrackId));
		return Service.START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		guaranteedLogHandler.removeCallbacks(guaranteedLogRunnable);
		lmgr_gps.removeUpdates(this);
		unregisterReceiver(stopReceiver);
		sensorListener.unregister();
	}

	private void startTracking(long trackId) {
		currentTrackId = trackId;
		isTracking = true;
		lastLoggedLocation = null;
		mostRecentLocation = null;

		Log.v(TAG, "Official start of tracking for track #" + trackId);
		getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, getNotification(trackId));
		guaranteedLogHandler.postDelayed(guaranteedLogRunnable, GUARANTEED_LOG_INTERVAL_MS);
	}

	private void stopTrackingAndSave() {
		if (!isTracking) return; // گارد برای جلوگیری از فراخوانی چندباره

		isTracking = false;
		guaranteedLogHandler.removeCallbacks(guaranteedLogRunnable);

		if (currentTrackId != -1) {
			dataHelper.stopTracking(currentTrackId);
			currentTrackId = -1;
		}

		Log.d(TAG, "Stopping foreground service and removing notification.");
		stopForeground(true);
		stopSelf();
	}

	@Override
	public void onLocationChanged(Location location) {
		if (location == null || !isTracking) return;
		isGpsEnabled = true;
		mostRecentLocation = location;

		if (isLocationAcceptable(location)) {
			if (lastLoggedLocation == null) {
				logPoint(location, "First Acceptable");
				return;
			}
			float distance = location.distanceTo(lastLoggedLocation);
			if (distance >= gpsLoggingMinDistance) {
				logPoint(location, "Movement");
			}
		}
	}

	private void logPoint(Location location, String reason) {
		if (!isTracking || location == null) return;

		guaranteedLogHandler.removeCallbacks(guaranteedLogRunnable);

		dataHelper.track(currentTrackId, location, sensorListener.getAzimuth(), sensorListener.getAccuracy(), pressureListener.getPressure());
		lastLoggedLocation = location;

		Log.d(TAG, "Point logged. Reason: [" + reason + "]. Accuracy: " + location.getAccuracy() + "m");
		guaranteedLogHandler.postDelayed(guaranteedLogRunnable, GUARANTEED_LOG_INTERVAL_MS);
	}

	private boolean isLocationAcceptable(Location location) {
		if (location == null) return false;
		float maxAccuracy = location.getSpeed() < WALKING_SPEED_THRESHOLD_MS ? WALKING_MAX_ACCEPTABLE_ACCURACY : DRIVING_MAX_ACCEPTABLE_ACCURACY;
		return location.getAccuracy() <= maxAccuracy;
	}

	public class GPSLoggerBinder extends Binder {
		public GPSLogger getService() { return GPSLogger.this; }
	}
	private final IBinder binder = new GPSLoggerBinder();
	@Override
	public IBinder onBind(Intent intent) { return binder; }

	private Notification getNotification(long trackId) {
		Intent notificationIntent = new Intent(this, DisplayTrackMap.class);
		notificationIntent.putExtra(TrackContentProvider.Schema.COL_TRACK_ID, trackId);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
		return new NotificationCompat.Builder(this, CHANNEL_ID)
				.setSmallIcon(R.drawable.ic_stat_track)
				.setContentTitle(getResources().getString(R.string.notification_title))
				.setContentText(getResources().getString(R.string.notification_text))
				.setPriority(NotificationCompat.PRIORITY_DEFAULT)
				.setContentIntent(pendingIntent)
				.build();
	}

	private void createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "GPS Logger", NotificationManager.IMPORTANCE_LOW);
			getSystemService(NotificationManager.class).createNotificationChannel(channel);
		}
	}

	@Override
	public void onProviderDisabled(String p) { isGpsEnabled = false; }
	@Override
	public void onProviderEnabled(String p) { isGpsEnabled = true; }
	@Override
	public void onStatusChanged(String p, int s, Bundle e) {}
	public boolean isTracking() { return isTracking; }
	public boolean isGpsEnabled() {
		return this.isGpsEnabled;
	}

	private Location lastValidLocation;

	private final LocationListener locationListener = new LocationListener() {
		@Override
		public void onLocationChanged(@NonNull Location location) {
			// فیلتر اول: دقت
			if (location.getAccuracy() > 25) {
				Log.d("GPSLogger", "Low accuracy, skipping: " + location.getAccuracy());
				return;
			}

			// اگر موقعیت قبلی وجود داره:
			if (lastValidLocation != null) {
				float distance = location.distanceTo(lastValidLocation); // فاصله با نقطه قبلی
				float speed = location.hasSpeed() ? location.getSpeed() : distance / ((location.getTime() - lastValidLocation.getTime()) / 1000f);

				// فیلتر دوم: سرعت غیرواقعی
				if (speed > 50) { // بیش از 50 m/s = احتمالاً نقطه پرت
					Log.d("GPSLogger", "Unrealistic speed, skipping: " + speed);
					return;
				}

				// فیلتر سوم: فاصله زیاد در زمان کوتاه
				if (distance > 100) { // بیش از 100 متر بین دو نقطه = مشکوک
					Log.d("GPSLogger", "Large jump in distance, skipping: " + distance);
					return;
				}
			}

			// در اینجا، موقعیت معتبر است
			lastValidLocation = location;

			// TODO: ذخیره‌سازی یا ارسال موقعیت
			Log.d("GPSLogger", "Valid location: " + location.getLatitude() + ", " + location.getLongitude());
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {}

		@Override
		public void onProviderEnabled(@NonNull String provider) {}

		@Override
		public void onProviderDisabled(@NonNull String provider) {}
	};

}