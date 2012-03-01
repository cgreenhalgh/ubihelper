/**
 * 
 */
package uk.ac.horizon.ubihelper.service;

import java.util.LinkedList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import uk.ac.horizon.ubihelper.R;
import uk.ac.horizon.ubihelper.R.drawable;
import uk.ac.horizon.ubihelper.R.string;
import uk.ac.horizon.ubihelper.channel.NamedChannel;
import uk.ac.horizon.ubihelper.httpserver.HttpContinuation;
import uk.ac.horizon.ubihelper.httpserver.HttpError;
import uk.ac.horizon.ubihelper.httpserver.HttpListener;
import uk.ac.horizon.ubihelper.ui.MainPreferences;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * @author cmg
 *
 */
public class Service extends android.app.Service {
	static final String TAG = "ubihelper-svc";

	// local binder for this class
	private final IBinder mBinder = new LocalBinder();
	/** notification manager */
	private static final int RUNNING_ID = 1;
	private HttpListener httpListener = null;
	private static final int DEFAULT_PORT = 8080;
	private static final String DEFAULT_PATH = "/ubihelper";
	private int httpPort;
	private boolean wifiDiscoverable;
	private WifiDiscoveryManager wifiDiscoveryManager;
	private Handler mHandler;
	private LinkedList<NamedChannel> channels = new LinkedList<NamedChannel>();
	private PeerManager peerManager;
	private BluetoothAdapter bluetooth;
	private String btmac;
	private TelephonyManager telephony;
	private String imei;
	
	@Override
	public void onCreate() {
		// One-time set-up...
		Log.d(TAG,"onCreate()");		
		// TODO
		super.onCreate();
		// handler for requests
		mHandler = new Handler();
		// create taskbar notification
		int icon = R.drawable.notification_icon;
		CharSequence tickerText = getText(R.string.notification_start_message);
		long when = System.currentTimeMillis();

		Notification notification = new Notification(icon, tickerText, when);
		
		Context context = this;
		CharSequence contentTitle = getText(R.string.notification_title);
		CharSequence contentText = getText(R.string.notification_description);
		Intent notificationIntent = new Intent(this, MainPreferences.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

		startForeground(RUNNING_ID, notification);	
		
		// sensors
		if (!isEmulator()) {
			Log.d(TAG,"Create sensor channels...");
			SensorChannel magnetic = new SensorChannel("magnetic", this, Sensor.TYPE_MAGNETIC_FIELD);
			channels.add(magnetic);
			SensorChannel accelerometer = new SensorChannel("accelerometer", this, Sensor.TYPE_ACCELEROMETER);
			channels.add(accelerometer);
		}
		Log.d(TAG,"Create http server...");
		
		// http server
		httpPort = getPort();

		httpListener = new HttpListener(this, httpPort);
		httpListener.start();
		
		// peer communication
		peerManager = new PeerManager(this);
		int serverPort = peerManager.getServerPort();
		
		// wifi discovery
		wifiDiscoveryManager = new WifiDiscoveryManager(this);
		wifiDiscoverable = getWifiDiscoverable();
		wifiDiscoveryManager.setServerPort(serverPort);
		wifiDiscoveryManager.setEnabled(wifiDiscoverable);
		
		bluetooth = BluetoothAdapter.getDefaultAdapter();
		if (bluetooth!=null)
			btmac = bluetooth.getAddress();
		
		telephony = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
		if (telephony!=null)
			imei = telephony.getDeviceId();
		
		Log.d(TAG,"onCreate() finished");
	}
	public static boolean isEmulator() {
		String model = android.os.Build.MODEL;
		Log.d(TAG,"Model: "+model);
		if ("sdk".equals(model) || "google_sdk".equals(model))
			return true;
		return false;
	}
	private int getPort() {
		int port = DEFAULT_PORT;
		String sport = PreferenceManager.getDefaultSharedPreferences(this).getString(MainPreferences.HTTP_PORT_PREFERENCE, null);
		if (sport!=null)
			try {
				port = Integer.parseInt(sport);
			}
			catch (NumberFormatException nfe) {
				Log.e(TAG,"Port value not a number: "+sport);
			}
		return port;
	}
	private String getPath() {
		String path = PreferenceManager.getDefaultSharedPreferences(this).getString(MainPreferences.HTTP_PATH_PREFERENCE, DEFAULT_PATH);
		return path;
	}
	private boolean getWifiDiscoverable() {
		boolean wifidisc = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(MainPreferences.WIFIDISC_PREFERENCE, false);
		return wifidisc;
	}
	public String getDeviceName() {
		String name = PreferenceManager.getDefaultSharedPreferences(this).getString(MainPreferences.WIFIDISC_NAME_PREFERENCE, null);
		// defaults...
		if (name==null) {
			BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
			if (bluetooth!=null) 
				name = bluetooth.getName();
			if (name==null)
				name = android.os.Build.MODEL;
		}
		return name;
	}
	public String getBtMac() {
		return btmac;
	}
	public String getImei() {
		return imei;
	}
	@Override
	public void onDestroy() {
		// Final resource clean-up
		Log.d(TAG,"onDestroy()");
		// TODO
		super.onDestroy();
		// tidy up notification
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancel(RUNNING_ID);	
		if (peerManager!=null)
			peerManager.close();
		if (httpListener!=null)
			httpListener.close();
		if (wifiDiscoveryManager!=null)
			wifiDiscoveryManager.close();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// Started... (by startService())
		Log.d(TAG,"onStartCommand()");
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public IBinder onBind(Intent arg0) {
		// Bound to (by bindService); return an IBinder for interaction with the service
		// For now we will only do this from within-process, so no AIDL, just some
		// methods
		Log.d(TAG,"onBind()");
		return mBinder;
	}

	/** Binder subclass (inner class) with methods for local interaction with service */
	public class LocalBinder extends android.os.Binder {
		// local methods... direct access to service
		public Service getService() {
			return Service.this;
		}
	}
	
	/** public API - preference changed */
	public void sharedPreferenceChanged(SharedPreferences prefs,
			String key) {
		Log.d(TAG, "onSharedPreferenceChanged("+key+")");
		if (MainPreferences.HTTP_PORT_PREFERENCE.equals(key)) {
			// update port
			httpPort = getPort();
			if (httpListener!=null)
				httpListener.setPort(httpPort);
		}
		else if (MainPreferences.WIFIDISC_PREFERENCE.equals(key)) {
			wifiDiscoverable = getWifiDiscoverable();
			wifiDiscoveryManager.setEnabled(wifiDiscoverable);
		}
	}
	/** public API - get PeerManager */
	public PeerManager getPeerManager() {
		return peerManager;
	}
	
	/** post request from another thread */
	public boolean postRequest(final String path, final String body, final HttpContinuation continuation) {
		return mHandler.post(new Runnable() {
			public void run() {
				String response = null;
				int status = 200;
				String message = "OK";
				try {
					response = handleRequest(path, body);
				}
				catch (HttpError he) {
					status = he.getStatus();
					message = he.getMessage();
				}
				catch (Exception e) {
					status = 500;
					message = "Internal error: "+e.getMessage();
				}
				try {
					if (continuation!=null)
						continuation.done(status, message, response);
				}
				catch (Exception e) {
					Log.d(TAG,"Calling continuation: "+e.getMessage());
				}
			}
		});
	}	
	boolean postTask(final Runnable task) {
		return mHandler.post(task);
	}
	
	private String handleRequest(String path, String body) throws HttpError {
		Log.d(TAG,"handleRequest "+path+" "+body);
		String myPath = getPath();
		if (!myPath.equals(path))
			throw HttpError.badRequest("Incorrect path ("+path+")");
		JSONArray response = new JSONArray();
		try {
			JSONArray request = (JSONArray)new JSONTokener(body).nextValue();
			// TODO
			for (int i=0; i<request.length(); i++) {
				JSONObject req = request.getJSONObject(i);
				String name = req.getString("name");
				// defaults?!
				double period = req.has("period") ? req.getDouble("period") : 1;
				int count = req.has("count") ? req.getInt("count") : 1;
				int timeout = req.has("timeout") ? req.getInt("timeout") : 30;
				
				JSONObject resp = new JSONObject();
				resp.put("name", name);
				
				NamedChannel nc = null;
				for (int ci=0; nc==null && ci<channels.size(); ci++) {
					if (channels.get(i).getName().equals(name))
						nc = channels.get(i);
				}
				if (nc!=null) {
					JSONArray values = new JSONArray();
					resp.put("values", values);

					nc.updateConfiguration(count, period, timeout);
					LinkedList<JSONObject> vs = nc.getValues();
					for (JSONObject v : vs)
						values.put(v);
							
				} else {
					Log.d(TAG,"Unknown channel "+name);
				}
				response.put(resp);
			}
		} catch (JSONException e) {
			throw HttpError.badRequest("Requst not well-formed JSON");
		}
		return response.toString();
	}
	public String getDeviceId() {
		WifiManager wifi = (WifiManager)getSystemService(WIFI_SERVICE);
		if (wifi!=null) {
			WifiInfo wi = wifi.getConnectionInfo();
			if (wi!=null) {
				return wi.getMacAddress();
			}
		}
		Log.w(TAG,"Could not get device ID");
		return "UNKNOWNID";
	}	
}