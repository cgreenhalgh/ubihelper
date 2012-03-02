/**
 * 
 */
package uk.ac.horizon.ubihelper.ui;

import uk.ac.horizon.ubihelper.service.BroadcastIntentSubscription;
import uk.ac.horizon.ubihelper.service.Service;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

/**
 * @author cmg
 *
 */
public abstract class ChannelViewActivity extends Activity {
	protected static final String TAG = "ubihelper-channelvalue";
	private BroadcastIntentSubscription subscription;
	protected String channelName;
	private Service service;
	protected double period = 1;

	@Override
	protected void onStart() {
		super.onStart();
		channelName = getIntent().getExtras().getString(BroadcastIntentSubscription.EXTRA_NAME);
		refresh(null);
		IntentFilter peerChangeFilter = new IntentFilter(BroadcastIntentSubscription.ACTION_CHANNEL_NEW_VALUE);
		registerReceiver(peerChangeListener, peerChangeFilter);
		Intent i = new Intent(this, Service.class);
		bindService(i, mServiceConnection, 0);
	}

	@Override
	protected void onStop() {
		super.onStop();
		unregisterReceiver(peerChangeListener);
		if (subscription!=null) {
			if (service!=null)
				service.unwatchChannel(subscription);
			subscription = null;
		}
		unbindService(mServiceConnection);
		service = null;
	}

	private BroadcastReceiver peerChangeListener = new BroadcastReceiver () {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (channelName!=null && channelName.equals(intent.getExtras().getString(BroadcastIntentSubscription.EXTRA_NAME)))
				refresh(intent);
		}		
	};

	/** monitor binding to service (used for preference update push) */
	private ServiceConnection mServiceConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName name, IBinder binder) {
			service = ((Service.LocalBinder)binder).getService();
			Log.d(TAG,"Service connected");
			if (channelName!=null) {
				subscription = service.watchChannel(channelName, period );
			}
		}

		public void onServiceDisconnected(ComponentName name) {
			service = null;
			subscription = null;
			Log.d(TAG,"Service disconnected");
		}		
	};

	protected abstract void refresh(Intent intent);

}
