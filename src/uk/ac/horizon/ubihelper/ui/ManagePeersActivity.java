/**
 * 
 */
package uk.ac.horizon.ubihelper.ui;

import java.util.ArrayList;
import java.util.List;

import uk.ac.horizon.ubihelper.R;
import uk.ac.horizon.ubihelper.R.layout;
import uk.ac.horizon.ubihelper.service.PeerManager;
import uk.ac.horizon.ubihelper.service.Service;
import uk.ac.horizon.ubihelper.service.PeerManager.PeerInfo;
import uk.ac.horizon.ubihelper.service.Service.LocalBinder;

import android.app.Activity;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.HeaderViewListAdapter;
import android.widget.ListView;
import android.widget.ListView.FixedViewInfo;
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
	private ArrayAdapter<PeerWrapper> peersAdapter;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		peersAdapter = new ArrayAdapter<PeerWrapper>(this, R.layout.peer_item);
		//aa.add("Hello list");
		ListView lv = getListView();
		// following line fails with addView not supported in AdapterView
		searchView = getLayoutInflater().inflate(R.layout.add_peer_item, null);
		lv.addHeaderView(searchView, "Add a new item", true);
		lv.setAdapter(peersAdapter);
		
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (view==searchView) {
					Intent i = new Intent(ManagePeersActivity.this, SearchPeersActivity.class);
					startActivity(i);
				}
				else {
					if (position>=1 && position<=peersAdapter.getCount()) {
						PeerManager.PeerInfo pi = peersAdapter.getItem(position-1).peerInfo;
						Intent i = PeerInfoActivity.getStartActivityIntent(ManagePeersActivity.this, pi);
						startActivity(i);
					}
				}
			}			
		});
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
	
	private static class PeerWrapper {
		private PeerManager.PeerInfo peerInfo;
		PeerWrapper(PeerManager.PeerInfo peerInfo) {
			this.peerInfo = peerInfo;
		}
		public String toString() {
			return peerInfo.instanceName;
		}
	}
	protected void refresh() {
		// TODO Auto-generated method stub
		if (peerManager==null)
			return;
		peersAdapter.clear();		
		List<PeerManager.PeerInfo> peers = peerManager.getPeers();
		for (PeerManager.PeerInfo pi : peers) {
			peersAdapter.add(new PeerWrapper(pi));
		}
	}
}
