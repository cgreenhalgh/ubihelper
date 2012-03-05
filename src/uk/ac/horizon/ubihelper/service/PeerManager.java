/**
 * 
 */
package uk.ac.horizon.ubihelper.service;

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

import uk.ac.horizon.ubihelper.R;
import uk.ac.horizon.ubihelper.R.drawable;
import uk.ac.horizon.ubihelper.dns.DnsClient;
import uk.ac.horizon.ubihelper.dns.DnsProtocol;
import uk.ac.horizon.ubihelper.dns.DnsUtils;
import uk.ac.horizon.ubihelper.dns.DnsProtocol.RR;
import uk.ac.horizon.ubihelper.net.Message;
import uk.ac.horizon.ubihelper.net.OnPeerConnectionListener;
import uk.ac.horizon.ubihelper.net.PeerConnection;
import uk.ac.horizon.ubihelper.net.PeerConnectionScheduler;
import uk.ac.horizon.ubihelper.protocol.ClientInfo;
import uk.ac.horizon.ubihelper.protocol.ClientState;
import uk.ac.horizon.ubihelper.protocol.MessageUtils;
import uk.ac.horizon.ubihelper.protocol.PeerInfo;
import uk.ac.horizon.ubihelper.protocol.ProtocolManager;
import uk.ac.horizon.ubihelper.protocol.ProtocolManager.ClientConnectionListener;
import uk.ac.horizon.ubihelper.service.PeerManager.SearchInfo;
import uk.ac.horizon.ubihelper.ui.PeerRequestInfoActivity;
import uk.ac.horizon.ubihelper.ui.PeerRequestActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
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
	public static final String TAG = "ubihelper-peermgr";
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
	private MessageDigest messageDigest;
	private MyProtocolManager protocol;
	private SQLiteDatabase database;
	/** partial, id -> PeerInfo */
	private HashMap<String,PeerInfo> peerInfoCache = new HashMap<String,PeerInfo>();
	
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
	public static final String ACTION_PEER_REQUEST_STATE_CHANGED = "uk.ac.horizon.ubihelper.action.PEER_REQUEST_STATE_CHANGED";
	public static final String EXTRA_NAME = "uk.ac.horizon.ubihelper.extra.NAME";
	public static final String EXTRA_SOURCEIP = "uk.ac.horizon.ubihelper.extra.SOURCEIP";
	public static final String EXTRA_PORT = "uk.ac.horizon.ubihelper.extra.PORT";
	public static final String EXTRA_PEER_STATE = "uk.ac.horizon.ubihelper.extra.PEER_STATE";
	public static final String EXTRA_DETAIL = "uk.ac.horizon.ubihelper.extra.DETAIL";
	public static final String EXTRA_ID = "uk.ac.horizon.ubihelper.extra.ID";
	public static final String ACTION_PEER_REQUESTS_CHANGED = "uk.ac.horizon.ubihelper.action.PEER_REQUESTS_CHANGED";

	public static final String ACTION_PEERS_CHANGED = "uk.ac.horizon.ubihelper.action.PEERS_CHANGED";
	public static final String ACTION_PEER_STATE_CHANGED = "uk.ac.horizon.ubihelper.action.PEER_STATE_CHANGED";

	private static final long MAX_QUERY_AGE = 15000;
	
	/** info keys */
	private static final String KEY_IMEI = "imei";
	private static final String KEY_WIFIMAC = "wifimac";
	private static final String KEY_BTMAC = "bcmac";
	
	public PeerManager(Service service) {		
		this.service = service;
		// Note: meant to open database on another thread?!
		database = new PeersOpenHelper(service).getWritableDatabase();

		protocol = new MyProtocolManager();
		peerConnectionListener = new OnPeerConnectionListener(protocol);
		
		wifi = (WifiManager)service.getSystemService(Service.WIFI_SERVICE);
		
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
	public SQLiteDatabase getDatabase() {
		return database;
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
		if (selector!=null) {
			selector.close();
			selector = null;
		}
		if (database!=null) {
			database.close();
			database = null;
		}
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
	/** return OK if started; false if couldn't, e.g. no wifi active */
	public synchronized boolean startSearch() {
		closeSearchInternal();
		DnsProtocol.Query query = new DnsProtocol.Query();
		query.name = DnsUtils.getServiceDiscoveryName();
		query.rclass = DnsProtocol.CLASS_IN;
		query.type = DnsProtocol.TYPE_PTR;
		searchClient = new DnsClient(query, true);
		searchClient.setOnChange(onSearchChangeListener);
		NetworkInterface ni = getNetworkInterface();
		if (ni==null) {
			Log.w(TAG,"Could not start Dns search - no NetworkInterface");
			return false;
		}
		searchClient.setNetworkInterface(ni);
		searchClient.start();
		searchStarted = true;
		Intent i = new Intent(ACTION_SEARCH_STARTED);
		service.sendBroadcast(i);
		return true;
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
	public static enum PeerRequestState {
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
		STATE_PEERED, 
		STATE_PEERED_UNTRUSTED
	}
	/** peer info */
	public static class PeerRequestInfo implements Cloneable {
		public int _id = -1;
		public PeerRequestState state;
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
		
		PeerRequestInfo(String instanceName, InetAddress src) {
			this.instanceName = instanceName;
			this.src = src;
			state = PeerRequestState.STATE_SEARCHED_ADD;
		}		
		PeerRequestInfo(PeerRequestInfo pi) {
			id = pi.id;
			state = pi.state;
			instanceName = pi.instanceName;
			src = pi.src;
			port = pi.port;
			detail = pi.detail;
		}
		public PeerRequestInfo(ClientInfo ci) {
			id = ci.id;
			instanceName = ci.name;
			pc = ci.pc;
			port = ci.port;
		}
	}
	private void broadcastPeerState(PeerRequestInfo pi) {
		Intent i = new Intent(ACTION_PEER_REQUEST_STATE_CHANGED);
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
	private ArrayList<PeerRequestInfo> peerRequests = new ArrayList<PeerRequestInfo>();

	/** public API - list peers */
	public synchronized List<PeerRequestInfo> getPeerRequests() {
		ArrayList<PeerRequestInfo> rpeers = new ArrayList<PeerRequestInfo>();
		for (PeerRequestInfo pi : peerRequests) {
			rpeers.add(new PeerRequestInfo(pi));
		}
		return rpeers;
	}
	/** public API - get info on peer */
	public synchronized PeerRequestInfo getPeer(Intent i) {
		String sourceip = i.getExtras().getString(EXTRA_SOURCEIP);
		String name = i.getExtras().getString(EXTRA_NAME);
		for (PeerRequestInfo pi : peerRequests) {
			if (pi.instanceName.equals(name) && pi.src.getHostAddress().equals(sourceip))
				return pi;
		}
		return null;
	}
	/** public API - matches */
	public static synchronized boolean matches(PeerRequestInfo pi, Intent i) {
		String sourceip = i.getExtras().getString(EXTRA_SOURCEIP);
		String name = i.getExtras().getString(EXTRA_NAME);
		if (pi.instanceName.equals(name) && pi.src.getHostAddress().equals(sourceip))
			return true;
		return false;
	}
	/** public API - matches */
	public static synchronized boolean matches(PeerRequestInfo pi, SearchInfo si) {
		if (pi.instanceName.equals(si.name) && pi.src.equals(si.src))
			return true;
		return false;
	}
	/** public API - start adding a discovered peer */
	public synchronized void addPeer(SearchInfo peerInfo) {
		// already in progress?
		for (PeerRequestInfo pi : peerRequests) {
			if (matches(pi, peerInfo)) {
				// kick?
				return;
			}
		}
		PeerRequestInfo pi = new PeerRequestInfo(peerInfo.name, peerInfo.src);
		peerRequests.add(pi);
		Intent i = new Intent(ACTION_PEER_REQUESTS_CHANGED);
		service.sendBroadcast(i);
		updatePeer(pi);
	}
	private synchronized void updatePeer(PeerRequestInfo pi) {
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
	private OnPeerConnectionListener peerConnectionListener;
	private class OnPeerConnectionListener extends ClientConnectionListener {
		public OnPeerConnectionListener(ProtocolManager pm) {
			super(pm);
		}

		public void onRecvMessage(PeerConnection pc) {
			if (pc.attachment() instanceof PeerRequestInfo) {
				PeerRequestInfo pi = (PeerRequestInfo)pc.attachment();
				Log.d(TAG,"onMessage PeerInfo "+pi);
				checkMessages(pi);
			}
			else super.onRecvMessage(pc);
		}
		
		public void onFail(PeerConnection pc, boolean sendFailed,
				boolean recvFailed, boolean connectFailed) {
			if (pc.attachment() instanceof PeerRequestInfo) {
				PeerRequestInfo pi = (PeerRequestInfo)pc.attachment();
				Log.d(TAG, "onFail PeerInfo "+pi);
				pi.state = PeerRequestState.STATE_CONNECTING_FAILED;
				pi.detail = null;
				updatePeer(pi);
			}
			else super.onFail(pc, sendFailed, recvFailed, connectFailed);
		}

		public void onConnected(PeerConnection pc) {
			if (pc.attachment() instanceof PeerRequestInfo) {
				PeerRequestInfo pi = (PeerRequestInfo)pc.attachment();
				if (pi.state==PeerRequestState.STATE_CONNECTING) {
					Log.d(TAG, "onConnected -> connected PeerInfo "+pi);
					pi.state = PeerRequestState.STATE_CONNECTED;
					pi.detail = null;
					broadcastPeerState(pi);
					updatePeer(pi);
				}
				else
					Log.d(TAG,"onConnected "+pi.state+" PeerInfo "+pi);
			}
			else super.onConnected(pc);
		}
	};
	
	private synchronized void connectPeer(PeerRequestInfo pi) {
		if (pi.src==null) {
			pi.state = PeerRequestState.STATE_CONNECTING_FAILED;
			pi.detail = "IP unknown";
			updatePeer(pi);
			return;
		}
		pi.state = PeerRequestState.STATE_CONNECTING;
		pi.detail = "connectPeer()";
		try {
			Log.d(TAG,"Connect to "+pi.src.getHostAddress()+":"+pi.port);
			pi.pc = selector.connect(new InetSocketAddress(pi.src, pi.port), peerConnectionListener, pi);
			//Log.d(TAG,"Connect done="+done);
			//pi.detail = "4 (done="+done+")";
			boolean done = pi.pc.isConnected();
			if (done) {
				//Toast.makeText(service, "Connected!", Toast.LENGTH_SHORT).show();
				pi.state = PeerRequestState.STATE_CONNECTED;
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
			pi.state = PeerRequestState.STATE_CONNECTING_FAILED;
			pi.detail = e.getMessage();
			updatePeer(pi);
		}		
	}
	
	private class MyProtocolManager extends ProtocolManager {

		@Override
		public void removeClient(ClientInfo ci) {
			hideClientNotification(ci);
			super.removeClient(ci);
			clients.remove(ci);
		}
		@Override
		protected byte [] base64Decode(String str) {
			return Base64.decode(str, Base64.DEFAULT);
		}
		@Override
		protected String base64Encode(byte [] bs) {
			return Base64.encodeToString(bs, Base64.DEFAULT);
		}
		/** prompt for PIN, e.g. from user. return false (default) if cannot prompt. */
		@Override
		protected boolean clientPromptForPin(ClientInfo ci) {
			// Notification?
			// create taskbar notification
			int icon = R.drawable.peer_request_notification_icon;
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
			
			return true;
		}
		/** called when successfully peered */
		@Override
		protected boolean clientHandlePeered(ClientInfo ci) {
			// convert to PeerInfo
			PeerInfo pi = new PeerInfo();
			pi.createdTimestamp = System.currentTimeMillis();
			pi.info = ci.peerInfo;
			if (pi.info!=null) {
				try {
					if (pi.info.has(KEY_BTMAC))
						pi.btmac = pi.info.getString(KEY_BTMAC);
					if (pi.info.has(KEY_WIFIMAC))
						pi.wifimac = pi.info.getString(KEY_WIFIMAC);
					if (pi.info.has(KEY_IMEI))
						pi.imei = pi.info.getString(KEY_IMEI);
				}
				catch (JSONException e) {
					Log.e(TAG,"Unexpected JSON error unpacking peerInfo: "+e);
				}
			}
			pi.secret = protocol.combineSecrets(ci.secret1, ci.secret2);
			pi.ip = ci.pc.getSocketChannel().socket().getInetAddress().getHostAddress();
			pi.ipTimestamp = System.currentTimeMillis();
			pi.port = ci.pc.getSocketChannel().socket().getPort();
			pi.portTimestamp = pi.ipTimestamp;
			pi.name = ci.name;
			pi.id = ci.id;
			pi.trusted = true;
			pi.nickname = pi.name;
			Log.i(TAG,"Converted ClientInfo to PeerInfo");
			
			clients.remove(ci);
			addPeer(pi);
//			peerCache.put(pi.id, pi);
//			pi.pc.attach(pi);
//
//			Intent i = new Intent(ACTION_PEER_REQUESTS_CHANGED);
//			service.sendBroadcast(i);

			// don't handle more messages as a client
			return false;
		}
		@Override
		protected JSONObject getInfo() {
			return PeerManager.this.getInfo();
		}
		@Override
		protected String getName() {
			return service.getDeviceName();
		}
		@Override
		protected int getPort() {
			return serverPort;
		}
		@Override
		protected String getId() {
			return getDeviceId();
		}

	}
	protected synchronized void failPeer(PeerRequestInfo pi, String detail) {
		pi.state = PeerRequestState.STATE_CONNECTING_FAILED;
		pi.detail = detail;
		broadcastPeerState(pi);
		removePeer(pi);
	}
	private synchronized void addPeer(PeerInfo pi) {
		// in database?
		if (pi.id==null) {
			Log.e(TAG,"addPeer with null id");
			return;
		}
		database.beginTransaction();
		try {
			PeerInfo pi2 = PeersOpenHelper.getPeerInfo(database, pi.id);
			if (pi2!=null) {
				// update!
				pi._id = pi2._id;
				pi.enabled = pi2.enabled;
				Log.d(TAG,"Updating peer "+pi.id+" on addPeer");
				PeersOpenHelper.updatePeerInfo(database, pi);
			}
			else {
				// add
				PeersOpenHelper.addPeerInfo(database, pi);
			}
			
			// TODO add to peers
			// TODO broadcast etc.!
		}
		finally {
			database.endTransaction();
		}
	}
	protected synchronized void checkMessages(PeerRequestInfo pi) {
		Message m = null;
		while ((m=pi.pc.getMessage())!=null) {
			switch (pi.state){
			case STATE_NEGOTIATE_PROTOCOL: {
				boolean ok = MessageUtils.checkNegotiateProtocolResponse(m);
				if (!ok) {
					failPeer(pi, "Incompatible protocol: "+m.body);
					return;
				}
				pi.state = PeerRequestState.STATE_PEER_REQ;
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
	private synchronized void startSrvDiscovery(final PeerRequestInfo pi) {
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
			pi.state = PeerRequestState.STATE_SRV_DISCOVERY;
			broadcastPeerState(pi);
			fdc.start();
			return;
		}
		else {
			pi.state = PeerRequestState.STATE_SRV_DISCOVERY;
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
		ArrayList<PeerRequestInfo> peers2 = new ArrayList<PeerRequestInfo>();
		peers2.addAll(peerRequests);
		for (int i=0; i<peers2.size(); i++) {
			PeerRequestInfo pi = peers2.get(i);
			if (pi.state==PeerRequestState.STATE_SRV_DISCOVERY && pi.src.equals(src)) {
				LinkedList<DnsProtocol.RR> as = dc.getAnswers();
				if (as.size()==0) {
					pi.state = PeerRequestState.STATE_SRV_DISCOVERY_FAILED;
					updatePeer(pi);
				} else {
					try {
						DnsProtocol.SrvData srv = DnsProtocol.srvFromData(as.get(0).rdata);
						pi.state = PeerRequestState.STATE_SRV_FOUND;
						pi.port = srv.port;
						if (!srv.target.equals(src.getHostAddress())) {
							Log.w(TAG,"SRV returned different IP: "+srv.target+" vs "+src.getHostAddress());
						}
						updatePeer(pi);
					} catch (IOException e) {
						Log.w(TAG,"Error parsing SRV data: "+e.getMessage());
						pi.state = PeerRequestState.STATE_SRV_DISCOVERY_FAILED;					
						updatePeer(pi);
					}					
				}
			}
		}
	}
	private synchronized void removePeer(PeerRequestInfo pi) {
		if (peerRequests.remove(pi))
		{
			Intent i = new Intent(ACTION_PEER_REQUESTS_CHANGED);
			service.sendBroadcast(i);
		}
		if (pi.pc!=null) {
			pi.pc.close();
			pi.pc = null;
		}
	}
	
	private synchronized void negotiateProtocol(PeerRequestInfo pi) {
		// Start negotiation on PeerConnection...
		// protocol hello
		String protocol = Message.getHelloBody();
		Message m = new Message(Message.Type.HELLO, null, null, protocol);
		pi.pc.sendMessage(m);
		pi.state = PeerRequestState.STATE_NEGOTIATE_PROTOCOL;
		broadcastPeerState(pi);
		return;
	}		
	private synchronized void sendPeerRequest(PeerRequestInfo pi) {
		
		// create peer request
		JSONObject msg = new JSONObject();
		try {
			msg.put(MessageUtils.KEY_TYPE, MessageUtils.MSG_INIT_PEER_REQ);
			// pass key
			byte pbuf[] = new byte[4];
			protocol.getRandom(pbuf);
			StringBuilder pb = new StringBuilder();
			for (int i=0; i<pbuf.length; i++) 
				pb.append((char)('0'+((pbuf[i]&0xff) % 10)));
			pi.pin = pb.toString();
			// pin nonce and digest
			byte nbuf[] = new byte[8];
			protocol.getRandom(nbuf);
			pi.pinnonce = Base64.encodeToString(nbuf, Base64.DEFAULT);
			messageDigest.reset();
			messageDigest.update(nbuf);
			messageDigest.update(pi.pin.getBytes("UTF-8"));
			byte dbuf[] = messageDigest.digest();
			pi.pindigest = Base64.encodeToString(dbuf, Base64.DEFAULT);
			msg.put(MessageUtils.KEY_PINDIGEST, pi.pindigest);
			
			msg.put(MessageUtils.KEY_ID, getDeviceId());
			msg.put(MessageUtils.KEY_NAME, service.getDeviceName());
			msg.put(MessageUtils.KEY_PORT, this.serverPort);
			Message m = new Message(Message.Type.MANAGEMENT, null, null, msg.toString());
			pi.pc.sendMessage(m);
			
			pi.state = PeerRequestState.STATE_PEER_REQ;
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
	private boolean handlePeerReqResponse(PeerRequestInfo pi, Message m) {
		if (m.type!=Message.Type.MANAGEMENT) {
			failPeer(pi, "Received init_peer_req response of type "+m.type);
			return false;
		}
		String pinGuess = null;
		try {
			JSONObject msg = new JSONObject(m.body);
			String type = msg.getString(MessageUtils.KEY_TYPE);
			if (!MessageUtils.MSG_RESP_PEER_PIN.equals(type)) {
				if (MessageUtils.MSG_RESP_PEER_NOPIN.equals(type)) {
					return handlePeerNopin(pi, msg);
				}
				failPeer(pi, "Received init_peer_req response "+type);
				// TODO resp_peer_known
				return false;
			}
			// id, name, port, pin
			pinGuess = msg.getString(MessageUtils.KEY_PIN);
			pi.id = msg.getString(MessageUtils.KEY_ID);
			// TODO known IDs?
			int port = msg.getInt(MessageUtils.KEY_PORT);
			if (port!=pi.port)
				Log.w(TAG,"resp_peer_pin has different port: "+port+" vs "+pi.port);
			String name = msg.getString(MessageUtils.KEY_NAME);
			if (pi.instanceName==null)
				pi.instanceName = name;
			else if (!name.equals(pi.instanceName)) 
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
			resp.put(MessageUtils.KEY_TYPE, MessageUtils.MSG_INIT_PEER_DONE);
			byte sbuf[] = new byte[8];
			protocol.getRandom(sbuf);
			pi.secret1 = Base64.encodeToString(sbuf, Base64.DEFAULT);
			resp.put(MessageUtils.KEY_SECRET, pi.secret1);
			resp.put(MessageUtils.KEY_PINNONCE, pi.pinnonce);
			JSONObject info = getInfo();
			if (info!=null)
				resp.put(MessageUtils.KEY_INFO, info);

			Message r = new Message(Message.Type.MANAGEMENT, null, null, resp.toString());
			pi.pc.sendMessage(r);
			
			pi.state = PeerRequestState.STATE_PEER_DONE;
			pi.detail = null;

			broadcastPeerState(pi);

			return true;
			
		} catch (JSONException e) {
			// shouldn't happen!
			Log.e(TAG,"JSON error (shoulnd't be): "+e);
		}		
		return false;
	}
	/** nopin response from peer in response to pin request */
	private synchronized boolean handlePeerNopin(PeerRequestInfo pi, JSONObject msg) {
		try {
			pi.id = msg.getString(MessageUtils.KEY_ID);
			// TODO known IDs?
			int port = msg.getInt(MessageUtils.KEY_PORT);
			if (port!=pi.port)
				Log.w(TAG,"resp_peer_nopin has different port: "+port+" vs "+pi.port);
			String name = msg.getString(MessageUtils.KEY_NAME);
			if (pi.instanceName==null)
				pi.instanceName = name;
			else if (!name.equals(pi.instanceName)) 
				Log.w(TAG,"resp_peer_nopin has different name: "+name+" vs "+pi.instanceName);
			pi.secret2 = msg.getString(MessageUtils.KEY_SECRET);
			pi.peerInfo = msg.getJSONObject(MessageUtils.KEY_INFO);
		} catch (JSONException e) {
			failPeer(pi, "Error in resp_peer_pin message: "+e);
			return false;
		}
		Log.i(TAG,"Received resp_peer_nopin in state peer_req");

		pi.state = PeerRequestState.STATE_PEERED_UNTRUSTED;
		pi.detail = "no pin";

		peerRequests.remove(pi);
		Intent bi = new Intent(ACTION_PEER_REQUESTS_CHANGED);
		service.sendBroadcast(bi);
		updatePeer(pi);
		
		addPeer(pi);
		
		return true;
	}
	private void addPeer(PeerRequestInfo preq) {
		// convert to PeerInfo
		PeerInfo pi = new PeerInfo();
		pi.createdTimestamp = System.currentTimeMillis();
		pi.info = preq.peerInfo;
		if (pi.info!=null) {
			try {
				if (pi.info.has(KEY_BTMAC))
					pi.btmac = pi.info.getString(KEY_BTMAC);
				if (pi.info.has(KEY_WIFIMAC))
					pi.wifimac = pi.info.getString(KEY_WIFIMAC);
				if (pi.info.has(KEY_IMEI))
					pi.imei = pi.info.getString(KEY_IMEI);
			}
			catch (JSONException e) {
				Log.e(TAG,"Unexpected JSON error unpacking peerInfo: "+e);
			}
		}
		pi.secret = protocol.combineSecrets(preq.secret1, preq.secret2);
		pi.ip = preq.src.getHostAddress();
		pi.ipTimestamp = System.currentTimeMillis();
		pi.port = preq.port;
		pi.portTimestamp = pi.ipTimestamp;
		pi.name = preq.instanceName;
		pi.id = preq.id;
		pi.trusted = preq.state==PeerRequestState.STATE_PEERED;
		pi.nickname = pi.name;
		Log.i(TAG,"Converted PeerRequestInfo to PeerInfo");
		
		addPeer(pi);
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
	private static int peerRequestNotificationId = 2;
	/** called on receipt of message after init_peer_done in peer */
	private synchronized boolean handlePeerDoneResponse(PeerRequestInfo pi, Message m) {
		if (m.type!=Message.Type.MANAGEMENT) {
			failPeer(pi, "Received init_peer_done response of type "+m.type);
			return false;
		}
		try {
			JSONObject msg = new JSONObject(m.body);
			String type = msg.getString(MessageUtils.KEY_TYPE);
			if (!MessageUtils.MSG_RESP_PEER_DONE.equals(type)) {
				failPeer(pi, "Received init_peer_done response "+type);
				return false;
			}
			pi.secret2 = msg.getString(MessageUtils.KEY_SECRET);
			pi.peerInfo = msg.getJSONObject(MessageUtils.KEY_INFO);
		} catch (JSONException e) {
			failPeer(pi, "Error in resp_peer_done message: "+e);
			return false;
		}
		// all done
		pi.state = PeerRequestState.STATE_PEERED;
		pi.detail = null;
		
		peerRequests.remove(pi);
		Intent bi = new Intent(ACTION_PEER_REQUESTS_CHANGED);
		service.sendBroadcast(bi);
		updatePeer(pi);
		
		addPeer(pi);

		return true;
	}
	private String getDeviceId() {
		return service.getDeviceId();
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
		Message m = MessageUtils.getRespPeerPin(getDeviceId(), serverPort, service.getDeviceName(), pin);
		ci.pc.sendMessage(m);
		ci.state = ClientState.STATE_PEER_PIN;			
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
		protocol.removeClient(ci);
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
	/** public API - start peer add for specified host/port. Return PeerReq8estInfoActivity intent */
	public synchronized Intent addPeerRequest(InetAddress host, int port) {
		Intent i = new Intent(service, PeerRequestInfoActivity.class);
		i.putExtra(EXTRA_SOURCEIP, host.getHostAddress());
		i.putExtra(EXTRA_PORT, port);
		String name =  "Unknown (manually added)";
		i.putExtra(EXTRA_NAME, name);
		
		PeerRequestInfo pi = new PeerRequestInfo(name, host);
		pi.port = port;
		pi.state = PeerRequestState.STATE_SRV_FOUND;

		peerRequests.add(pi);
		
		Intent bi = new Intent(ACTION_PEER_REQUESTS_CHANGED);
		service.sendBroadcast(bi);
		updatePeer(pi);

		return i;
	}
}
