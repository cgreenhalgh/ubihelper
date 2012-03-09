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
