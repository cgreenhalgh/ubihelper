/**
 * 
 */
package uk.ac.horizon.ubihelper.j2se;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.nio.channels.ServerSocketChannel;
import java.util.logging.Logger;

import uk.ac.horizon.ubihelper.dns.DnsClient;
import uk.ac.horizon.ubihelper.dns.DnsProtocol;
import uk.ac.horizon.ubihelper.dns.DnsServer;
import uk.ac.horizon.ubihelper.dns.DnsUtils;
import uk.ac.horizon.ubihelper.dns.DnsProtocol.SrvData;
import uk.ac.horizon.ubihelper.net.PeerConnection;
import uk.ac.horizon.ubihelper.net.PeerConnectionScheduler;
import uk.ac.horizon.ubihelper.protocol.ClientInfo;
import android.util.Log;

/** Desktop server.
 * 
 * @author cmg
 *
 */
public class Server {
	static Logger logger = Logger.getLogger(Server.class);

	private ServerSocketChannel serverSocket;
	private PeerConnectionScheduler selector;
	private boolean failed = false;
	private int serverPort;
	private DnsServer dns;
	
	public Server() {
	}
	public void init() {
		// create channels
		// create server socket
		try {
			serverSocketChannel = ServerSocketChannel.open();
			ServerSocket ss = serverSocketChannel.socket();
			ss.bind(new InetSocketAddress(InetAddress.getByName("0.0.0.0"),0));
			serverPort = ss.getLocalPort();
			logger.info("Open server socket on port "+serverPort);
			serverSocketChannel.configureBlocking(false);
		} catch (IOException e) {
			logger.severe("Error opening ServerSocketChannel: "+e.getMessage());
			failed = true;
			return;
		}
		try {
			selector = new PeerConnectionScheduler(serverSocketChannel);
			selector.setListener(selectorListener);
			selector.start();
		} catch (IOException e) {
			logger.severe("Error starting Selector: "+e.getMessage());
			failed = true;
			return;
		}
		// create and advertise with DnsServer
		dns = new DnsServer();
		NetworkInterface ni = DnsClient.getFirstActiveInterface();
		dns.setNeworkInterface(ni);
		InetAddress ip = ni.getInetAddresses().nextElement();
		logger.info("Binding for multicast to "+ip.getHostAddress());
		
		String servicename = DnsUtils.getServiceDiscoveryName();
		String name = ip.getHostAddress();
		SrvData srv = new SrvData(1, 1, serverPort, name);
		logger.info("Discoverable "+name+" as "+servicename);
		dns.add(new DnsProtocol.RR(servicename, DnsProtocol.TYPE_SRV, 
				DnsProtocol.CLASS_IN, DEFAULT_TTL, DnsProtocol.srvToData(srv)));

		String instancename = "Server on "+ip;
		Log.d(TAG,"Discoverable as "+instancename+" "+servicename);
		DnsProtocol.RR ptrRR = new DnsProtocol.RR(servicename, DnsProtocol.TYPE_PTR, 
				DnsProtocol.CLASS_IN, DEFAULT_TTL, DnsProtocol.ptrToData(instancename, servicename));
		dnsServer.add(ptrRR);

		dns.start();
	}
	
	private PeerConnectionScheduler.Listener selectorListener = new PeerConnectionScheduler.Listener() {
		
		public void onAccept(PeerConnectionScheduler pcs,
				PeerConnection newPeerConnection) {
			ClientInfo ci = new ClientInfo(newPeerConnection);
			newPeerConnection.setOnPeerConnectionListener(peerConnectionListener);
			newPeerConnection.attach(ci);
			//clients.add(ci);
			logger.info("Accepted new connection from "+newPeerConnection.getSocketChannel().socket().getInetAddress().getHostAddress()+":"+newPeerConnection.getSocketChannel().socket().getPort());			
		}
	};


	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Server server = new Server();
		server.init();
	}

}
