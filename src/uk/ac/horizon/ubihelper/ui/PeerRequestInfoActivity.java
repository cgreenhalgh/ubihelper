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
import uk.ac.horizon.ubihelper.R.layout;
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
import android.widget.TextView;

/**
 * @author cmg
 *
 */
public class PeerRequestInfoActivity extends Activity {
	static final String TAG = "ubihelper-peerinfo";
	
	private PeerManager peerManager;
	private PeerManager.PeerRequestInfo peerInfo;
	private TextView peerInfoName, peerInfoSourceip, peerInfoState, peerInfoPort, peerInfoDetail;
	
	public static Intent getStartActivityIntent(Context context, PeerManager.SearchInfo si) {
		Intent i = new Intent(context, PeerRequestInfoActivity.class);
		i.putExtra(PeerManager.EXTRA_NAME, si.name);
		i.putExtra(PeerManager.EXTRA_SOURCEIP, si.src.getHostAddress());
		return i;
	}
	public static Intent getStartActivityIntent(Context context,
			PeerRequestInfo pi) {
		Intent i = new Intent(context, PeerRequestInfoActivity.class);
		i.putExtra(PeerManager.EXTRA_NAME, pi.instanceName);
		i.putExtra(PeerManager.EXTRA_SOURCEIP, pi.src.getHostAddress());
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
		if (intent!=null) {
			String id = intent.getExtras().getString(PeerManager.EXTRA_ID);
			if (id!=null) {
				String state = intent.getExtras().getString(PeerManager.EXTRA_PEER_STATE);
				if (PeerManager.PeerRequestState.STATE_PEERED.name().equals(state) ||
						PeerManager.PeerRequestState.STATE_PEERED_UNTRUSTED.name().equals(state)) {
					finish();
					startActivity(PeerInfoActivity.getStartActivityIntent(this, id));
					return;
				}						
			}
		}
		//Log.d(TAG,"refresh start");
		// this routine blocks when called from ServiceConnection, presumably because of the call to getPeer
		boolean ok = false;
		// fallback values
		if (getIntent()!=null) {
			String name = getIntent().getExtras().getString(PeerManager.EXTRA_NAME);
			peerInfoName.setText(name);
			String sourceip = getIntent().getExtras().getString(PeerManager.EXTRA_SOURCEIP);
			peerInfoSourceip.setText(sourceip);
		}
		if (peerManager!=null && getIntent()!=null) {
			//Log.d(TAG,"getPeer...");
			// this call blocks when called from ServiceConnection, presumably because of the lock on getPeer
			peerInfo = peerManager.getPeer(getIntent());
			//Log.d(TAG,"getPeer done");
			if (peerInfo!=null) {
				peerInfoName.setText(peerInfo.instanceName);
				peerInfoSourceip.setText(peerInfo.src.getHostAddress());
				peerInfoState.setText(peerInfo.state.name());
				peerInfoPort.setText(Integer.toString(peerInfo.port));
				peerInfoDetail.setText(peerInfo.detail!=null ? peerInfo.detail : "");
				ok = true;
			}
			else if (intent!=null) {
				try {
					String state = PeerManager.PeerRequestState.values()[intent.getExtras().getInt(PeerManager.EXTRA_PEER_STATE)].name();
					peerInfoState.setText(state+" (deleted)");
				}
				catch (Exception e) {
					int state = intent.getExtras().getInt(PeerManager.EXTRA_PEER_STATE);
					peerInfoState.setText(state+" (deleted)");
				}
				String detail = intent.getExtras().getString(PeerManager.EXTRA_DETAIL);
				peerInfoDetail.setText(detail!=null ? detail : "");
				peerInfoPort.setText("?");			
				ok = true;
			}
		}
		if (!ok) {
			//peerInfoName.setText("?");
			//peerInfoSourceip.setText("?");
			peerInfoState.setText("?");
			peerInfoPort.setText("?");			
			peerInfoDetail.setText("?");			
		}
		Log.d(TAG,"refresh complete");
	}

	private BroadcastReceiver peerChangeListener = new BroadcastReceiver () {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (peerInfo!=null && PeerManager.matches(peerInfo, intent))
				refresh(intent);
		}		
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.peer_request_info);
		peerInfoName = (TextView)findViewById(R.id.peer_info_name);
		peerInfoSourceip= (TextView)findViewById(R.id.peer_info_sourceip);
		peerInfoState = (TextView)findViewById(R.id.peer_info_state);
		peerInfoPort = (TextView)findViewById(R.id.peer_info_port);
		peerInfoDetail = (TextView)findViewById(R.id.peer_info_detail);
	}

	@Override
	protected void onStart() {
		super.onStart();
		Intent i = new Intent(this, Service.class);
		IntentFilter peerChangeFilter = new IntentFilter(PeerManager.ACTION_PEER_REQUEST_STATE_CHANGED);
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
