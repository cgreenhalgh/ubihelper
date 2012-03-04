/**
 * 
 */
package uk.ac.horizon.ubihelper.service.channel;

import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;

/**
 * @author cmg
 *
 */
public class BluetoothDiscoveryChannel extends PollingChannel {
	private static final String KEY_TIME = "time";
	private static final String KEY_DEVICES = "devices";
	private static final String KEY_ADDRESS = "btaddress";
	private static final String KEY_CLASS = "btclass";
	private static final String KEY_NAME = "name";
	private static final String KEY_BOND = "bond";

	static String TAG = "ubihelper-btchan";
	private BluetoothAdapter bluetooth;
	private Service service;
	private HashMap<String,BluetoothDevice> devices = new HashMap<String,BluetoothDevice>();
	/**
	 * @param handler
	 * @param name
	 */
	public BluetoothDiscoveryChannel(Service service, Handler handler, String name) {
		super(handler, name);
		bluetooth = BluetoothAdapter.getDefaultAdapter();
		this.service = service;
		if (bluetooth==null)
			Log.w(TAG,"No BluetoothAdapter");
		if (!bluetooth.isEnabled()) {
			Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			service.sendBroadcast(i);
		}
		IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		filter.addAction(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
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
			if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
				handlePollComplete();
			}
			else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
				// turned off?
				if (!bluetooth.isDiscovering())
					synchronized (BluetoothDiscoveryChannel.this) {
						if (pollInProgress)
							pollComplete();
					}
			}
			else if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction()) || 
					BluetoothDevice.ACTION_NAME_CHANGED.equals(intent.getAction())) {
				try {
					BluetoothDevice device = intent.getExtras().getParcelable(BluetoothDevice.EXTRA_DEVICE);
					if (device!=null) {
						String address = device.getAddress();
						Log.d(TAG,"Found device "+address);
						synchronized (BluetoothDiscoveryChannel.this) {
							devices.put(address, device);
						}
					}
				}
				catch (Exception e) {
					Log.w(TAG,"Error getting device: "+e);
				}
			}
		}
	};

	@Override
	protected boolean startPoll() {
		if (bluetooth==null) {
			return false;
		}
		if (!bluetooth.isEnabled()) {
			Log.d(TAG,"startPoll: Bluetooth disabled");
			return false;
		}
		if (bluetooth.isDiscovering()) {
			Log.d(TAG,"startPoll: already isDiscovering");
			return true;
		}
		synchronized (this) {
			devices.clear();
		}
		if (!bluetooth.startDiscovery()) {
			Log.d(TAG,"startPoll: could not startDiscovery");
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
			value.put(KEY_DEVICES, ds);
			for (BluetoothDevice device : this.devices.values()) {
				JSONObject d = new JSONObject();
				d.put(KEY_ADDRESS, device.getAddress());
				BluetoothClass btclass = device.getBluetoothClass();
				if(btclass!=null)
					d.put(KEY_CLASS, btclass.getDeviceClass());
				String name = device.getName();
				if (name!=null)
					d.put(KEY_NAME, name);
				d.put(KEY_BOND, device.getBondState());
				ds.put(d);
			}
			onNewValue(value);
		}
		catch(JSONException e) {
			// shouldn't
		}
	}

}
