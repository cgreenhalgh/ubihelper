/**
 * 
 */
package uk.ac.horizon.ubihelper;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Log;

/**
 * @author cmg
 *
 */
public class MainPreferences extends PreferenceActivity {
	static final String TAG = "ubihelper-pref";
	static final String RUN_PREFERENCE = "run_service";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.mainpreferences);
	}

	/** preference change listener */
	private final OnSharedPreferenceChangeListener onRunChangeListener = 
			new OnSharedPreferenceChangeListener() {

				public void onSharedPreferenceChanged(SharedPreferences prefs,
						String key) {
					if (RUN_PREFERENCE.equals(key)) {
						boolean run_service = prefs.getBoolean(RUN_PREFERENCE, false);
						startStopService(run_service,"on preference change");
					}
				}

	};

	private void startStopService(boolean run_service, String info) {
		if (run_service) {
			Intent i = new Intent(this.getApplicationContext(), Service.class);
			ComponentName current = startService(i);
			Log.d(TAG,"Called startService ("+info+"");
		}
		else {
			Intent i = new Intent(this.getApplicationContext(), Service.class);
			boolean stopped = stopService(i);
			if (stopped)
				Log.d(TAG,"Stopped service ("+info+")");
		}
	}

	
	/** activity no longer in focus */
	@Override
	protected void onPause() {
		// tidy up?
		super.onPause();
		SharedPreferences prefs = this.getPreferenceManager().getSharedPreferences();
		prefs.unregisterOnSharedPreferenceChangeListener(onRunChangeListener);
	}

	/** becoming visible */
	@Override
	protected void onResume() {
		// check if service should be running...
		super.onResume();
		SharedPreferences prefs = this.getPreferenceManager().getSharedPreferences();
		boolean run_service = prefs.getBoolean(RUN_PREFERENCE, false);
		startStopService(run_service, "in onResume()");
		// register for changes to preference...
		prefs.registerOnSharedPreferenceChangeListener(onRunChangeListener);
	}

}
