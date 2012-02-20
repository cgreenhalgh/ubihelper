/**
 * 
 */
package uk.ac.horizon.ubihelper;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

/**
 * @author cmg
 *
 */
public class WifiStatusActivity extends Activity {
	private static final String TAG = "ubihelper-wifistatus";
	
	private BroadcastReceiver updateReceiver = new UpdateReceiver();
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.wifistatus);
	}

	@Override
	protected void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		registerReceiver(updateReceiver, filter);
		updateView();
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(updateReceiver);
	}

	/** broadcast receiver */
	private class UpdateReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			// strangely, doesn't seem to be called for Disabling -> Disabled
			// Is called for Enabling -> Enabled, for change (new) SSID, and 
			// change (allocate) IP. Ah that might be NETWORK_STATE_CHANGED_ACTION
			// vs WIFI_STATE_CHANGED_ACTION
			Log.d(TAG,"Received broadcast "+intent.getAction());
			WifiStatusActivity.this.runOnUiThread(new Runnable() {
				public void run() {
					updateView();
				}
			});
		}		
	}
	
	private void updateView() {
		WifiManager wifi = (WifiManager)getSystemService(WIFI_SERVICE);
		TextView wifi_status_text = (TextView)findViewById(R.id.wifi_status_text);
		int state = wifi.getWifiState();
		switch(state) {
		case WifiManager.WIFI_STATE_ENABLED:
			wifi_status_text.setText("Enabled");
			break;
		case WifiManager.WIFI_STATE_ENABLING:
			wifi_status_text.setText("Enabling");
			break;
		case WifiManager.WIFI_STATE_DISABLED:
			wifi_status_text.setText("Disabled");
			break;
		case WifiManager.WIFI_STATE_DISABLING:
			wifi_status_text.setText("Disabling");
			break;
		case WifiManager.WIFI_STATE_UNKNOWN:
			wifi_status_text.setText("Unknown");
			break;
		default:
			wifi_status_text.setText("Unknown ("+state+")");				
		}
		WifiInfo ci = wifi.getConnectionInfo();
		// this seems to be an empty string if disabled (inc. on emulator)
		String ssid = ci!=null ? ci.getSSID() : "-";
		int ip = ci!=null ? ci.getIpAddress() : 0;
		// this seems to be avabile in usual xx:xx:xx:xx:xx:xx format on real device
		// even with wifi disabled, but not on emulator
		String mac = ci!=null ? ci.getMacAddress() : "-";
		
		((TextView)findViewById(R.id.wifi_ssid_text)).setText(ssid);
		((TextView)findViewById(R.id.wifi_ip_text)).setText(ip2string(ip));
		((TextView)findViewById(R.id.wifi_mac_text)).setText(mac);
		
		// this information is cached and returned on real device even 
		// when no longer valid, e.g. if wifi now disabled.
		DhcpInfo di = wifi.getDhcpInfo();
		int ip2 = di!=null ? di.ipAddress : 0;
		int netmask = di!=null ? di.netmask : 0;
		int gateway = di!=null ? di.gateway : 0;

		((TextView)findViewById(R.id.wifi_ip2_text)).setText(ip2string(ip2));
		((TextView)findViewById(R.id.wifi_netmask_text)).setText(ip2string(netmask));
		((TextView)findViewById(R.id.wifi_gateway_text)).setText(ip2string(gateway));
	}

	public static String ip2string(int ip) {
		// NB at least on my samsung google s the high-byte is in the low bits
		StringBuilder sb = new StringBuilder();
		sb.append(Integer.toString((ip) & 0xff));
		sb.append(".");
		sb.append(Integer.toString((ip >> 8) & 0xff));
		sb.append(".");
		sb.append(Integer.toString((ip >> 16) & 0xff));
		sb.append(".");
		sb.append(Integer.toString((ip >> 24) & 0xff));
		return sb.toString();
	}
}
