/**
 * 
 */
package uk.ac.horizon.ubihelper;

import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * @author cmg
 *
 */
public class Service extends android.app.Service {
	static final String TAG = "ubihelper-svc";

	// local binder for this class
	private final IBinder mBinder = new LocalBinder();
	
	@Override
	public void onCreate() {
		// One-time set-up...
		Log.d(TAG,"onCreate()");
		// TODO
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		// Final resource clean-up
		Log.d(TAG,"onDestroy()");
		// TODO
		super.onDestroy();
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
}
