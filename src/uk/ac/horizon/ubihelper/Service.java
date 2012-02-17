/**
 * 
 */
package uk.ac.horizon.ubihelper;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
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
	private static final String DEFAULT_PATH = "ubihelper";
	private int httpPort;
	private String httpPath;
	
	@Override
	public void onCreate() {
		// One-time set-up...
		Log.d(TAG,"onCreate()");
		// TODO
		super.onCreate();
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
		
		httpPort = getPort();
		httpPath = getPath();

		httpListener = new HttpListener(this, httpPort);
		httpListener.start();
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

	@Override
	public void onDestroy() {
		// Final resource clean-up
		Log.d(TAG,"onDestroy()");
		// TODO
		super.onDestroy();
		// tidy up notification
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancel(RUNNING_ID);	
		if (httpListener!=null)
			httpListener.close();
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
		Service getService() {
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
	}
}
