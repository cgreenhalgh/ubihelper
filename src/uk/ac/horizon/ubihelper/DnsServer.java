/**
 * 
 */
package uk.ac.horizon.ubihelper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.LinkedList;
import java.util.logging.Logger;

//import android.util.Log;

/** Embedded multicast DNS server
 * @author cmg
 *
 */
public class DnsServer extends Thread {
	//static final String TAG = "ubihelper-dnssvr";
	static Logger logger =  Logger.getLogger(DnsServer.class.getName());
	
	public static final String MDNS_ADDRESS = "224.0.0.251";
	public static final int MDNS_PORT = 5353;

	private static final int MDMS_PACKET_SIZE = 512;
	
	private MulticastSocket socket = null;
	private boolean closed = false;
	private LinkedList<DnsProtocol.RR> rrs = new LinkedList<DnsProtocol.RR>();
	
	public DnsServer() {
	}
	
	public synchronized void add(DnsProtocol.RR r) {
		for (int i=0; i<rrs.size(); i++) {
			DnsProtocol.RR r2 = rrs.get(i);
			if (r2.name.equals(r.name) && r2.rclass==r.rclass && r2.type==r.type) {
				// remove/replace
				rrs.remove(i);
				i--;
			}
		}
		rrs.add(r);
	}
	public synchronized void clear() {
		rrs.clear();
	}
	
	public synchronized boolean isRunning() {
		return socket!=null;
	}
	public synchronized void close() {
		closed = true;
		interrupt();
		closeInternal();		
	}
	private synchronized void closeInternal() {
		if (socket!=null) {
			try {
				socket.close();
			}
			catch (Exception e) {
				/* ignore */
			}
			socket = null;
		}
	}
	
	public void run() {
		while (true) {
			MulticastSocket s = null;
			synchronized (this) {
				if (closed)
					break;
				if (socket==null) {
					try {
						socket = new MulticastSocket(MDNS_PORT);
						socket.setTimeToLive(1);
						socket.setReuseAddress(true);
						// join group?!
						socket.joinGroup(InetAddress.getByName(MDNS_ADDRESS));
						// leave unbound (0.0.0.0)?!
						//Log.d(TAG,"Opened multicast socket "+MDNS_ADDRESS+":"+MDNS_PORT);
						logger.info("Opened multicast socket "+MDNS_ADDRESS+":"+MDNS_PORT);
					}
					catch (Exception e) {
						//Log.e(TAG,"Creating multicast socket on "+MDNS_PORT+": "+e.getMessage());
						logger.warning("Creating multicast socket on "+MDNS_PORT+": "+e.getMessage());
						closeInternal();
						try {
							// avoid racing...
							wait(1000);
						} catch (InterruptedException e1) {
							/* ignore */
						}
					}
				}
				// copy ref
				s = socket;
			}
			try {
				byte buf[] = new byte[MDMS_PACKET_SIZE];
				DatagramPacket p = new DatagramPacket(buf, buf.length);
				s.receive(p);
				//Log.d(TAG,"Received "+p.getLength()+" bytes from "+p.getAddress().getHostAddress()+":"+p.getPort());
				logger.fine("Received "+p.getLength()+" bytes from "+p.getAddress().getHostAddress()+":"+p.getPort());
				DnsProtocol pp = new DnsProtocol();
				pp.bytes = p.getData();
				pp.len = p.getLength();
				pp.unmarshall();
				if (!pp.response && pp.queries.length>0) {
					// start response...
					DnsProtocol rpp = new DnsProtocol();
					rpp.queries = new DnsProtocol.Query[0];
					rpp.response = true;
					rpp.id = pp.id;
					LinkedList<DnsProtocol.RR> resprrs = new LinkedList<DnsProtocol.RR>();
					for (int i=0; i<pp.queries.length; i++) {
						DnsProtocol.Query q = pp.queries[i];
						for (DnsProtocol.RR r : rrs) {
							if (q.name.equals(r.name) && q.type==r.type && q.rclass==r.rclass) {
								//Log.d(TAG,"Found matching RR: "+r);
								logger.info("Found matching RR: "+r);
								resprrs.add(r);
							}
						}
					}
					if (resprrs.size()>0) {
						rpp.answers = resprrs.toArray(new DnsProtocol.RR[resprrs.size()]);
						rpp.marshall();
						DatagramPacket rp = new DatagramPacket(rpp.bytes, rpp.len, p.getAddress(), p.getPort());
						//Log.d(TAG,"Sending response "+rp.getLength()+" bytes to "+rp.getAddress().getHostAddress()+":"+rp.getPort());
						logger.info("Sending response "+rp.getLength()+" bytes to "+rp.getAddress().getHostAddress()+":"+rp.getPort());
						s.send(rp);
					}
					else {
						logger.info("Ignored query "+pp);
					}
				}
			}
			catch (Exception e) {
				//Log.d(TAG,"Error doing receive: "+e.getMessage());
				logger.warning("Error doing receive: "+e.getMessage());
			}
		}
		closeInternal();
	}
	
	/** test */
	public static void main(String args[]) {
		DnsServer server = new DnsServer();
		DnsProtocol.RR r = new DnsProtocol.RR();
		r.name = "some.name.local";
		r.rclass = DnsProtocol.CLASS_IN;
		r.type = DnsProtocol.TYPE_A;
		r.rdata = new byte[4];
		r.rdata[0] = 0x1; r.rdata[1] = 0x2; r.rdata[2] = 0x3; r.rdata[3] = 0x4;
		server.add(r);
		server.start();
	}
}
