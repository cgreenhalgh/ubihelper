/**
 * 
 */
package uk.ac.horizon.ubihelper.ui;

import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;

import uk.ac.horizon.ubihelper.R;
import uk.ac.horizon.ubihelper.service.PeerManager;
import uk.ac.horizon.ubihelper.service.Service;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author cmg
 *
 */
public class SearchPeersActivity extends Activity {
	private static final String TAG = "ubihelper-search";
	private View searchView;
	private ArrayAdapter<PeerManager.SearchInfo> peerSearchAdapter; 
	static final int DIALOG_ADD_PEER = 1;
	private PeerManager.SearchInfo currentPeerInfo = null;
	private PeerManager peerManager = null;
	private ArrayAdapter<PeerRequestWrapper> peerRequestAdapter;
	//private View peerView;
	private SearchListener searchListener = new SearchListener();
	boolean hadSavedInstanceState = false;
	
	private static class PeerRequestWrapper {
		private PeerManager.PeerRequestInfo peerInfo;
		PeerRequestWrapper(PeerManager.PeerRequestInfo peerInfo) {
			this.peerInfo = peerInfo;
		}
		public String toString() {
			return peerInfo.instanceName;
		}
	}

	private void setHeaderText(String text) {
		if (searchView!=null)
			((TextView)searchView).setText(text);
	}
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.search_peers);

		// search
		peerSearchAdapter = new ArrayAdapter<PeerManager.SearchInfo>(this, R.layout.peer_item);
		//aa.add("Hello list 2");
		ListView lv = (ListView)findViewById(R.id.peer_search_list);
		// following line fails with addView not supported in AdapterView
		searchView = getLayoutInflater().inflate(R.layout.search_peer_item, null);
		setHeaderText("Search");
		lv.addHeaderView(searchView, "Add a new item", true);
		lv.setAdapter(peerSearchAdapter);
		hadSavedInstanceState = savedInstanceState!=null;
		
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (view==searchView) {
					startSearch(false);
				}
				else {
					if (position<1 || position>peerSearchAdapter.getCount()) 
					{
						Toast.makeText(SearchPeersActivity.this, "Position out of range: "+position, Toast.LENGTH_SHORT).show();
					} 
					else {
						currentPeerInfo = peerSearchAdapter.getItem(position-1);
						showDialog(DIALOG_ADD_PEER);
					}
				}
			}			
		});
		
		// requests
		peerRequestAdapter = new ArrayAdapter<PeerRequestWrapper>(this, R.layout.peer_item);
		//aa.add("Hello list");
		lv = (ListView)findViewById(R.id.peer_request_list);
		// following line fails with addView not supported in AdapterView
		//peerView = getLayoutInflater().inflate(R.layout.add_peer_item, null);
		//lv.addHeaderView(peerView, "Add a new item", true);
		lv.setAdapter(peerRequestAdapter);
		
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//				if (view==searchView) {
//					Intent i = new Intent(SearchPeersActivity.this, SearchPeersActivity.class);
//					startActivity(i);
//				}
//				else {
//					if (position>=1 && position<=peersAdapter.getCount()) {
				if (position>=0 && position<peerRequestAdapter.getCount()) {
						PeerManager.PeerRequestInfo pi = peerRequestAdapter.getItem(position).peerInfo;
						Intent i = PeerRequestInfoActivity.getStartActivityIntent(SearchPeersActivity.this, pi);
						startActivity(i);
					}
//				}
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
		Intent i = PeerRequestInfoActivity.getStartActivityIntent(this, peerInfo);
		startActivity(i);
	}
	private Intent getServiceIntent() {		
		Intent i = new Intent(this.getApplicationContext(), Service.class);
		return i;
	}
	@Override
	protected void onStart() {
		super.onStart();
		peerSearchAdapter.clear();
		IntentFilter searchFilter = new IntentFilter(PeerManager.ACTION_PEER_DISCOVERED);
		searchFilter.addAction(PeerManager.ACTION_SEARCH_STARTED);
		searchFilter.addAction(PeerManager.ACTION_SEARCH_STOPPED);	
		searchFilter.addAction(PeerManager.ACTION_PEER_REQUESTS_CHANGED);
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
					peerSearchAdapter.add(si);
					findViewById(R.id.search_peers_parent).requestLayout();
				}
				catch (Exception e) {
					Log.w(TAG,"Extra making SearchInfo from ACTION_PEER_DISCOVERED: "+e.getMessage());
				}
			} else if (PeerManager.ACTION_SEARCH_STOPPED.equals(intent.getAction())) {
				setHeaderText("Search again");
			} else if (PeerManager.ACTION_PEER_REQUESTS_CHANGED.equals(intent.getAction())) {
				refreshRequests();
			}
		}
		
	}
	protected void refreshRequests() {
		// TODO Auto-generated method stub
		if (peerManager==null)
			return;
		peerRequestAdapter.clear();		
		List<PeerManager.PeerRequestInfo> peers = peerManager.getPeerRequests();
		for (PeerManager.PeerRequestInfo pi : peers) {
			peerRequestAdapter.add(new PeerRequestWrapper(pi));
		}
		findViewById(R.id.search_peers_parent).requestLayout();
	}

	private synchronized void startSearch(boolean resumeOnly) {
		setHeaderText("Search again");
		if (peerManager==null) {
			return;
		}
		// previous records
		peerSearchAdapter.clear();
		LinkedList<PeerManager.SearchInfo> sis = peerManager.getSearchAnswers();
		for (PeerManager.SearchInfo si : sis) {
			peerSearchAdapter.add(si);
		}
		if (peerManager.isSearchActive()) {
			setHeaderText("(Searching)");
		}
		else if (!resumeOnly) {
			setHeaderText("(Searching)");
			peerSearchAdapter.clear();
			if (!peerManager.startSearch()) {
				setHeaderText("Search again (sorry, no WiFi last time)");
				return;
			}
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
			refreshRequests();
			startSearch(hadSavedInstanceState);
		}

		public void onServiceDisconnected(ComponentName name) {
			synchronized (SearchPeersActivity.this) {
				peerManager = null;
			}
			Log.d(TAG,"Service disconnected");
		}		
	};
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		 MenuInflater inflater = getMenuInflater();
		 inflater.inflate(R.menu.search_peers_menu, menu);
		 return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.manualadd_option:
		{
			Intent i  = new Intent(this, PeerManualAddActivity.class); 
			startActivity(i);
			return true;
		}
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	

	
}
