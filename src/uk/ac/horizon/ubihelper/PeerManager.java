/**
 * 
 */
package uk.ac.horizon.ubihelper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.LinkedList;

import uk.ac.horizon.ubihelper.DnsProtocol.RR;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

/** Manages all interactions with Peers
 * 
 * @author cmg
 *
 */
public class PeerManager {
	static final String TAG = "ubihelper-peermgr";
	private Service service;
	private boolean closed = false;
	/** for initial discovery search */
	private DnsClient searchClient = null;
	private WifiManager wifi = null;
	private DnsClient.OnChange onSearchChangeListener = new OnSearchChangeListener();
	private boolean searchStarted = false;
	
	public static class SearchInfo {
		public String name;
		public InetAddress src;
		public String toString() {
			return name;
		}
	}
	
	public static final String ACTION_SEARCH_STARTED = "uk.ac.horizon.ubihelper.action.SEARCH_STARTED";
	public static final String ACTION_SEARCH_STOPPED = "uk.ac.horizon.ubihelper.action.SEARCH_STOPPED";
	public static final String ACTION_PEER_DISCOVERED = "uk.ac.horizon.ubihelper.action.PEER_DISCOVERED";
	public static final String EXTRA_NAME = "uk.ac.horizon.ubihelper.extra.NAME";
	public static final String EXTRA_SOURCEIP = "uk.ac.horizon.ubihelper.extra.SOURCEIP";
	
	public PeerManager(Service service) {
		this.service = service;
		wifi = (WifiManager)service.getSystemService(Service.WIFI_SERVICE);
	}
	public synchronized void close() {
		closed = true;
		closeInternal();
	}
	private synchronized void closeInternal() {		
		closeSearchInternal();
	}
	private synchronized void closeSearchInternal() {		
		if (searchClient!=null) {
 			searchClient.close();
 			searchClient = null;
		}
		if (searchStarted) {
			Intent i = new Intent(ACTION_SEARCH_STOPPED);
			service.sendBroadcast(i);
			searchStarted = false;
		}
	}
	public synchronized boolean isSearchActive() {
		return (searchClient!=null && !searchClient.getDone());
	}
	public synchronized LinkedList<SearchInfo> getSearchAnswers() {
		LinkedList<SearchInfo> sis = new LinkedList<SearchInfo> ();
		if (searchClient==null)
			return sis;
		LinkedList<DnsProtocol.RR> as = searchClient.getAnswers();
		for (DnsProtocol.RR a : as) {
			SearchInfo si = getSearchInfo(a);
			if (si!=null)
				sis.add(si);
		}
		return sis;
	}
	public synchronized void startSearch() {
		closeSearchInternal();
		DnsProtocol.Query query = new DnsProtocol.Query();
		query.name = DnsUtils.getServiceDiscoveryName();
		query.rclass = DnsProtocol.CLASS_IN;
		query.type = DnsProtocol.TYPE_PTR;
		searchClient = new DnsClient(query, true);
		searchClient.setOnChange(onSearchChangeListener);
		NetworkInterface ni = getNetworkInterface();
		if (ni!=null)
			searchClient.setNetworkInterface(ni);
		searchClient.start();
		searchStarted = true;
		Intent i = new Intent(ACTION_SEARCH_STARTED);
		service.sendBroadcast(i);
	}
	private NetworkInterface getNetworkInterface() {
		if (!wifi.isWifiEnabled()) {
			Log.d(TAG,"wifi not enabled");
			return null;
		}
		switch (wifi.getWifiState()) {
		case WifiManager.WIFI_STATE_ENABLED:
			// OK
			break;
		case WifiManager.WIFI_STATE_ENABLING:
			Log.d(TAG, "Wifi enabling");
			return null;
		case WifiManager.WIFI_STATE_DISABLING:
		case WifiManager.WIFI_STATE_DISABLED:
			Log.d(TAG, "wifi disabled/disabling");
			return null;
		default:
			Log.d(TAG, "Wifi state unknown");
			return null;
		}
		// has address?
		WifiInfo info = wifi.getConnectionInfo();
		int ip = info.getIpAddress();
		if (ip==0) {
			Log.d(TAG,"waiting for an IP address");
			return null;
		}
		
		NetworkInterface ni = DnsUtils.getNetworkInterface(ip);
		return ni;
	}
	private class OnSearchChangeListener implements DnsClient.OnChange {
		public void onAnswer(final RR rr) {
			SearchInfo si = getSearchInfo(rr);
			if (si!=null) {
				Intent i = new Intent(ACTION_PEER_DISCOVERED);
				i.putExtra(EXTRA_NAME, si.name);
				i.putExtra(EXTRA_SOURCEIP, si.src.getHostAddress());
				service.sendBroadcast(i);
			}			
		}
		public void onComplete(String error) {
			closeSearchInternal();
		}		
	}
	private static SearchInfo getSearchInfo(DnsProtocol.RR rr) {
		if (rr.type==DnsProtocol.TYPE_PTR) {
			try {
				SearchInfo pi = new SearchInfo();
				String ns[] = DnsProtocol.ptrFromData(rr.rdata);
				if (ns!=null && ns.length>0)
					pi.name = ns[0];
				pi.src = rr.src;
				return pi;
			} catch (IOException e) {
				Log.w(TAG,"Error decoding PTR record: "+e.getMessage());
			}
		}
		return null;
	}
}
