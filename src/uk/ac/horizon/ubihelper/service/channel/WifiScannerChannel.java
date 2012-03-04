/**
 * 
 */
package uk.ac.horizon.ubihelper.service.channel;

import java.util.HashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.util.Log;

/**
 * @author cmg
 *
 */
public class WifiScannerChannel extends PollingChannel {
	private static final String KEY_TIME = "time";
	private static final String KEY_NETWORKS = "networks";
	private static final String KEY_ADDRESS = "address";
	private static final String KEY_CAPABILITIES = "capabilities";
	private static final String KEY_NAME = "name";
	private static final String KEY_LEVEL = "level";

	static String TAG = "ubihelper-wifichan";
	private WifiManager wifi;
	private Service service;
	/**
	 * @param handler
	 * @param name
	 */
	public WifiScannerChannel(Service service, Handler handler, String name) {
		super(handler, name);
		wifi = (WifiManager)service.getSystemService(Service.WIFI_SERVICE);
		this.service = service;
		if (wifi==null)
			Log.w(TAG,"No WifiManager");
		IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		this.service.registerReceiver(receiver, filter);
	}
	
	@Override
	public synchronized void close() {
		super.close();
		service.unregisterReceiver(receiver);
	}

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		
		@Override
		public void onReceive(Context context, Intent intent) {
			if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
				handlePollComplete();
			}
			else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
				// turned off?
				if (!wifi.isWifiEnabled())
					synchronized (WifiScannerChannel.this) {
						if (pollInProgress)
							pollComplete();
					}
			}
		}
	};

	@Override
	protected boolean startPoll() {
		if (wifi==null) {
			return false;
		}
		if (!wifi.isWifiEnabled()) {
			Log.d(TAG,"startPoll: wifi disabled");
			return false;
		}
		if (!wifi.startScan()) {
			Log.d(TAG,"startPoll: could not startScan");
			return false;
		}
		return true;
	}

	protected synchronized void handlePollComplete() {
		if (pollInProgress)
			pollComplete();
		try {
			JSONObject value = new JSONObject();
			value.put(KEY_TIME, System.currentTimeMillis());
			JSONArray ds = new JSONArray();
			value.put(KEY_NETWORKS, ds);
			List<ScanResult> results = wifi.getScanResults();
			for (ScanResult result : results) {
				JSONObject d = new JSONObject();
				if (result.BSSID!=null)
					d.put(KEY_ADDRESS, result.BSSID);
				if (result.SSID!=null)
					d.put(KEY_NAME, result.SSID);
				if (result.capabilities!=null)
					d.put(KEY_CAPABILITIES, result.capabilities);
				d.put(KEY_LEVEL, result.level);
				ds.put(d);
			}
			onNewValue(value);
		}
		catch(JSONException e) {
			// shouldn't
		}
	}

}
