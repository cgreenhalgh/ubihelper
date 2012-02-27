/**
 * 
 */
package uk.ac.horizon.ubihelper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONException;
import org.json.JSONObject;

import uk.ac.horizon.ubihelper.DnsProtocol.RR;
import uk.ac.horizon.ubihelper.PeerManager.SearchInfo;
import uk.ac.horizon.ubihelper.net.Message;
import uk.ac.horizon.ubihelper.net.OnPeerConnectionListener;
import uk.ac.horizon.ubihelper.net.PeerConnection;
import uk.ac.horizon.ubihelper.net.PeerConnectionScheduler;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.util.Base64;
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
	private ServerSocketChannel serverSocketChannel;
	private int serverPort;
	private PeerConnectionScheduler selector;
	private SecureRandom srandom;
	private Random random;
	private MessageDigest messageDigest;
	
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
	public static final String EXTRA_ID = "uk.ac.horizon.ubihelper.extra.ID";
	public static final String ACTION_PEERS_CHANGED = "uk.ac.horizon.ubihelper.action.PEERS_CHANGED";
	private static final long MAX_QUERY_AGE = 15000;
	
	/** management message types */
	private static final String MSG_INIT_PEER_REQ = "init_peer_req";
	private static final String MSG_RESP_PEER_REQ = "resp_peer_req";
	private static final String MSG_INIT_PEER_DONE = "init_peer_done";
	private static final String MSG_RESP_PEER_NOPIN = "resp_peer_nopin";
	private static final String MSG_RESP_PEER_PIN = "resp_peer_pin";
	private static final String MSG_RESP_PEER_DONE = "resp_peer_done";
	/** management message keys */
	private static final String KEY_TYPE = "type";
	private static final String KEY_ID = "id";
	private static final String KEY_NAME = "name";
	private static final String KEY_PORT = "port";
	private static final String KEY_PINDIGEST = "pindigest";
	private static final String KEY_PIN = "pin";
	private static final String KEY_PINNONCE = "pinnonce";
	private static final String KEY_SECRET = "secret";
	private static final String KEY_INFO = "info";
	/** info keys */
	private static final String KEY_IMEI = "imei";
	private static final String KEY_WIFIMAC = "wifimac";
	private static final String KEY_BTMAC = "bcmac";
	
	public PeerManager(Service service) {
		this.service = service;
		wifi = (WifiManager)service.getSystemService(Service.WIFI_SERVICE);
		
		random = new Random(System.currentTimeMillis());
		try {
			srandom = SecureRandom.getInstance("SHA1PRNG");
		}
		catch (Exception e) {
			Log.e(TAG,"Could not get SecureRandom: "+e);
		}
		try {
			messageDigest = MessageDigest.getInstance("MD5");
		}
		catch (Exception e) {
			Log.e(TAG,"Could not get MessageDigest: "+e);
		}
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
		STATE_NEGOTIATE_PROTOCOL,
		STATE_PEER_REQ,
		STATE_PEER_DONE,
		STATE_PEERED
	}
	/** peer info */
	public static class PeerInfo implements Cloneable {
		public int _id = -1;
		public PeerState state;
		// Search
		public String instanceName;
		public InetAddress src;
		// SRV
		public int port = 0;
		// connect
		PeerConnection pc;
		// peer negotiation
		String pin;
		String pinnonce;
		String pindigest;
		String id;
		String secret1, secret2;
		// done
		public JSONObject peerInfo;
		
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
		public PeerInfo(ClientInfo ci) {
			id = ci.id;
			instanceName = ci.name;
			pc = ci.pc;
			port = ci.port;
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
			negotiateProtocol(pi);
			break;
		}
	}
	private OnPeerConnectionListener peerConnectionListener = new OnPeerConnectionListener() {
		public void onRecvMessage(PeerConnection pc) {
			if (pc.attachment() instanceof PeerInfo) {
				PeerInfo pi = (PeerInfo)pc.attachment();
				Log.d(TAG,"onMessage PeerInfo "+pi);
				checkMessages(pi);
			}
			else if (pc.attachment() instanceof ClientInfo) {
				ClientInfo ci = (ClientInfo)pc.attachment();
				Log.d(TAG,"onMessage ClientInfo "+ci);
				checkMessages(ci);
			}
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
	/** called on indication of avilable messages at client */ 
	protected synchronized void checkMessages(ClientInfo ci) {
		Message m = null;
		while ((m=ci.pc.getMessage())!=null) {
			switch (ci.state) {
			case STATE_NEGOTIATE_PROTOCOL: {
				boolean ok = checkNegotiateProtocolResponse(m);
				if (!ok) {
					removeClient(ci);
					return;
				}
				ci.pc.sendMessage(getHelloMessage());
				ci.state = ClientState.STATE_NEGOTIATED_PROTOCOL;
				break;
			}
			case STATE_NEGOTIATED_PROTOCOL: {
				boolean ok = handleFirstMessage(ci, m);
				if (!ok)
					return;
				break;
			}
			case STATE_PEER_PIN: {
				boolean ok = handlePeerPinResponse(ci, m);
				if (!ok)
					return;
				break;
			}
			}
		}		
	}
	private Message getHelloMessage() {
		return new Message(Message.Type.HELLO, null, null, Message.getHelloBody());
	}
	private boolean checkNegotiateProtocolResponse(Message m) {
		// should be hello
		if (m.type!=Message.Type.HELLO) {
			Log.e(TAG,"received "+m.type+" message when negotiating protocol");
			return false;
		}
		String protocol = Message.getHelloBody();
		// TODO versions
		if (!protocol.equals(m.body)) {
			Log.w(TAG,"received incompatible protocol: "+m.body);
			return false;
		}
		// OK
		return true;
	}
	private synchronized void removeClient(ClientInfo ci) {
		hideClientNotification(ci);
		try {
			ci.pc.close();
		}
		catch (Exception e) {
			/* ignore */
		}
		clients.remove(ci);
	}
	protected synchronized void failPeer(PeerInfo pi, String detail) {
		pi.state = PeerState.STATE_CONNECTING_FAILED;
		pi.detail = detail;
		broadcastPeerState(pi);
		removePeer(pi);
	}
	protected synchronized void checkMessages(PeerInfo pi) {
		Message m = null;
		while ((m=pi.pc.getMessage())!=null) {
			switch (pi.state){
			case STATE_NEGOTIATE_PROTOCOL: {
				boolean ok = checkNegotiateProtocolResponse(m);
				if (!ok) {
					failPeer(pi, "Incompatible protocol: "+m.body);
					return;
				}
				pi.state = PeerState.STATE_PEER_REQ;
				pi.detail = null;
				broadcastPeerState(pi);
				sendPeerRequest(pi);
				break;
			}
			case STATE_PEER_REQ: {
				// should get pin back (but in future could get something else, e.g.already known)
				boolean ok = handlePeerReqResponse(pi, m);
				if (!ok)
					return;
				break;
			}
			case STATE_PEER_DONE: {
				boolean ok = handlePeerDoneResponse(pi, m);
				if (!ok)
					return;
				break;
			}
			default:
				Log.w(TAG,"Don't know what to do with message in state "+pi.state);
				return;
			}
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
	private void getRandom(byte buf[]) {
		if (srandom!=null)
			srandom.nextBytes(buf);
		else
			random.nextBytes(buf);
	}
	
	private synchronized void negotiateProtocol(PeerInfo pi) {
		// Start negotiation on PeerConnection...
		// protocol hello
		String protocol = Message.getHelloBody();
		Message m = new Message(Message.Type.HELLO, null, null, protocol);
		pi.pc.sendMessage(m);
		pi.state = PeerState.STATE_NEGOTIATE_PROTOCOL;
		broadcastPeerState(pi);
		return;
	}		
	private synchronized void sendPeerRequest(PeerInfo pi) {
		
		// create peer request
		JSONObject msg = new JSONObject();
		try {
			msg.put(KEY_TYPE, MSG_INIT_PEER_REQ);
			// pass key
			byte pbuf[] = new byte[4];
			getRandom(pbuf);
			StringBuilder pb = new StringBuilder();
			for (int i=0; i<pbuf.length; i++) 
				pb.append((char)('0'+((pbuf[i]&0xff) % 10)));
			pi.pin = pb.toString();
			// pin nonce and digest
			byte nbuf[] = new byte[8];
			getRandom(nbuf);
			pi.pinnonce = Base64.encodeToString(nbuf, Base64.DEFAULT);
			messageDigest.reset();
			messageDigest.update(nbuf);
			messageDigest.update(pi.pin.getBytes("UTF-8"));
			byte dbuf[] = messageDigest.digest();
			pi.pindigest = Base64.encodeToString(dbuf, Base64.DEFAULT);
			msg.put(KEY_PINDIGEST, pi.pindigest);
			
			msg.put(KEY_ID, getDeviceId());
			msg.put(KEY_NAME, service.getDeviceName());
			msg.put(KEY_PORT, this.serverPort);
			Message m = new Message(Message.Type.MANAGEMENT, null, null, msg.toString());
			pi.pc.sendMessage(m);
			
			pi.state = PeerState.STATE_PEER_REQ;
			pi.detail = "Pin is "+pi.pin;
			broadcastPeerState(pi);
		} catch (JSONException e) {
			// shouldn't happen!
			Log.e(TAG,"JSON error (shoulnd't be): "+e);
		} catch (UnsupportedEncodingException e) {
			// shouldn't happen!
			Log.e(TAG,"Unsupported encoding (shoulnd't be): "+e);
		}		
	}	
	/** called from checkMessage(pi) to handle message in state STATE_PEER_REQ */
	private boolean handlePeerReqResponse(PeerInfo pi, Message m) {
		if (m.type!=Message.Type.MANAGEMENT) {
			failPeer(pi, "Received init_peer_req response of type "+m.type);
			return false;
		}
		String pinGuess = null;
		try {
			JSONObject msg = new JSONObject(m.body);
			String type = msg.getString(KEY_TYPE);
			if (!MSG_RESP_PEER_PIN.equals(type)) {
				failPeer(pi, "Received init_peer_req response "+type);
				// TODO resp_peer_nopin
				// TODO resp_peer_known
				return false;
			}
			// id, name, port, pin
			pinGuess = msg.getString(KEY_PIN);
			pi.id = msg.getString(KEY_ID);
			// TODO known IDs?
			int port = msg.getInt(KEY_PORT);
			if (port!=pi.port)
				Log.w(TAG,"resp_peer_pin has different port: "+port+" vs "+pi.port);
			String name = msg.getString(KEY_NAME);
			if (!name.equals(pi.instanceName))
				Log.w(TAG,"resp_peer_pin has different name: "+name+" vs "+pi.instanceName);
		} catch (JSONException e) {
			failPeer(pi, "Error in resp_peer_pin message: "+e);
			return false;
		}
		Log.i(TAG,"Received resp_peer_pin in state peer_req with pin="+pinGuess);
		// pin?
		if (!pi.pin.equals(pinGuess)) {
			failPeer(pi, "Incorrect pin: "+pinGuess);
			return false;
		}
		// done
		try {
			JSONObject resp = new JSONObject();
			resp.put(KEY_TYPE, MSG_INIT_PEER_DONE);
			byte sbuf[] = new byte[8];
			getRandom(sbuf);
			pi.secret1 = Base64.encodeToString(sbuf, Base64.DEFAULT);
			resp.put(KEY_SECRET, pi.secret1);
			resp.put(KEY_PINNONCE, pi.pinnonce);
			JSONObject info = getInfo();
			if (info!=null)
				resp.put(KEY_INFO, info);

			Message r = new Message(Message.Type.MANAGEMENT, null, null, resp.toString());
			pi.pc.sendMessage(r);
			
			pi.state = PeerState.STATE_PEER_DONE;
			pi.detail = null;

			broadcastPeerState(pi);

			return true;
			
		} catch (JSONException e) {
			// shouldn't happen!
			Log.e(TAG,"JSON error (shoulnd't be): "+e);
		}		
		return false;
	}
	/** get info to pass to peer */
	private synchronized JSONObject getInfo() {
		try {
			JSONObject info = new JSONObject();
			// has address?
			WifiInfo wifiinfo = wifi.getConnectionInfo();
			String wifimac = wifiinfo.getMacAddress();
			if (wifimac!=null)
				info.put(KEY_WIFIMAC, wifi);
			// note: cannot get BluetoothAdapter from comms thread if not a looper
			String btmac = service.getBtMac();
			if(btmac!=null)	
				info.put(KEY_BTMAC, btmac);				
			String imei = service.getImei();
			if (imei!=null)
				info.put(KEY_IMEI, imei);
			return info;
		} catch (JSONException e) {
			// shouldn't happen!
			Log.e(TAG,"JSON error (shoulnd't be): "+e);
		}		
		return null;
	}
	private boolean handleFirstMessage(ClientInfo ci, Message m) {
		if (m.type!=Message.Type.MANAGEMENT) {
			Log.w(TAG,"Received first message of type "+m.type);
			removeClient(ci);
			return false;
		}
		
		try {
			JSONObject msg = new JSONObject(m.body);
			String type = msg.getString(KEY_TYPE);
			if (MSG_INIT_PEER_REQ.equals(type)) {
				return handlePeerReq(ci, msg);
			}
			else {
				Log.w(TAG,"Received first management message of type "+type);
				removeClient(ci);
				return false;
			}
		} catch (JSONException e) {
			Log.w(TAG,"Error parsing JSON MANAGEMENT message: "+e);
			removeClient(ci);
			return false;
		}
	}
	private static int peerRequestNotificationId = 2;
	/** called from handleFirstMessage on receipt of init_peer_req message by client */
	private synchronized boolean handlePeerReq(ClientInfo ci, JSONObject msg) {
		try {
			ci.id = msg.getString(KEY_ID);
			ci.name =msg.getString(KEY_NAME);
			ci.port = msg.getInt(KEY_PORT);
			ci.pindigest = msg.getString(KEY_PINDIGEST);
		} catch (JSONException e) {
			Log.w(TAG,"Error unpacking init_peer_req: "+e);
			removeClient(ci);
			return false;
		}
		// Notification?
		// create taskbar notification
		int icon = R.drawable.notification_icon;
		CharSequence tickerText = "eer request from "+ci.name;
		long when = System.currentTimeMillis();

		Notification notification = new Notification(icon, tickerText, when);
		
		Context context = service;
		CharSequence contentTitle = "Peer request from "+ci.name;
		CharSequence contentText = "Accept or reject peer request from "+ci.name;
		Intent notificationIntent = new Intent(service,  PeerRequestActivity.class);
		notificationIntent.putExtra(EXTRA_ID, ci.id);
		notificationIntent.putExtra(EXTRA_NAME, ci.name);
		PendingIntent contentIntent = PendingIntent.getActivity(service, 0, notificationIntent, 0);

		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
		peerRequestNotificationId++;
		ci.notificationId = peerRequestNotificationId;
		NotificationManager mNotificationManager = (NotificationManager) service.getSystemService(Service.NOTIFICATION_SERVICE);
		//service.(ci.notificationId, notification);	
		mNotificationManager.notify(ci.notificationId, notification);
		ci.state = ClientState.STATE_WAITING_FOR_PIN;
		
		return true;
	}
	/** called on receipt of message after resp_peer_pin by client */
	private boolean handlePeerPinResponse(ClientInfo ci, Message m) {
		if (m.type!=Message.Type.MANAGEMENT) {
			Log.w(TAG, "Received "+m.type+" message after resp_peer_pin");
			removeClient(ci);
			return false;
		}
		String pinnonce = null;
		JSONObject peerInfo = null;
		String secret1 = null;
		try {
			JSONObject msg = new JSONObject(m.body);
			String type = msg.getString(KEY_TYPE);
			if (!MSG_INIT_PEER_DONE.equals(type)) {
				Log.w(TAG, "received "+type+" after resp_peer_pin");
				removeClient(ci);
				return false;
			}
			pinnonce = msg.getString(KEY_PINNONCE);
			peerInfo = msg.getJSONObject(KEY_INFO);
			secret1 = msg.getString(KEY_SECRET);
		}
		catch (JSONException e) {
			Log.w(TAG,"Error unpacking resp_peer_pin response: "+e);
			removeClient(ci);
			return false;			
		}
		Log.i(TAG,"Received init_peer_done in state peer_pin with info="+peerInfo);
		// check pin
		try {
			byte nbuf[] = Base64.decode(pinnonce, Base64.DEFAULT);
			messageDigest.reset();
			messageDigest.update(nbuf);
			messageDigest.update(ci.pin.getBytes("UTF-8"));
			byte dbuf[] = messageDigest.digest();
			String pindigest = Base64.encodeToString(dbuf, Base64.DEFAULT);
			if (!ci.pindigest.equals(pindigest)) {
				Log.i(TAG,"Reject peer with incorrect pindigest");
				removeClient(ci);
				return false;
			}
		} catch (UnsupportedEncodingException e) {
			// shouldn't happen!
			Log.e(TAG,"Unsupported encoding (shoulnd't be): "+e);
		}
		// send response
		String secret2 =  null;
		try {
			JSONObject resp = new JSONObject();
			resp.put(KEY_TYPE, MSG_RESP_PEER_DONE);
			JSONObject info = getInfo();
			if (info!=null)
				resp.put(KEY_INFO, info);
			byte sbuf[] = new byte[8];
			getRandom(sbuf);
			secret2 = Base64.encodeToString(sbuf, Base64.DEFAULT);
			resp.put(KEY_SECRET, secret2);
			
			Message r = new Message(Message.Type.MANAGEMENT, null, null, resp.toString());
			ci.pc.sendMessage(r);
			
		}
		catch (JSONException e) {
			// shouldn't happen!
			Log.e(TAG,"JSON error (shoulnd't be): "+e);			
		}
		
		// convert to PeerInfo
		PeerInfo pi = new PeerInfo(ci);
		pi.secret1 = secret1;
		pi.secret2 = secret2;
		pi.state = PeerState.STATE_PEERED;
		pi.peerInfo = peerInfo;
		pi.src = ci.pc.getSocketChannel().socket().getInetAddress();
		Log.i(TAG,"Converted ClientInfo to PeerInfo");
		
		clients.remove(ci);
		peers.add(pi);
		pi.pc.attach(pi);

		Intent i = new Intent(ACTION_PEERS_CHANGED);
		service.sendBroadcast(i);

		// don't handle more messages as a client
		return false;
	}
	/** called on receipt of message after init_peer_done in peer */
	private boolean handlePeerDoneResponse(PeerInfo pi, Message m) {
		if (m.type!=Message.Type.MANAGEMENT) {
			failPeer(pi, "Received init_peer_done response of type "+m.type);
			return false;
		}
		try {
			JSONObject msg = new JSONObject(m.body);
			String type = msg.getString(KEY_TYPE);
			if (!MSG_RESP_PEER_DONE.equals(type)) {
				failPeer(pi, "Received init_peer_done response "+type);
				return false;
			}
			pi.secret2 = msg.getString(KEY_SECRET);
			pi.peerInfo = msg.getJSONObject(KEY_INFO);
		} catch (JSONException e) {
			failPeer(pi, "Error in resp_peer_done message: "+e);
			return false;
		}
		// all done
		pi.state = PeerState.STATE_PEERED;
		pi.detail = null;
		broadcastPeerState(pi);

		return true;
	}
	private String getDeviceId() {
		return service.getDeviceId();
	}
	private static enum ClientState {
		STATE_NEGOTIATE_PROTOCOL, // waiting for HELLO
		STATE_NEGOTIATED_PROTOCOL, // Had HELLO & responded; wait for next
		STATE_PEER_NOPIN,
		STATE_PEER_PIN,
		STATE_PEER_DONE, STATE_WAITING_FOR_PIN
	}

	private static class ClientInfo {
		public int notificationId;
		ClientState state;
		// connect
		PeerConnection pc;
		// peer request
		public String name;
		public String id;
		public int port;
		String pindigest;
		String pin;
		
		public ClientInfo(PeerConnection pc) {
			this.pc = pc;
			state = ClientState.STATE_NEGOTIATE_PROTOCOL;
		}
		
		public ClientInfo(ClientInfo ci) {
			state = ci.state;
			name = ci.name;
			id = ci.id;
			port = ci.port;
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
			newPeerConnection.attach(ci);
			clients.add(ci);
			Log.d(TAG,"Accepted new connection from "+newPeerConnection.getSocketChannel().socket().getInetAddress().getHostAddress()+":"+newPeerConnection.getSocketChannel().socket().getPort());			
		}
	};

	/** public API - peer request accept */
	public synchronized void acceptPeerRequest(Intent triggerIntent, String pin) {
		// TODO
		ClientInfo ci = getClientInfo(triggerIntent);
		if (ci==null) {
			Log.w(TAG,"Could not find ClientInfo for reject with id "+triggerIntent.getExtras().getString(EXTRA_ID));
			return;
		}
		ci.pin = pin;
		// notification?
		hideClientNotification(ci);
		// next step
		// create peer request
		JSONObject msg = new JSONObject();
		try {
			msg.put(KEY_TYPE, MSG_RESP_PEER_PIN);
			msg.put(KEY_ID, getDeviceId());
			msg.put(KEY_PORT, serverPort);
			msg.put(KEY_NAME, service.getDeviceName());
			msg.put(KEY_PIN, pin);
			Message m = new Message(Message.Type.MANAGEMENT, null, null, msg.toString());
			ci.pc.sendMessage(m);
			ci.state = ClientState.STATE_PEER_PIN;			
		}
		catch (JSONException e) {
			Log.e(TAG,"JSON error (shouldn't be) creating resp_peer_pin message: "+e);
		}
	}
	/** public API - peer request reject */
	public synchronized void rejectPeerRequest(Intent triggerIntent) {
		// TODO
		ClientInfo ci = getClientInfo(triggerIntent);
		if (ci==null) {
			Log.w(TAG,"Could not find ClientInfo for reject with id "+triggerIntent.getExtras().getString(EXTRA_ID));
			return;
		}
		// notification?
		hideClientNotification(ci);
		// reject
		removeClient(ci);
	}
	private synchronized ClientInfo getClientInfo(Intent triggerIntent) {
		String id = triggerIntent.getExtras().getString(EXTRA_ID);
		for (ClientInfo ci : clients) {
			if (ci.id.equals(id))
				return ci;
		}
		return null;
	}
	private synchronized void hideClientNotification(ClientInfo ci) {
		if (ci.notificationId!=0) {
			NotificationManager nm = (NotificationManager)service.getSystemService(Service.NOTIFICATION_SERVICE);
			nm.cancel(ci.notificationId);
			ci.notificationId = 0;
		}
	}
}
