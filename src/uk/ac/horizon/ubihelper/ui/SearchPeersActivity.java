/**
 * 
 */
package uk.ac.horizon.ubihelper.ui;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.LinkedList;

import uk.ac.horizon.ubihelper.R;
import uk.ac.horizon.ubihelper.R.layout;
import uk.ac.horizon.ubihelper.dns.DnsProtocol.RR;
import uk.ac.horizon.ubihelper.service.PeerManager;
import uk.ac.horizon.ubihelper.service.Service;
import uk.ac.horizon.ubihelper.service.PeerManager.SearchInfo;
import uk.ac.horizon.ubihelper.service.Service.LocalBinder;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
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
public class SearchPeersActivity extends ListActivity {
	private static final String TAG = "ubihelper-search";
	private View searchView;
	private ArrayAdapter<PeerManager.SearchInfo> peerInfos; 
	static final int DIALOG_ADD_PEER = 1;
	private PeerManager.SearchInfo currentPeerInfo = null;
	private PeerManager peerManager = null;
	private SearchListener searchListener = new SearchListener();
	boolean hadSavedInstanceState = false;
	
	@SuppressWarnings("unused")
	private void setHeaderText(String text) {
		if (searchView!=null)
			((TextView)searchView).setText(text);
	}
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// TODO from table
		peerInfos = new ArrayAdapter<PeerManager.SearchInfo>(this, R.layout.peer_item);
		//aa.add("Hello list 2");
		ListView lv = getListView();
		// following line fails with addView not supported in AdapterView
		searchView = getLayoutInflater().inflate(R.layout.search_peer_item, null);
		setHeaderText("Search");
		lv.addHeaderView(searchView, "Add a new item", true);
		lv.setAdapter(peerInfos);
		hadSavedInstanceState = savedInstanceState!=null;
		
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (view==searchView) {
					startSearch(false);
				}
				else {
					if (position<1 || position>peerInfos.getCount()) 
					{
						Toast.makeText(SearchPeersActivity.this, "Position out of range: "+position, Toast.LENGTH_SHORT).show();
					} 
					else {
						currentPeerInfo = peerInfos.getItem(position-1);
						showDialog(DIALOG_ADD_PEER);
					}
				}
			}			
		});
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		switch (id) {
		case DIALOG_ADD_PEER: {
			final PeerManager.SearchInfo peerInfo = currentPeerInfo;
			if (peerInfo==null) {
				Log.e(TAG,"onCreateDialog ADD_PEER with null currentPeerInfo");
				return dialog;
			}
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("Add peer "+peerInfo.toString()+"?")
			       //.setCancelable(false)
			       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			        	   dialog.cancel();
			        	   startAddPeer(peerInfo);
			           }
			       })
			       .setNegativeButton("No", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			                dialog.cancel();
			           }
			       });
			dialog = builder.create();	
			break;
		}
		default:
			dialog = super.onCreateDialog(id);				
		}
		return dialog;
	}
	
	// Start adding a peer!
	protected void startAddPeer(PeerManager.SearchInfo peerInfo) {
		if (peerManager!=null)
			peerManager.addPeer(peerInfo);
		// UI..
		Intent i = PeerInfoActivity.getStartActivityIntent(this, peerInfo);
		startActivity(i);
	}
	private Intent getServiceIntent() {		
		Intent i = new Intent(this.getApplicationContext(), Service.class);
		return i;
	}
	@Override
	protected void onStart() {
		super.onStart();
		peerInfos.clear();
		IntentFilter searchFilter = new IntentFilter(PeerManager.ACTION_PEER_DISCOVERED);
		searchFilter.addAction(PeerManager.ACTION_SEARCH_STARTED);
		searchFilter.addAction(PeerManager.ACTION_SEARCH_STOPPED);	
		registerReceiver(searchListener, searchFilter);
		Intent service = getServiceIntent();
		// should call startSearch once connected!
		bindService(service, mServiceConnection, 0);
	}

	private class SearchListener extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO Auto-generated method stub
			if (PeerManager.ACTION_PEER_DISCOVERED.equals(intent.getAction())) {
				Bundle extras = intent.getExtras();
				try {
					PeerManager.SearchInfo si = new PeerManager.SearchInfo();
					si.name = extras.getString(PeerManager.EXTRA_NAME);
					si.src = InetAddress.getByName(extras.getString(PeerManager.EXTRA_SOURCEIP));
					peerInfos.add(si);
				}
				catch (Exception e) {
					Log.w(TAG,"Extra making SearchInfo from ACTION_PEER_DISCOVERED: "+e.getMessage());
				}
			} else if (PeerManager.ACTION_SEARCH_STOPPED.equals(intent.getAction())) {
				setHeaderText("Search again");
			}
		}
		
	}
	
	private synchronized void startSearch(boolean resumeOnly) {
		setHeaderText("Search again");
		if (peerManager==null) {
			return;
		}
		// previous records
		peerInfos.clear();
		LinkedList<PeerManager.SearchInfo> sis = peerManager.getSearchAnswers();
		for (PeerManager.SearchInfo si : sis) {
			peerInfos.add(si);
		}
		if (peerManager.isSearchActive()) {
			setHeaderText("Searching...");
		}
		else if (!resumeOnly) {
			setHeaderText("Searching...");
			peerInfos.clear();
			peerManager.startSearch();
		}
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		unregisterReceiver(searchListener);
		unbindService(mServiceConnection);
	}

	/** monitor binding to service (used for preference update push) */
	private ServiceConnection mServiceConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName name, IBinder binder) {
			Service service = ((Service.LocalBinder)binder).getService();
			Log.d(TAG,"Service connected");
			synchronized (SearchPeersActivity.this) {
				peerManager = service.getPeerManager();
			}
			startSearch(hadSavedInstanceState);
		}

		public void onServiceDisconnected(ComponentName name) {
			synchronized (SearchPeersActivity.this) {
				peerManager = null;
			}
			Log.d(TAG,"Service disconnected");
		}		
	};
	

	
}
