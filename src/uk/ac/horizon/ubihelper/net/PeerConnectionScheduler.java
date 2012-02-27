/**
 * 
 */
package uk.ac.horizon.ubihelper.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;



/** Scheduler thread for PeerConnections
 * 
 * @author cmg
 *
 */
public class PeerConnectionScheduler extends Thread {
	static Logger logger = Logger.getLogger(PeerConnectionScheduler.class.getName());
	
	private ServerSocketChannel serverSocketChannel;
	private Selector selector;
	private boolean closed = false;
	private ReentrantLock selectorLock = new ReentrantLock();
	private Listener listener;
	
	public static interface Listener {
		void onAccept(PeerConnectionScheduler pcs, PeerConnection newPeerConnection);
	}
	
	/** cons - with server socket 
	 * @throws IOException */
	public PeerConnectionScheduler(ServerSocketChannel ssc) throws IOException {
		serverSocketChannel = ssc;
		serverSocketChannel.configureBlocking(false);
		selector = Selector.open();
		if (serverSocketChannel!=null)
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT, serverSocketChannel);
	}

	public synchronized void close() {
		closed = true;
		try {
			selector.close();
		}
		catch (Exception e) {
			/* ignore */
		}
	}
	public synchronized void setListener(Listener l) {
		listener = l;
	}
	public PeerConnection connect(InetSocketAddress address, OnPeerConnectionListener pcl, Object attachment) throws IOException {
		logger.info("Connect to "+address);
		SocketChannel socketChannel = SocketChannel.open();
		socketChannel.configureBlocking(false);
		socketChannel.connect(address);
		PeerConnection pc = new PeerConnection(selector, selectorLock);
		pc.setOnPeerConnectionListener(pcl);
		pc.attach(attachment);
		pc.changeSocketChannel(socketChannel);
		return pc;
	}
	
	public void run() {
		while (!closed) {
			try {
				// nasty register problem - see http://stackoverflow.com/questions/1057224/thread-is-stuck-while-registering-channel-with-selector-in-java-nio-server
				// ensure other gets chance to complete
				selectorLock.lock();
				selectorLock.unlock();
				logger.info("Select...");
				int i = selector.select();
				logger.info("select done with "+i+" ready");
			} catch (IOException e) {
				logger.warning("doing select(): "+e.getMessage());
			}
			Set<SelectionKey> keys = null;
			try {
				keys = selector.selectedKeys();
			}
			catch (ClosedSelectorException e) {
				logger.info("Selector closed - exiting");
				break;
			}
			for (SelectionKey key : keys) {
				Object obj = key.attachment();
				if (obj instanceof PeerConnection) {
					PeerConnection pc = (PeerConnection)obj;
					try {
						logger.info("Select on PeerConnection connect="+key.isConnectable()+" read="+key.isReadable()+" write="+key.isWritable()+" "+pc);
						if (key.isConnectable())
							pc.checkConnect();
						if (key.isReadable())
							pc.checkRecv();
						if (key.isWritable())
							pc.checkSend();
						int ops = pc.getWaitingOps();
						logger.info("register for ops "+ops);
						key.channel().register(selector, ops, pc);
					} catch (Exception e) {
						logger.warning("Error checking peer connection: "+e);
						e.printStackTrace();
					}
				} else if (serverSocketChannel!=null && obj==serverSocketChannel && key.isAcceptable()) {
					try {
						SocketChannel socketChannel = serverSocketChannel.accept();
						if (socketChannel==null) {
							logger.info("Event on serverSocketChannel but accept returned null");
							continue;
						}							
						socketChannel.configureBlocking(false);
						//Toast.makeText(service, "Accepted connect", Toast.LENGTH_SHORT).show();
						PeerConnection pc = new PeerConnection(selector, selectorLock);
						pc.changeSocketChannel(socketChannel);
						logger.info("Accepted new connection from "+socketChannel.socket().getInetAddress().getHostAddress()+":"+socketChannel.socket().getPort());
						synchronized (this) {
							if (listener!=null) {
								try {
									listener.onAccept(this, pc);
								}
								catch (Exception e) {
									//logger.warning("Error in onAccept listener: "+e.getMessage());
									logger.log(Level.WARNING, "Error in onAccept listener", e);
								}
							}
						}
					} catch (IOException e) {
						logger.warning("Error accepted new connection: "+e.getMessage());
					}						
				}
			}
		}
		// update closed?!
		synchronized (this) {}
	}
	
	
	// test
	public static void main(String args[]) {
		try {
			ServerSocketChannel ssc = ServerSocketChannel.open();
			// any port
			ssc.socket().bind(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0));
			int serverPort = ssc.socket().getLocalPort();
			logger.info("Test server socket on port "+serverPort);
			ssc.configureBlocking(false);
			PeerConnectionScheduler pcs = new PeerConnectionScheduler(ssc);
			final OnPeerConnectionListener pcl = new OnPeerConnectionListener() {
				
				public void onRecvMessage(PeerConnection pc) {
					// TODO Auto-generated method stub
					logger.info("onRecvMessage: "+pc);
					while(true) {
						Message m = pc.getMessage();
						if (m==null)
							break;
						logger.info("Got message: "+m);
					}
				}
				
				public void onFail(PeerConnection pc, boolean sendFailed,
						boolean recvFailed, boolean connectFailed) {
					// TODO Auto-generated method stub
					logger.info("onFail: "+pc);
				}

				public void onConnected(PeerConnection pc) {
					// TODO Auto-generated method stub
					logger.info("onConnected: "+pc);
				}
			};
			pcs.setListener(new Listener() {

				public void onAccept(PeerConnectionScheduler pcs,
						PeerConnection newPeerConnection) {
					// TODO Auto-generated method stub
					logger.info("onAccept: "+newPeerConnection);
					
					newPeerConnection.setOnPeerConnectionListener(pcl);
				}
				
			});
			pcs.start();
			logger.info("Started scheduler thread");
			Thread.sleep(1000);
			PeerConnection pc = null;
			for (int i=0;i<args.length; i++) {
				try {
					int port = Integer.parseInt(args[i]);
					logger.info("Try connect to "+port);
					pc = pcs.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), pcl, null);
				}
				catch (NumberFormatException e) {
					logger.warning("Arg "+i+" not a port number ("+args[i]+")");
				}
				Thread.sleep(1000);
				if (pc!=null) {
					logger.info("Send hello...");
					Message m =new Message(Message.Type.HELLO, null, null, Message.getHelloBody());
					pc.sendMessage(m);
					logger.info("hello sent");
					Thread.sleep(1000);
				}
			}
		}
		catch (Exception e) {
			logger.warning("Error: "+e);
			e.printStackTrace();
		}
	}
}
