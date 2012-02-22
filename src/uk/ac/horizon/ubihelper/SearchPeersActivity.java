/**
 * 
 */
package uk.ac.horizon.ubihelper;

import java.io.IOException;
import java.net.NetworkInterface;
import java.util.ArrayList;

import uk.ac.horizon.ubihelper.DnsProtocol.RR;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
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
	private DnsClient dnsClient = null;
	private WifiManager wifi = null;
	private DnsClient.OnChange onChangeListener = new OnChangeListener();
	private ArrayAdapter<PeerInfo> peerInfos; 
	static final int DIALOG_ADD_PEER = 1;
	private PeerInfo currentPeerInfo = null;
	
	private class PeerInfo {
		private RR rr;
		private String name;
		private String ns[];
		
		public PeerInfo(RR rr) {
			this.rr = rr;
			if (rr.type==DnsProtocol.TYPE_PTR) {
				try {
					ns = DnsProtocol.ptrFromData(rr.rdata);
					if (ns!=null && ns.length>0)
						name = ns[0];
				} catch (IOException e) {
					Log.w(TAG,"Error decoding PTR record: "+e.getMessage());
					name = "Sorry, could not decode name";
				}
			}
		}
		
		public String toString() {
			return name;
		}
	}
	
	@SuppressWarnings("unused")
	private void setHeaderText(String text) {
		if (searchView!=null)
			((TextView)searchView).setText(text);
	}
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// TODO from table
		peerInfos = new ArrayAdapter<PeerInfo>(this, R.layout.peer_item);
		//aa.add("Hello list 2");
		ListView lv = getListView();
		// following line fails with addView not supported in AdapterView
		searchView = getLayoutInflater().inflate(R.layout.search_peer_item, null);
		setHeaderText("Search");
		lv.addHeaderView(searchView, "Add a new item", true);
		lv.setAdapter(peerInfos);
		
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (view==searchView) {
					if (dnsClient==null)
						startSearch();
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
		
		wifi = (WifiManager)getSystemService(WIFI_SERVICE);
	}

	@Override
	protected Dialog onCreateDialog(int id, Bundle args) {
		Dialog dialog = null;
		switch (id) {
		case DIALOG_ADD_PEER: {
			final PeerInfo peerInfo = currentPeerInfo;
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
			dialog = super.onCreateDialog(id, args);				
		}
		return dialog;
	}
	
	// Start adding a peer!
	protected void startAddPeer(PeerInfo peerInfo) {
		// TODO Auto-generated method stub
		
	}
	@Override
	protected void onStart() {
		super.onStart();
		startSearch();
	}

	private synchronized void startSearch() {
		abortSearch();
		setHeaderText("Search again");
		// enabled?
		if (!wifi.isWifiEnabled()) {
			wifiError("Please enable wifi in order to search");
			return;
		}
		switch (wifi.getWifiState()) {
		case WifiManager.WIFI_STATE_ENABLED:
			// OK
			break;
		case WifiManager.WIFI_STATE_ENABLING:
			wifiError("Please wait for Wifi to finish enabling");
			return;
		case WifiManager.WIFI_STATE_DISABLING:
		case WifiManager.WIFI_STATE_DISABLED:
			wifiError("Please enable wifi in order to search");
			return;
		default:
			wifiError("Sorry, could not get wifi state - please try again");
			return;
		}
		// has address?
		WifiInfo info = wifi.getConnectionInfo();
		int ip = info.getIpAddress();
		if (ip==0) {
			wifiError("Sorry, still waiting for an IP address");
			return;
		}
		
		DnsProtocol.Query query = new DnsProtocol.Query();
		query.name = DnsUtils.getServiceDiscoveryName();
		query.rclass = DnsProtocol.CLASS_IN;
		query.type = DnsProtocol.TYPE_PTR;
		dnsClient = new DnsClient(query, true);
		dnsClient.setOnChange(onChangeListener);
		NetworkInterface ni = DnsUtils.getNetworkInterface(ip);
		if (ni!=null)
			dnsClient.setNetworkInterface(ni);

		setHeaderText("Searching...");
		peerInfos.clear();
		
		dnsClient.start();
	}
	
	private class OnChangeListener implements DnsClient.OnChange {
		public void onAnswer(final RR rr) {
			runOnUiThread(new Runnable() {
				public void run() {
					PeerInfo pi = new PeerInfo(rr);
					peerInfos.add(pi);
				}
			});
		}
		public void onComplete(String error) {
			runOnUiThread(new Runnable() {
				public void run() {
					setHeaderText("Search again");
					abortSearch();
				}
			});
		}		
	}
	
	private void wifiError(String string) {
		Log.d(TAG,"wifiError: "+string);
		Toast.makeText(getApplicationContext(), string, Toast.LENGTH_SHORT).show();
	}
	@Override
	protected void onStop() {
		super.onStop();
		abortSearch();
	}
	private synchronized void abortSearch() {
		if (dnsClient!=null) {
			dnsClient.close();
			dnsClient = null;
		}		
	}

	
	
}
