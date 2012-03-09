/**
 * Copyright (c) 2012 The University of Nottingham
 * 
 * This file is part of ubihelper
 *
 *  ubihelper is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ubihelper is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with ubihelper. If not, see <http://www.gnu.org/licenses/>.
 *  
 *  @author Chris Greenhalgh (cmg@cs.nott.ac.uk), The University of Nottingham
 */
package uk.ac.horizon.ubihelper.ui;

import uk.ac.horizon.ubihelper.R;
import uk.ac.horizon.ubihelper.R.id;
import uk.ac.horizon.ubihelper.R.menu;
import uk.ac.horizon.ubihelper.R.xml;
import uk.ac.horizon.ubihelper.channel.ChannelManager;
import uk.ac.horizon.ubihelper.service.Service;
import uk.ac.horizon.ubihelper.service.Service.LocalBinder;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * @author cmg
 *
 */
public class MainPreferences extends PreferenceActivity {
	static final String TAG = "ubihelper-pref";
	public static final String RUN_PREFERENCE = "run_service";
	public static final String HTTP_PORT_PREFERENCE = "http_port";
	public static final String HTTP_PATH_PREFERENCE = "http_path";
	public static final String WIFIDISC_PREFERENCE = "wifidisc";
	public static final String WIFIDISC_NAME_PREFERENCE = "wifidisc_name";
	private EditTextPreference httpPortPref;
	private EditTextPreference httpPathPref;
	private EditTextPreference wifiNamePref;
	private Service mService = null;
	private BluetoothAdapter bluetooth;
	private BluetoothReceiver bluetoothReciever = new BluetoothReceiver();
	
	public static final String ACTION_NAME_CHANGED = "uk.ac.horizon.ubihelper.action.NAME_CHANGED";
	public static final String EXTRA_NAME = "uk.ac.horizon.ubihelper.extra.NAME";
	
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
        wifiNamePref = (EditTextPreference)this.getPreferenceScreen().findPreference(WIFIDISC_NAME_PREFERENCE);
        if (wifiNamePref.getText()==null || wifiNamePref.getText().length()==0 || android.os.Build.MODEL.equals(wifiNamePref.getText())) {
            String deviceName = android.os.Build.MODEL;
            wifiNamePref.setSummary("Defaults to device model ("+deviceName+")");
            bluetooth = BluetoothAdapter.getDefaultAdapter();
            if (bluetooth!=null) {
	            String btname = bluetooth.getName();
	            if (btname!=null && !android.os.Build.MODEL.equals(btname)) {
	                wifiNamePref.setSummary("Set to bluetooth name ("+btname+")");
	            	deviceName = btname;
	            }
	            IntentFilter btfilter = new IntentFilter(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
	            btfilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
	            registerReceiver(bluetoothReciever, btfilter);
            }
            wifiNamePref.setText(deviceName);
        }
        else
        	wifiNamePref.setSummary(wifiNamePref.getText());
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (bluetooth!=null)
			unregisterReceiver(bluetoothReciever);
	}

	private class BluetoothReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
	        if (wifiNamePref.getText()==null || wifiNamePref.getText().length()==0 || android.os.Build.MODEL.equals(wifiNamePref.getText())) {
	        	String btname = bluetooth.getName();
	            if (btname!=null && !android.os.Build.MODEL.equals(btname)) {
	        		wifiNamePref.setSummary("Set to bluetooth name ("+btname+")");
	        		wifiNamePref.setText(btname);
	        	}
	        	else {
	                wifiNamePref.setSummary("Defaults to device model ("+android.os.Build.MODEL+")");
	        		wifiNamePref.setText(android.os.Build.MODEL);	        		
	        	}
	        }
		}
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
		case R.id.channels_option:
		{
			Intent i  = ChannelListActivity.getStartActivityIntent(this);
			startActivity(i);
			return true;
		}
		case R.id.about_option:
		{
			Intent i  = new Intent(this, AboutActivity.class); 
			startActivity(i);
			return true;
		}
		case R.id.test_option:
		{
			Intent i  = new Intent(this, TestActivity.class); 
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
					else if (WIFIDISC_PREFERENCE.equals(key)) {
						Boolean discoverable = prefs.getBoolean(key, false);
						Log.d(TAG,key+" changed to "+discoverable);
						if (discoverable)
							checkWifiEnabled();
						if (mService!=null)
							mService.sharedPreferenceChanged(prefs, key);
					}
					else if (WIFIDISC_NAME_PREFERENCE.equals(key)) {
						String value = prefs.getString(key, "undefined");
						Log.d(TAG,key+" changed to "+value);
						if (wifiNamePref!=null)
							wifiNamePref.setSummary(value);
						if (mService!=null)
							mService.sharedPreferenceChanged(prefs, key);
						broadcastNameChanged(value);
					}
					else
					{
						Log.d(TAG,"Preference "+key+" changed...");
						if (mService!=null)
							mService.sharedPreferenceChanged(prefs, key);
					}
				}

	};

	private Intent getServiceIntent() {		
		Intent i = new Intent(this.getApplicationContext(), Service.class);
		return i;
	}
	protected void broadcastNameChanged(String value) {
		Intent i = new Intent(ACTION_NAME_CHANGED);
		i.putExtra(EXTRA_NAME, value);
		sendBroadcast(i);
	}

	/** start/stop service */
	private void startStopService(boolean run_service, String info) {
		if (run_service) {
			Boolean discoverable = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(WIFIDISC_PREFERENCE, false);
			if (discoverable)
				checkWifiEnabled();
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

	private void checkWifiEnabled() {
		// called in main thread
		WifiManager wifi = (WifiManager)getSystemService(WIFI_SERVICE);
		if (!wifi.isWifiEnabled()) {
			boolean enabled = wifi.setWifiEnabled(true);
			Log.d(TAG,"Enabled wifi: "+enabled);
			if (!enabled) {
				Toast.makeText(this, "Sorry, could not enable Wifi", Toast.LENGTH_SHORT);
			}	
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
