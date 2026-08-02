package Ir.co.tfs.farazaman.listener;

import Ir.co.tfs.farazaman.activity.TrackLogger;

import android.view.View;
import android.view.View.OnClickListener;

/**
 * Manages voice recording.
 * 
 * @author Nicolas Guillaumin
 * 
 */
public class VoiceRecOnClickListener implements OnClickListener{

	/**
	 * Parent activity
	 */
	private TrackLogger tl;
	
	public VoiceRecOnClickListener(TrackLogger trackLogger) {
		tl = trackLogger;
	}

	@Override
	public void onClick(View v) {
		tl.showDialog(TrackLogger.DIALOG_VOICE_RECORDING);
	}

}
