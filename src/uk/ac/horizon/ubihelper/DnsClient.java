/**
 * 
 */
package uk.ac.horizon.ubihelper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
//import java.net.SocketException;
import java.util.LinkedList;
import java.util.logging.Logger;

/**
 * @author cmg
 *
 */
public class DnsClient extends Thread {
	//static final String TAG = "ubihelper-dnssvr";
	static Logger logger =  Logger.getLogger(DnsServer.class.getName());

	private MulticastSocket socket;
	private boolean closed = false;
	private boolean done = false;
	private String error = null;
	private LinkedList<DnsProtocol.RR> answers = new LinkedList<DnsProtocol.RR>();
	private DnsProtocol.Query query;
	private long startTime;
	private boolean multiple = false;
	
	public DnsClient(DnsProtocol.Query query, boolean multiple) {
		this.query = query;
		this.multiple = multiple;
	}
	
	public synchronized void close() {
		closed = true;
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
	
	public synchronized String getError() {
		return error;		
	}
	
	public synchronized LinkedList<DnsProtocol.RR> getAnswers() {
		LinkedList<DnsProtocol.RR> rval = new LinkedList<DnsProtocol.RR>();
		rval.addAll(answers);
		return rval;
	}
	
	public synchronized boolean getDone() {
		return done || closed || error!=null;
	}
	
	public void run() {
		synchronized (this) {
			startTime = System.currentTimeMillis();
			try {
				socket = new MulticastSocket();
				socket.setTimeToLive(1);
				//socket.setSoTimeout(TIMEOUT_MS);
			}
			catch (Exception e) {
				errorInternal("Creating socket: "+e.getMessage());
				return;
			}
		}
		DnsProtocol qp = new DnsProtocol();
		try {
			qp.queries = new DnsProtocol.Query[1];
			qp.queries[0] = query;
			qp.answers = new DnsProtocol.RR[0];
			qp.marshall();
		}
		catch (Exception e) {
			errorInternal("Marshalling query: "+e.getMessage());
			e.printStackTrace();
			return;
		}
		final int ATTEMPTS = 3;
		final int INTERVAL_MS = 3000;
		done:
		for (int a=0; a<ATTEMPTS && !closed && error==null; a++) {
			long endTime = startTime+(a+1)*INTERVAL_MS;
			try {
				DatagramPacket dp = new DatagramPacket(qp.bytes, qp.len, InetAddress.getByName(DnsServer.MDNS_ADDRESS), DnsServer.MDNS_PORT);
				socket.send(dp);
			}
			catch (Exception e) {
				logger.warning("Error sending request: "+e.getMessage());
			}
			while (!closed && error==null) {
				long remaining = endTime-System.currentTimeMillis();
				if (remaining<=0)
					break;
				try {
					socket.setSoTimeout((int)remaining);
					DatagramPacket rdp = new DatagramPacket(new byte[512], 512);
					socket.receive(rdp);
					DnsProtocol rp = new DnsProtocol();
					rp.bytes = rdp.getData();
					rp.len = rdp.getLength();
					rp.unmarshall();
					for (int i=0; i<rp.answers.length; i++) {
						DnsProtocol.RR r = rp.answers[i];
						if (query.name.equals(r.name) && query.rclass==r.rclass && query.type==r.type) {
							synchronized (this) {
								boolean known = false;
								for (int ai=0; !known && ai<answers.size(); ai++) {
									DnsProtocol.RR ar = answers.get(ai);
									if (ar.rdata.length==r.rdata.length) {
										boolean same = true;
										for (int bi=0; same && bi<ar.rdata.length; bi++)
											if (ar.rdata[bi]!=r.rdata[bi])
												same = false;
										if (same)
											known = true;
									}
								}
								if (!known) {
									answers.add(r);
									logger.info("Found answer "+r);
									if (!multiple)
										break done;
								}
								else {
									logger.info("Found known answer (query "+(a+1)+")");
								}
							}
						}
					}
				} catch (Exception e) {
					logger.warning("Error receiving packet: "+e.getMessage());
				}
			}			
		}
		synchronized (this) {
			done = true;
			closeInternal();			
		}
	}

	private synchronized void errorInternal(String msg) {
		logger.warning(msg);
		error = msg;
		closeInternal();
	}
	
	/** test */
	public static void main(String args[]) {
		test(args, false);
		test(args, true);
	}
	private static void test(String args[], boolean multiple) {
		DnsProtocol.Query q = new DnsProtocol.Query();
		q.name = args.length==0 ? "some.name.local" : args[0];
		q.rclass = DnsProtocol.CLASS_IN;
		q.type = DnsProtocol.TYPE_A;
		logger.info("Query for "+q.name+" ("+(multiple ? "multiple" : "single")+")");
		DnsClient c = new DnsClient(q, multiple);
		c.start();
		try {
			c.join();
			logger.info("Client finished");
			logger.info("Client error: "+c.getError());
			LinkedList<DnsProtocol.RR> rrs = c.getAnswers();
			for (DnsProtocol.RR rr : rrs) {
				if (rr.rdata.length==4)
					logger.info("Found answer: "+(rr.rdata[0]&0xff)+"."+(rr.rdata[1]&0xff)+"."+(rr.rdata[2]&0xff)+"."+(rr.rdata[3]&0xff));
				else
					logger.info("Found answer with "+rr.rdata.length+" bytes");
			}
		}
		catch (Exception e) {
			logger.warning("waiting for client: "+e.getMessage());
		}
	}
}
