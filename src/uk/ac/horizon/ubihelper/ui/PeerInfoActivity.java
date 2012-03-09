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

import java.util.Date;

import uk.ac.horizon.ubihelper.R;
import uk.ac.horizon.ubihelper.R.id;
import uk.ac.horizon.ubihelper.R.layout;
import uk.ac.horizon.ubihelper.protocol.PeerInfo;
import uk.ac.horizon.ubihelper.service.PeerManager;
import uk.ac.horizon.ubihelper.service.Service;
import uk.ac.horizon.ubihelper.service.PeerManager.PeerRequestInfo;
import uk.ac.horizon.ubihelper.service.PeerManager.PeerRequestState;
import uk.ac.horizon.ubihelper.service.PeerManager.SearchInfo;
import uk.ac.horizon.ubihelper.service.Service.LocalBinder;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

/**
 * @author cmg
 *
 */
public class PeerInfoActivity extends Activity {
	static final String TAG = "ubihelper-peerinfo";
	
	private PeerManager peerManager;
	private PeerInfo peerInfo;
	private TextView peerInfoName, peerInfoIp, peerInfoIpTimestamp, peerInfoId, peerInfoTrusted;
	private CheckBox peerInfoEnabled;
	
	public static Intent getStartActivityIntent(Context context, PeerInfo pi) {
		return getStartActivityIntent(context, pi.id);
	}
	public static Intent getStartActivityIntent(Context context, String id) {
		Intent i = new Intent(context, PeerInfoActivity.class);
		i.putExtra(PeerManager.EXTRA_ID, id);
		return i;
	}

	
	/** monitor binding to service (used for preference update push) */
	private ServiceConnection mServiceConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName name, IBinder binder) {
			peerManager = ((Service.LocalBinder)binder).getService().getPeerManager();
			Log.d(TAG,"Service connected");
			refresh(null);
		}

		public void onServiceDisconnected(ComponentName name) {
			peerManager = null;
			Log.d(TAG,"Service disconnected");
		}		
	};
	

	private void refresh(Intent intent) {
		//Log.d(TAG,"refresh start");
		// this routine blocks when called from ServiceConnection, presumably because of the call to getPeer
		boolean ok = false;
		// fallback values
		if (getIntent()!=null) {
			String id = getIntent().getExtras().getString(PeerManager.EXTRA_ID);
			peerInfoId.setText(id);
			String name = getIntent().getExtras().getString(PeerManager.EXTRA_NAME);
			if (name!=null)
				peerInfoName.setText(name);
		}
		if (peerManager!=null && getIntent()!=null) {
			//Log.d(TAG,"getPeer...");
			String id = getIntent().getExtras().getString(PeerManager.EXTRA_ID);
			// this call blocks when called from ServiceConnection, presumably because of the lock on getPeer
			peerInfo = peerManager.getPeer(id);
			//Log.d(TAG,"getPeer done");
			if (peerInfo!=null) {
				peerInfoName.setText(peerInfo.name);
				peerInfoIp.setText(peerInfo.ip);
				peerInfoIpTimestamp.setText(new Date(peerInfo.ipTimestamp).toString());
				peerInfoEnabled.setChecked(peerInfo.enabled);
				peerInfoTrusted.setText(peerInfo.trusted ? "Yes" : "No");
				ok = true;
			}
			else if (intent!=null) {
				//  ?
				ok = true;
			}
		}
		if (!ok) {
			//peerInfoName.setText("?");
			//peerInfoSourceip.setText("?");
			peerInfoEnabled.setText("?");
			peerInfoIp.setText("?");			
			peerInfoIpTimestamp.setText("?");			
		}
		Log.d(TAG,"refresh complete");
	}

	private BroadcastReceiver peerChangeListener = new BroadcastReceiver () {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (peerInfo!=null && peerInfo.id.equals(intent.getExtras().getString(PeerManager.EXTRA_ID)))
				refresh(intent);
		}		
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.peer_info);
		peerInfoName = (TextView)findViewById(R.id.peer_info_name);
		peerInfoId = (TextView)findViewById(R.id.peer_info_id);
		peerInfoIp = (TextView)findViewById(R.id.peer_info_ip);
		peerInfoIpTimestamp = (TextView)findViewById(R.id.peer_info_ip_timetamp);
		peerInfoTrusted = (TextView)findViewById(R.id.peer_info_trusted);
		peerInfoEnabled = (CheckBox)findViewById(R.id.peer_info_enabled);
		peerInfoEnabled.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (peerManager!=null && peerInfo.enabled!=isChecked)
					peerManager.setPeerEnabled(peerInfo, isChecked);
			}
		});
	}

	@Override
	protected void onStart() {
		super.onStart();
		Intent i = new Intent(this, Service.class);
		IntentFilter peerChangeFilter = new IntentFilter(PeerManager.ACTION_PEER_STATE_CHANGED);
		registerReceiver(peerChangeListener, peerChangeFilter);
		bindService(i, mServiceConnection, 0);
	}

	@Override
	protected void onStop() {
		super.onStop();
		unbindService(mServiceConnection);
		unregisterReceiver(peerChangeListener);
	}

}
