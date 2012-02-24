/**
 * 
 */
package uk.ac.horizon.ubihelper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import uk.ac.horizon.ubihelper.DnsProtocol.RR;
import uk.ac.horizon.ubihelper.PeerManager.SearchInfo;
import uk.ac.horizon.ubihelper.net.OnPeerConnectionListener;
import uk.ac.horizon.ubihelper.net.PeerConnection;
import uk.ac.horizon.ubihelper.net.PeerConnectionScheduler;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.widget.Toast;

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
	private ServerSocketChannel serverSocketChannel;
	private int serverPort;
	private PeerConnectionScheduler selector;
	
	public static class SearchInfo {
		public String name;
		public InetAddress src;
		public String toString() {
			return name;
		}
	}
	
	public static final String ACTION_SEARCH_STARTED = "uk.ac.horizon.ubihelper.action.SEARCH_STARTED";
	public static final String ACTION_SEARCH_STOPPED = "uk.ac.horizon.ubihelper.action.SEARCH_STOPPED";
	/** Broadcast, has extra NAME & SOURCEIP */
	public static final String ACTION_PEER_DISCOVERED = "uk.ac.horizon.ubihelper.action.PEER_DISCOVERED";
	/** Broadcast, has extra NAME, SOURCEIP, PEER_STATE & (optionally) PORT */
	public static final String ACTION_PEER_STATE_CHANGED = "uk.ac.horizon.ubihelper.action.PEER_STATE_CHANGED";
	public static final String EXTRA_NAME = "uk.ac.horizon.ubihelper.extra.NAME";
	public static final String EXTRA_SOURCEIP = "uk.ac.horizon.ubihelper.extra.SOURCEIP";
	public static final String EXTRA_PORT = "uk.ac.horizon.ubihelper.extra.PORT";
	public static final String EXTRA_PEER_STATE = "uk.ac.horizon.ubihelper.extra.PEER_STATE";
	public static final String EXTRA_DETAIL = "uk.ac.horizon.ubihelper.extra.DETAIL";
	public static final String ACTION_PEERS_CHANGED = "uk.ac.horizon.ubihelper.action.PEERS_CHANGED";
	private static final long MAX_QUERY_AGE = 15000;
	
	public PeerManager(Service service) {
		this.service = service;
		wifi = (WifiManager)service.getSystemService(Service.WIFI_SERVICE);
		
		try {
			serverSocketChannel = ServerSocketChannel.open();
			ServerSocket ss = serverSocketChannel.socket();
			ss.bind(new InetSocketAddress(InetAddress.getByName("0.0.0.0"),0));
			serverPort = ss.getLocalPort();
			serverSocketChannel.configureBlocking(false);
		} catch (IOException e) {
			Log.w(TAG,"Error opening ServerSocketChannel: "+e.getMessage());
		}
		try {
			selector = new PeerConnectionScheduler(serverSocketChannel);
			selector.setListener(selectorListener);
			selector.start();
		} catch (IOException e) {
			Log.w(TAG,"Error starting Selector: "+e.getMessage());
		}
	}
	public synchronized int getServerPort() {
		return serverPort;
	}
	public synchronized void close() {
		closed = true;
		closeInternal();
	}
	private synchronized void closeInternal() {		
		closeSearchInternal();
		if (serverSocketChannel!=null) {
			try {
				serverSocketChannel.close();
			} catch (IOException e) {
			}
			serverSocketChannel = null;
		}
		if (selector!=null)
			selector.close();
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
	/** current queries - by name */
	private HashMap<String,ArrayList<DnsClient>> dnsClients = new HashMap<String,ArrayList<DnsClient>>();
	private DnsClient getDnsClient(String name, int type, InetAddress dest) {
		ArrayList<DnsClient> dcs = dnsClients.get(name);
		for (int i=0; dcs!=null && i<dcs.size(); i++) {
			DnsClient dc  = dcs.get(i);
			if (dc.getAge() > MAX_QUERY_AGE) {
				dcs.remove(i);
				i--;
				continue;
			}
			if (type==dc.getQuery().type && (dest==null || dest.equals(dc.getDestination())))
				return dc;
		}
		return null;
	}
	/** peer state */
	public static enum PeerState {
		STATE_SEARCHED_ADD,
		STATE_SRV_DISCOVERY,
		STATE_SRV_DISCOVERY_FAILED,
		STATE_SRV_FOUND,
		STATE_CONNECTING,
		STATE_CONNECTED,
		STATE_CONNECTING_FAILED,
	}
	/** peer info */
	public static class PeerInfo implements Cloneable {
		public int id = -1;
		public PeerState state;
		// Search
		public String instanceName;
		public InetAddress src;
		// SRV
		public int port = 0;
		// connect
		PeerConnection pc;
		
		// debug
		public String detail;
		
		PeerInfo(String instanceName, InetAddress src) {
			this.instanceName = instanceName;
			this.src = src;
			state = PeerState.STATE_SEARCHED_ADD;
		}		
		PeerInfo(PeerInfo pi) {
			id = pi.id;
			state = pi.state;
			instanceName = pi.instanceName;
			src = pi.src;
			port = pi.port;
			detail = pi.detail;
		}
	}
	private void broadcastPeerState(PeerInfo pi) {
		Intent i = new Intent(ACTION_PEER_STATE_CHANGED);
		i.putExtra(EXTRA_PEER_STATE, pi.state.ordinal());
		i.putExtra(EXTRA_NAME, pi.instanceName);
		if (pi.detail!=null)
			i.putExtra(EXTRA_DETAIL, pi.detail);
		i.putExtra(EXTRA_SOURCEIP, pi.src.getHostAddress());
		if (pi.port!=0)
			i.putExtra(EXTRA_PORT, pi.port);
		//Log.d(TAG,"sendBroadcast (peer state)...");
		service.sendBroadcast(i);
		//Log.d(TAG,"sendBroadcast done");
	}
	private ArrayList<PeerInfo> peers = new ArrayList<PeerInfo>();

	/** public API - list peers */
	public synchronized List<PeerInfo> getPeers() {
		ArrayList<PeerInfo> rpeers = new ArrayList<PeerInfo>();
		for (PeerInfo pi : peers) {
			rpeers.add(new PeerInfo(pi));
		}
		return rpeers;
	}
	/** public API - get info on peer */
	public synchronized PeerInfo getPeer(Intent i) {
		String sourceip = i.getExtras().getString(EXTRA_SOURCEIP);
		String name = i.getExtras().getString(EXTRA_NAME);
		for (PeerInfo pi : peers) {
			if (pi.instanceName.equals(name) && pi.src.getHostAddress().equals(sourceip))
				return pi;
		}
		return null;
	}
	/** public API - matches */
	public static synchronized boolean matches(PeerInfo pi, Intent i) {
		String sourceip = i.getExtras().getString(EXTRA_SOURCEIP);
		String name = i.getExtras().getString(EXTRA_NAME);
		if (pi.instanceName.equals(name) && pi.src.getHostAddress().equals(sourceip))
			return true;
		return false;
	}
	/** public API - matches */
	public static synchronized boolean matches(PeerInfo pi, SearchInfo si) {
		if (pi.instanceName.equals(si.name) && pi.src.equals(si.src))
			return true;
		return false;
	}
	/** public API - start adding a discovered peer */
	public synchronized void addPeer(SearchInfo peerInfo) {
		// already in progress?
		for (PeerInfo pi : peers) {
			if (matches(pi, peerInfo)) {
				// kick?
				return;
			}
		}
		PeerInfo pi = new PeerInfo(peerInfo.name, peerInfo.src);
		peers.add(pi);
		Intent i = new Intent(ACTION_PEERS_CHANGED);
		service.sendBroadcast(i);
		updatePeer(pi);
	}
	private synchronized void updatePeer(PeerInfo pi) {
		switch(pi.state) {
		case STATE_SEARCHED_ADD:
			startSrvDiscovery(pi);
			break;
		case STATE_SRV_DISCOVERY_FAILED:
		case STATE_CONNECTING_FAILED:
			broadcastPeerState(pi);
			removePeer(pi);
			break;
		case STATE_SRV_FOUND:
			connectPeer(pi);
			break;
		case STATE_CONNECTED:
			// create and send peer request message
			sendPeerRequest(pi);
		}
	}
	private OnPeerConnectionListener peerConnectionListener = new OnPeerConnectionListener() {
		public void onRecvMessage(PeerConnection pc) {
		}
		
		public void onFail(PeerConnection pc, boolean sendFailed,
				boolean recvFailed, boolean connectFailed) {
			if (pc.attachment() instanceof PeerInfo) {
				PeerInfo pi = (PeerInfo)pc.attachment();
				Log.d(TAG, "onFail PeerInfo "+pi);
				pi.state = PeerState.STATE_CONNECTING_FAILED;
				pi.detail = null;
				updatePeer(pi);
			}
			else if (pc.attachment() instanceof ClientInfo) {
				ClientInfo ci = (ClientInfo)pc.attachment();
				Log.d(TAG,"onFail ClientInfo "+ci);
				// TODO
			}
		}

		public void onConnected(PeerConnection pc) {
			if (pc.attachment() instanceof PeerInfo) {
				PeerInfo pi = (PeerInfo)pc.attachment();
				if (pi.state==PeerState.STATE_CONNECTING) {
					Log.d(TAG, "onConnected -> connected PeerInfo "+pi);
					pi.state = PeerState.STATE_CONNECTED;
					pi.detail = null;
					broadcastPeerState(pi);
					updatePeer(pi);
				}
				else
					Log.d(TAG,"onConnected "+pi.state+" PeerInfo "+pi);
			}
		}
	};
	
	private synchronized void connectPeer(PeerInfo pi) {
		if (pi.src==null) {
			pi.state = PeerState.STATE_CONNECTING_FAILED;
			pi.detail = "IP unknown";
			updatePeer(pi);
			return;
		}
		pi.state = PeerState.STATE_CONNECTING;
		pi.detail = "connectPeer()";
		try {
			Log.d(TAG,"Connect to "+pi.src.getHostAddress()+":"+pi.port);
			pi.pc = selector.connect(new InetSocketAddress(pi.src, pi.port), peerConnectionListener, pi);
			//Log.d(TAG,"Connect done="+done);
			//pi.detail = "4 (done="+done+")";
			boolean done = pi.pc.isConnected();
			if (done) {
				//Toast.makeText(service, "Connected!", Toast.LENGTH_SHORT).show();
				pi.state = PeerState.STATE_CONNECTED;
				pi.detail = "Connected immediately";
			}
			else {
				//Toast.makeText(service, "waiting...", Toast.LENGTH_SHORT).show();
				pi.detail = "Waiting for connect";
				//Log.d(TAG,"Register (connect)...");
				//Log.d(TAG,"registered");
			}
			//Log.d(TAG,"Broadcast...");
			broadcastPeerState(pi);
			//Log.d(TAG,"Broadcast done");
			if (done) {
				updatePeer(pi);
			}
			return;
		} catch (Exception e) {
			// Not just IOExceptions?!
			Log.w(TAG,"Problem connecting to peer "+pi.src.getHostAddress()+":"+pi.port+": "+e.getMessage());
			//Toast.makeText(service, "Error connecting: "+e, Toast.LENGTH_LONG).show();
			pi.state = PeerState.STATE_CONNECTING_FAILED;
			pi.detail = e.getMessage();
			updatePeer(pi);
		}		
	}
	private synchronized void startSrvDiscovery(final PeerInfo pi) {
		// 1: do SRV discovery to get IP/Port
		String name = DnsUtils.getServiceDiscoveryName();
		DnsClient dc = getDnsClient(name, DnsProtocol.TYPE_SRV, pi.src);
		if (dc==null) {
			// start again
			DnsProtocol.Query query = new DnsProtocol.Query();
			query.name = DnsUtils.getServiceDiscoveryName();
			query.type = DnsProtocol.TYPE_SRV;
			query.rclass = DnsProtocol.CLASS_IN;
			final DnsClient fdc = new DnsClient(query, false);
			NetworkInterface ni = getNetworkInterface();
			if (ni!=null)
				fdc.setNetworkInterface(ni);
			fdc.setDestination(pi.src);
			fdc.setOnChange(new DnsClient.OnChange() {
				public void onAnswer(RR rr) {
				}
				public void onComplete(String error) {
					service.postTask(new Runnable() {
						public void run() {
							srvDiscoveryComplete(fdc, pi.src);							
						}						
					});
				}
			});
			ArrayList<DnsClient> dcs = dnsClients.get(name);
			if (dcs==null) {
				dcs = new ArrayList<DnsClient>();
				dnsClients.put(name, dcs);
			}
			dcs.add(fdc);
			pi.state = PeerState.STATE_SRV_DISCOVERY;
			broadcastPeerState(pi);
			fdc.start();
			return;
		}
		else {
			pi.state = PeerState.STATE_SRV_DISCOVERY;
			// handle result? (not too old, but may have finished, or not)
			boolean done = dc.getDone();
			if (done) {
				srvDiscoveryComplete(dc, pi.src);
			}
			else
				broadcastPeerState(pi);
			// otherwise will be done...
			return;
		}
	}
		// 2: initiate connection
		// 3: generate and send peer request
		// 4: wait for and handle response 		
	private synchronized void srvDiscoveryComplete(DnsClient dc, InetAddress src) {
		// copy peers in case of delete
		ArrayList<PeerInfo> peers2 = new ArrayList<PeerInfo>();
		peers2.addAll(peers);
		for (int i=0; i<peers2.size(); i++) {
			PeerInfo pi = peers2.get(i);
			if (pi.state==PeerState.STATE_SRV_DISCOVERY && pi.src.equals(src)) {
				LinkedList<DnsProtocol.RR> as = dc.getAnswers();
				if (as.size()==0) {
					pi.state = PeerState.STATE_SRV_DISCOVERY_FAILED;
					updatePeer(pi);
				} else {
					try {
						DnsProtocol.SrvData srv = DnsProtocol.srvFromData(as.get(0).rdata);
						pi.state = PeerState.STATE_SRV_FOUND;
						pi.port = srv.port;
						if (!srv.target.equals(src.getHostAddress())) {
							Log.w(TAG,"SRV returned different IP: "+srv.target+" vs "+src.getHostAddress());
						}
						updatePeer(pi);
					} catch (IOException e) {
						Log.w(TAG,"Error parsing SRV data: "+e.getMessage());
						pi.state = PeerState.STATE_SRV_DISCOVERY_FAILED;					
						updatePeer(pi);
					}					
				}
			}
		}
	}
	private synchronized void removePeer(PeerInfo pi) {
		if (peers.remove(pi))
		{
			Intent i = new Intent(ACTION_PEERS_CHANGED);
			service.sendBroadcast(i);
		}
		if (pi.pc!=null) {
			pi.pc.close();
			pi.pc = null;
		}
	}
	private void sendPeerRequest(PeerInfo pi) {
		// create peer request
		// pass key
		// shared secret, encrypted with pass key
		// local device information
		
		// TODO Auto-generated method stub
		
	}

	private static class ClientInfo {
		// connect
		PeerConnection pc;
		
		public ClientInfo(PeerConnection pc) {
			this.pc = pc;
		}
	}
	
	/** clients */
	private ArrayList<ClientInfo> clients = new ArrayList<ClientInfo>();

	private PeerConnectionScheduler.Listener selectorListener = new PeerConnectionScheduler.Listener() {
		
		public void onAccept(PeerConnectionScheduler pcs,
				PeerConnection newPeerConnection) {
			Log.d(TAG,"onAccept new peer");
			// TODO Auto-generated method stub
			ClientInfo ci = new ClientInfo(newPeerConnection);
			newPeerConnection.setOnPeerConnectionListener(peerConnectionListener);
			clients.add(ci);
			Log.d(TAG,"Accepted new connection from "+newPeerConnection.getSocketChannel().socket().getInetAddress().getHostAddress()+":"+newPeerConnection.getSocketChannel().socket().getPort());			
		}
	};

}
