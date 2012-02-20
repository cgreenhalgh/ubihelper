/**
 * 
 */
package uk.ac.horizon.ubihelper;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

/**
 * @author cmg
 *
 */
public class MainPreferences extends PreferenceActivity {
	static final String TAG = "ubihelper-pref";
	static final String RUN_PREFERENCE = "run_service";
	static final String HTTP_PORT_PREFERENCE = "http_port";
	static final String HTTP_PATH_PREFERENCE = "http_path";
	private EditTextPreference httpPortPref;
	private EditTextPreference httpPathPref;
	private Service mService = null;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.mainpreferences);
        httpPortPref = (EditTextPreference)this.getPreferenceScreen().findPreference(HTTP_PORT_PREFERENCE);
        httpPortPref.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        httpPortPref.setSummary(httpPortPref.getText());
        httpPathPref = (EditTextPreference)this.getPreferenceScreen().findPreference(HTTP_PATH_PREFERENCE);
        httpPathPref.setSummary(httpPathPref.getText());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		 MenuInflater inflater = getMenuInflater();
		 inflater.inflate(R.menu.mainpreferencesmenu, menu);
		 return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.wifistatus_option:
		{
			Intent i  = new Intent(this, WifiStatusActivity.class); 
			startActivity(i);
			return true;
		}
		default:
			return super.onOptionsItemSelected(item);
		}
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
					else if (HTTP_PORT_PREFERENCE.equals(key)) {
						String value = prefs.getString(key, "undefined");
						Log.d(TAG,key+" changed to "+value);
						if (httpPortPref!=null)
							httpPortPref.setSummary(value);
						if (mService!=null)
							mService.sharedPreferenceChanged(prefs, key);
					}
					else if (HTTP_PATH_PREFERENCE.equals(key)) {
						String value = prefs.getString(key, "undefined");
						Log.d(TAG,key+" changed to "+value);
						if (httpPathPref!=null)
							httpPathPref.setSummary(value);
						if (mService!=null)
							mService.sharedPreferenceChanged(prefs, key);
					}
					else
					{
						Log.d(TAG,"Preference "+key+" changed...");
					}
				}

	};

	private Intent getServiceIntent() {		
		Intent i = new Intent(this.getApplicationContext(), Service.class);
		return i;
	}
	/** start/stop service */
	private void startStopService(boolean run_service, String info) {
		if (run_service) {
			Intent i = getServiceIntent();
			startService(i);
			Log.d(TAG,"Called startService ("+info+"");
		}
		else {
			Intent i = getServiceIntent();
			boolean stopped = stopService(i);
			if (stopped)
				Log.d(TAG,"Stopped service ("+info+")");
		}
	}

	/** monitor binding to service (used for preference update push) */
	private ServiceConnection mServiceConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName name, IBinder binder) {
			mService = ((Service.LocalBinder)binder).getService();
			Log.d(TAG,"Service connected");
		}

		public void onServiceDisconnected(ComponentName name) {
			mService = null;
			Log.d(TAG,"Service disconnected");
		}		
	};
	
	/** activity no longer in focus */
	@Override
	protected void onPause() {
		// tidy up?
		super.onPause();
		SharedPreferences prefs = this.getPreferenceManager().getSharedPreferences();
		prefs.unregisterOnSharedPreferenceChangeListener(onRunChangeListener);		
		unbindService(mServiceConnection);
		// clear mService?
		if (mService!=null) {
			Log.d(TAG,"Clearing mService");
			mService = null;
		}
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
		// service?
		Intent i = getServiceIntent();
		bindService(i, mServiceConnection, 0);
	}

}
