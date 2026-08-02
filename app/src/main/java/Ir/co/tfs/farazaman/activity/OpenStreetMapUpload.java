package Ir.co.tfs.farazaman.activity;

import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import Ir.co.tfs.farazaman.R;
import Ir.co.tfs.farazaman.data.db.TrackContentProvider;
import Ir.co.tfs.farazaman.data.db.model.Track;
import Ir.co.tfs.farazaman.gpx.ExportToTempFileTask;
import Ir.co.tfs.farazaman.gpx.ZipHelper;
import Ir.co.tfs.farazaman.AppConstants;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OpenStreetMapUpload extends TrackDetailEditor {

	private static final String TAG = OpenStreetMapUpload.class.getSimpleName();
	private static final String UPLOAD_URL = AppConstants.BASE_URL + "api/GisGeolocation/upload";
	private static final String PREF_TOKEN_KEY = "ACCESS_TOKEN";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState, R.layout.osm_upload, getTrackId());
		fieldsMandatory = true;

		Button btnOk = findViewById(R.id.osm_upload_btn_ok);
		btnOk.setOnClickListener(v -> {
			if (save()) {
				startUpload();
			}
		});

		Button btnCancel = findViewById(R.id.osm_upload_btn_cancel);
		btnCancel.setOnClickListener(v -> finish());

		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
	}

	private long getTrackId() {
		if (getIntent().getExtras() != null && getIntent().getExtras().containsKey(TrackContentProvider.Schema.COL_TRACK_ID)) {
			return getIntent().getExtras().getLong(TrackContentProvider.Schema.COL_TRACK_ID);
		} else {
			throw new IllegalArgumentException("Missing Track ID");
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		Cursor cursor = managedQuery(
				ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, trackId),
				null, null, null, null);

		if (!cursor.moveToFirst()) {
			Toast.makeText(this, "Track ID not found.", Toast.LENGTH_SHORT).show();
			finish();
			return;
		}

		bindTrack(Track.build(trackId, cursor, getContentResolver(), false));
	}

	private void startUpload() {
		new ExportToTempFileTask(this, trackId) {
			@Override
			protected void executionCompleted(boolean success) {
				if (success) {
					File gpxFile = getTmpFile();
					if (gpxFile != null && gpxFile.exists() && gpxFile.canRead()) {
						Log.d(TAG, "GPX file exists and is readable: " + gpxFile.getAbsolutePath());

						// Create the ZIP file here
						File zipFile = ZipHelper.zipCacheFiles(OpenStreetMapUpload.this, trackId, gpxFile); // Pass trackId
						if (zipFile != null && zipFile.exists() && zipFile.canRead()) {
							Log.d(TAG, "ZIP file created: " + zipFile.getAbsolutePath());
							try {
								File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
								if (downloadDir != null && downloadDir.mkdirs()) { // مطمئن شوید دایرکتوری Downloads وجود دارد
									File destFile = new File(downloadDir, zipFile.getName());
									java.nio.file.Files.copy(zipFile.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
									Log.d(TAG, "ZIP file copied to: " + destFile.getAbsolutePath());
									runOnUiThread(() -> Toast.makeText(OpenStreetMapUpload.this, "ZIP file copied to Downloads: " + destFile.getName(), Toast.LENGTH_LONG).show());
								}
							} catch (IOException e) {
								Log.e(TAG, "Failed to copy ZIP file to Downloads", e);
								runOnUiThread(() -> Toast.makeText(OpenStreetMapUpload.this, "Failed to copy ZIP for debugging.", Toast.LENGTH_LONG).show());
							}
							uploadToCustomServer(zipFile); // Upload the ZIP file
						} else {
							Log.e(TAG, "ZIP file could not be created or is not readable.");
							runOnUiThread(() -> Toast.makeText(OpenStreetMapUpload.this, "Error: Failed to create ZIP for upload.", Toast.LENGTH_LONG).show());
						}

					} else {
						Log.e(TAG, "GPX file does not exist or is not readable: " + (gpxFile != null ? gpxFile.getAbsolutePath() : "null file"));
						runOnUiThread(() -> Toast.makeText(OpenStreetMapUpload.this, "Error: GPX file not found or accessible for upload.", Toast.LENGTH_LONG).show());
					}
				} else {
					Log.e(TAG, "GPX file export failed, upload aborted.");
				}
			}
		}.execute();
	}

	private void uploadToCustomServer(File file) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String token = prefs.getString(PREF_TOKEN_KEY, null);

		if (token == null) {
			Toast.makeText(this, "Access token not found.", Toast.LENGTH_SHORT).show();
			return;
		}

		OkHttpClient client = new OkHttpClient.Builder()
				.connectTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
				.readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
				.writeTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
				.build();

		// Ensure the MediaType is correct. The original error said it's a GPX file, but you're trying to upload it as "application/zip".
		// If it's a GPX file, use "application/gpx+xml" or "application/xml"
		// If you intend to zip it, then ZipHelper.zipCacheFiles should be called before this.
		RequestBody fileBody = RequestBody.create(file, MediaType.parse("application/zip")); // Keep this if uploading ZIP
//		RequestBody fileBody = RequestBody.create(file, MediaType.parse("application/gpx+xml")); // Or "application/xml" or "application/zip" if you intend to zip it.
		MultipartBody requestBody = new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart("file", file.getName(), fileBody)
				.build();

		Request request = new Request.Builder()
				.url(UPLOAD_URL)
				.addHeader("Accept", "application/json")
				.addHeader("Authorization", "Bearer "+token)
				.post(requestBody)
				.build();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				runOnUiThread(() -> Toast.makeText(OpenStreetMapUpload.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
				Log.e(TAG, "Upload failed", e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				String result = response.body() != null ? response.body().string() : "";
				runOnUiThread(() -> {
					if (response.isSuccessful()) {
						Toast.makeText(OpenStreetMapUpload.this, "Upload successful", Toast.LENGTH_SHORT).show();
						finish();
					} else {
						Toast.makeText(OpenStreetMapUpload.this, "Upload error: " + response.code() + "\n" + result, Toast.LENGTH_LONG).show();
					}
				});
			}
		});
	}
}
