package Ir.co.tfs.farazaman.gpx;

import android.content.Context;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.util.Log;

import Ir.co.tfs.farazaman.OSMTracker;
import Ir.co.tfs.farazaman.data.db.DataHelper;
import Ir.co.tfs.farazaman.exception.ExportTrackException;
// import Ir.co.tfs.farazaman.util.PersianNumberConverter; // Not used, can be removed

import java.io.File;
import java.util.Date;

/**
 * Exports to a temporary file. Will not export associated
 * media, only the GPX file.
 *
 */
public abstract class ExportToTempFileTask extends ExportTrackTask {

	private static final String TAG = ExportToTempFileTask.class.getSimpleName();

	private final File tmpFile;
	private String filename;

	public ExportToTempFileTask(Context context, long trackId) {
		super(context, trackId);
		String desiredOutputFormat = PreferenceManager.getDefaultSharedPreferences(context).getString(
				OSMTracker.Preferences.KEY_OUTPUT_FILENAME,
				OSMTracker.Preferences.VAL_OUTPUT_FILENAME);

		try {
			DataHelper dataHelper = new DataHelper(context); // Instantiate DataHelper once
			String trackName = dataHelper.getTrackById(trackId).getName();

			long startDate = dataHelper.getTrackById(trackId).getTrackDate();
			String formattedTrackStartDate = DataHelper.FILENAME_FORMATTER.format(new Date(startDate));

			// Create temporary file
			String tmpFilename = super.formatGpxFilename(desiredOutputFormat, trackName, formattedTrackStartDate);
			StringBuilder englishtmpFilename = new StringBuilder();
			for (char c : tmpFilename.toCharArray()) {
				// Check if the character is a Persian digit (Unicode range U+06F0 to U+06F9)
				if (c >= '\u06F0' && c <= '\u06F9') {
					// Convert Persian digit to its English equivalent
					// The difference between the Unicode values of '0' and '۰' is constant
					englishtmpFilename.append((char) (c - '\u06F0' + '0'));
				} else {
					// Append other characters as they are (e.g., non-digit characters, English digits)
					englishtmpFilename.append(c);
				}
			}
			tmpFile = new File(context.getCacheDir(),englishtmpFilename + DataHelper.EXTENSION_GPX);
			Log.d(TAG, "Temporary file: "+ tmpFile.getAbsolutePath());
		} catch (Exception ioe) {
			Log.e(TAG, "Could not create temporary file", ioe);
			throw new IllegalStateException("Could not create temporary file", ioe);
		}
	}

	@Override
	protected File getExportDirectory(Date startDate) throws ExportTrackException {
		return tmpFile.getParentFile();
	}

	@Override
	public String buildGPXFilename(Cursor c, File parentDirectory) {
		filename = super.buildGPXFilename(c, parentDirectory);
		return tmpFile.getName();
	}

	@Override
	protected boolean exportMediaFiles() {
		return false;
	}

	@Override
	protected boolean updateExportDate() {
		return false;
	}

	public File getTmpFile() {
		return tmpFile;
	}

	public String getFilename() {
		return filename;
	}

	@Override
	protected void onPostExecute(Boolean success) {
		super.onPostExecute(success);
		// Pass the success status to executionCompleted
		executionCompleted(success);
	}

	// Modify the abstract method to accept a boolean parameter
	protected abstract void executionCompleted(boolean success);
}
