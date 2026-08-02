package Ir.co.tfs.farazaman.data.db;


import Ir.co.tfs.farazaman.R;
import Ir.co.tfs.farazaman.data.db.model.Track;
import Ir.co.tfs.farazaman.activity.TrackManager;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Adapter for track list in {@link TrackManager Track Manager}.
 * For each row's contents, see <tt>tracklist_item.xml</tt>.
 * 
 * @author Nicolas Guillaumin
 *
 */
public class TracklistAdapter extends CursorAdapter {

	public TracklistAdapter(Context context, Cursor c) {
		super(context, c);
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor) {
		bind(cursor, view, context);	
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup vg) {
		View view = LayoutInflater.from(vg.getContext()).inflate(R.layout.tracklist_item,
				vg, false);
		return view;
	}
	
	/**
	 * Do the binding between data and item view.
	 * 
	 * @param cursor
	 *				Cursor to pull data
	 * @param v
	 *				RelativeView representing one item
	 * @param context
	 *				Context, to get resources
	 * @return The relative view with data bound.
	 */
	private View bind(Cursor cursor, View v, Context context) {
		TextView vId = (TextView) v.findViewById(R.id.trackmgr_item_id);
		TextView vNameOrStartDate = (TextView) v.findViewById(R.id.trackmgr_item_nameordate);
		TextView vWps = (TextView) v.findViewById(R.id.trackmgr_item_wps);
		TextView vTps = (TextView) v.findViewById(R.id.trackmgr_item_tps);
		ImageView vStatus = (ImageView) v.findViewById(R.id.trackmgr_item_statusicon);
		ImageView vUploadStatus = (ImageView) v.findViewById(R.id.trackmgr_item_upload_statusicon);
		Button stopOrResume = (Button) v.findViewById(R.id.stop_or_resume);
		View line = (View) v.findViewById(R.id.line);
		Button end = (Button) v.findViewById(R.id.end_mission);

		// Bind id and build Track object first (needed for button logic)
		long trackId = cursor.getLong(cursor.getColumnIndex(TrackContentProvider.Schema.COL_ID));
		String strTrackId = Long.toString(trackId);
		vId.setText(strTrackId);

		// Build Track object for counts and name
		Track t = Track.build(trackId, cursor, context.getContentResolver(), false);
		int tpCount = t.getTpCount();
		
		// Bind WP count, TP count, name
		vTps.setText(Integer.toString(tpCount));
		vWps.setText(Integer.toString(t.getWpCount()));
		vNameOrStartDate.setText(t.getDisplayName());

		// Is track active ?
		int active = cursor.getInt(cursor.getColumnIndex(TrackContentProvider.Schema.COL_ACTIVE));
		if (TrackContentProvider.Schema.VAL_TRACK_ACTIVE == active) {
			// Yellow clock icon for Active
			vStatus.setImageResource(android.R.drawable.presence_away);
			vStatus.setVisibility(View.VISIBLE);
			stopOrResume.setText("توقف");
			stopOrResume.setBackgroundColor(context.getResources().getColor(R.color.stop)); // Set red background
			line.setBackgroundColor(context.getResources().getColor(R.color.stop)); // Set red background
		} else if (cursor.isNull(cursor.getColumnIndex(TrackContentProvider.Schema.COL_EXPORT_DATE))) {
			// Hide green circle icon: Track not yet exported
			vStatus.setVisibility(View.GONE);
			
			// Check if tracking has ever been started by looking at track points count
			if (tpCount == 0) {
				// Never started tracking - show "شروع"
				stopOrResume.setText("شروع");
			} else {
				// Started but currently paused - show "ادامه"
				stopOrResume.setText("ادامه");
			}
			
			stopOrResume.setBackgroundColor(context.getResources().getColor(R.color.brand_green)); // Set green background
			line.setBackgroundColor(context.getResources().getColor(R.color.brand_green)); // Set green background
		} else {
			// Show green circle icon (don't assume already visible with this drawable; may be a re-query)
			vStatus.setImageResource(android.R.drawable.presence_online);
			vStatus.setVisibility(View.VISIBLE);
		}
		
		// Upload status
		if (cursor.isNull(cursor.getColumnIndex(TrackContentProvider.Schema.COL_OSM_UPLOAD_DATE))) {
			vUploadStatus.setVisibility(View.GONE);
		}		
		else{
			vUploadStatus.setImageResource(android.R.drawable.stat_sys_upload_done);
			vUploadStatus.setVisibility(View.VISIBLE);
		}

		return v;
	}

}
