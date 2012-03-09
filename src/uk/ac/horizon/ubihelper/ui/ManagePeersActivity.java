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

import java.util.ArrayList;
import java.util.List;

import uk.ac.horizon.ubihelper.R;
import uk.ac.horizon.ubihelper.R.layout;
import uk.ac.horizon.ubihelper.protocol.PeerInfo;
import uk.ac.horizon.ubihelper.service.PeerManager;
import uk.ac.horizon.ubihelper.service.PeersOpenHelper;
import uk.ac.horizon.ubihelper.service.Service;
import uk.ac.horizon.ubihelper.service.PeerManager.PeerRequestInfo;
import uk.ac.horizon.ubihelper.service.Service.LocalBinder;

import android.app.Activity;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.HeaderViewListAdapter;
import android.widget.ListView;
import android.widget.ListView.FixedViewInfo;
import android.widget.CompoundButton;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author cmg
 *
 */
public class ManagePeersActivity extends ListActivity {
	static final String TAG = "ubihelper-managepeers";
	private View searchView;
	private PeerManager peerManager;
	private ArrayAdapter<PeerInfo> peersAdapter;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final OnClickListener clickListener = new OnClickListener() {
			public void onClick(View view) {
				PeerInfo pi = (PeerInfo)view.getTag();
				Log.d(TAG,"onItemClick "+pi.name);
				Intent i = PeerInfoActivity.getStartActivityIntent(ManagePeersActivity.this, pi);
				startActivity(i);
			}
		};
		final OnCheckedChangeListener enableListener = new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				PeerInfo pi = (PeerInfo)buttonView.getTag();
				Log.d(TAG,"onCheckedChanged "+pi.name+" -> "+isChecked);
				if (peerManager!=null && pi.enabled!=isChecked)
					peerManager.setPeerEnabled(pi, isChecked);
			}
		};
		peersAdapter = new ArrayAdapter<PeerInfo>(this, R.layout.peer_item, R.id.peer_item_name) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				View v = convertView;
				if (v==null) {
					v = ManagePeersActivity.this.getLayoutInflater().inflate(R.layout.peer_item, null);
				}
				PeerInfo pi = getItem(position);
				CheckBox enabled = (CheckBox)v.findViewById(R.id.peer_item_enabled);
				enabled.setTag(pi);
				enabled.setChecked(pi.enabled);
				//enabled.setEnabled(false);
				enabled.setOnCheckedChangeListener(enableListener);
				TextView name = (TextView)v.findViewById(R.id.peer_item_name);
				name.setText(pi.name);
				TextView description = (TextView)v.findViewById(R.id.peer_item_description);
				description.setText(pi.trusted ? "Trusted peer" : "Untrusted peer");
				v.setClickable(true);
				v.setTag(pi);
				v.setOnClickListener(clickListener);
				return v;
			}			
		};
		//aa.add("Hello list");
		ListView lv = getListView();
		// following line fails with addView not supported in AdapterView
		searchView = getLayoutInflater().inflate(R.layout.add_peer_item, null);
		lv.addFooterView(searchView, "Add a new peer", true);
		lv.setAdapter(peersAdapter);
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> adapter, View view, int arg2, long arg3) { 
				if (view==searchView) {
					Intent i = new Intent(ManagePeersActivity.this, SearchPeersActivity.class);
					startActivity(i);
				}
			}
		});
		
	}

	/* (non-Javadoc)
	 * @see android.app.ListActivity#onDestroy()
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	protected void onStart() {
		super.onStart();
		registerReceiver(peersChangedReceiver, new IntentFilter(PeerManager.ACTION_PEERS_CHANGED));
		Intent i = new Intent(this, Service.class);
		bindService(i, mServiceConnection, 0);
	}

	@Override
	protected void onStop() {
		super.onStop();
		unbindService(mServiceConnection);
		unregisterReceiver(peersChangedReceiver);
	}

	/** monitor binding to service (used for preference update push) */
	private ServiceConnection mServiceConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName name, IBinder binder) {
			peerManager = ((Service.LocalBinder)binder).getService().getPeerManager();
			Log.d(TAG,"Service connected");
			refresh();
		}

		public void onServiceDisconnected(ComponentName name) {
			peerManager = null;
			Log.d(TAG,"Service disconnected");
		}		
	};

	private BroadcastReceiver peersChangedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			// UI thread?
			refresh();
		}
	};	

	protected void refresh() {
		peersAdapter.clear();
		
		List<PeerInfo> peers = peerManager.getPeers();
		for (PeerInfo pi : peers) {
			Log.d(TAG,"Retrieved PeerInfo "+pi.id);
			peersAdapter.add(pi);
		}
	}
	
}
