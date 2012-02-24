/**
 * 
 */
package uk.ac.horizon.ubihelper.net;

import java.io.IOException;
import java.net.Proxy.Type;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

//import android.util.Log;

/** Wrapper for a (sequence of) network connection(s) to a (possible) peer, supporting message passing
 * communication over non-blocking java.nio.channels.SocketChannel.
 * 
 * @author cmg
 *
 */
public class PeerConnection {
	static final Logger logger = Logger.getLogger(PeerConnection.class.getName());
	
	private static final String TAG = "ubihelper-peerconn";
	static final int HEADER_SIZE = 8;
	private static final int HEADER_FLAGS = 0;
	static final int HEADER_FLAG_MORE = 0x80;
	private static final int HEADER_PAYLOADTYPE = 1;
	private static final int HEADER_MESSAGEID_HI = 2;
	private static final int HEADER_MESSAGEID_LO = 3;
	private static final int HEADER_FRAGMENTID_HI = 4;
	private static final int HEADER_FRAGMENTID_LO = 5;
	private static final int HEADER_LENGTH_HI = 6;
	private static final int HEADER_LENGTH_LO = 7;
	/** send messages - not yet fragmented (can be withdrawn or checked) */
	private PriorityQueue<Message> sendMessageQueue = new PriorityQueue<Message>(10, new Message.PriorityComparator());
	/** send fragments */
	private PriorityQueue<Fragment> sendFragmentQueue = new PriorityQueue<Fragment>(10, new Fragment.PriorityComparator());
	/** current fragment in send - removed from sendFragmentQueue atomically */
	private Fragment currentSendFragment;
	/** sent header? */
	private boolean sendHeaderDone;
	/** send buf */
	private ByteBuffer sendBuffer;
	/** information about part-received message */
	private static class PartMessage {
		int messageid;
		LinkedList<Fragment> fragments = new LinkedList<Fragment>();
		/** for timeout */
		long firstFragmentTime;
		
		PartMessage(int messageid, Fragment firstFragment) {
			this.messageid = messageid;
			fragments.add(firstFragment);
			firstFragmentTime = System.currentTimeMillis();
		}
	}
	/** pool of part-messages received, by messageid */
	private HashMap<Integer,PartMessage> recvPartMessages = new HashMap<Integer,PartMessage>();
	/** list of PartMessages by time created */
	private Queue<PartMessage> recvPartMessagesByTime = new LinkedList<PartMessage>();
	/** queue of complete received messages */
	private Queue<Message> recvMessageQueue = new LinkedList<Message>();
	/** current fragment in recv - added to recvPartMessages atomically */
	private Fragment currentRecvFragment;
	/** recv buffer */
	private ByteBuffer recvBuffer;
	/** recv header done */
	private boolean recvHeaderDone;
	/** selector used for non-blocking/registration */
	private Selector selector;
	/** current socket */
	private SocketChannel socketChannel;
	/** lock to be used/held when updating registration with the selector */
	private ReentrantLock selectorLock;
	/** selector key */
	private SelectionKey selectionKey;
	/** current socket failed on send */
	private boolean failedSend;
	/** current socket failed on recv */
	private boolean failedRecv;
	/** current socket failed on connect */
	private boolean failedConnect;
	/** wants to send (would block) */
	private int waitingOps;
	/** next send message id */
	private int nextMessageid;
	/** client object */
	private Object mAttachment[] = new Object[1];
	/** callback */
	private OnPeerConnectionListener callback = null;
	
	/** cons */
	public PeerConnection(Selector selector, ReentrantLock selectorLock) {
		this.selector = selector;
		this.selectorLock = selectorLock;
	}
	public void attach(Object attachment) {
		// didn't really want to lock whole Object
		synchronized (mAttachment) {
			mAttachment[0] = attachment;
		}
	}
	public Object attachment() {
		synchronized (mAttachment) {
			return mAttachment[0];
		}
	}
	public synchronized void setOnPeerConnectionListener(OnPeerConnectionListener listener) {
		callback = listener;
	}
	
	/** set (new) socket channel - start handshake, etc. */
	public synchronized void changeSocketChannel(SocketChannel socketChannel2) {
		// tidy up
		try {
			if (this.socketChannel!=null)
				this.socketChannel.close();
			// cancels selector anyway
			selectionKey = null;
		}
		catch (Exception e) {
			/* ignore */
		}
		// delete any remaining fragments that of part-sent messages and part-received messages
		sendFragmentQueue.clear();
		recvPartMessages.clear();
		recvPartMessagesByTime.clear();
		currentRecvFragment = null;
		recvBuffer = null;
		currentSendFragment = null;
		sendBuffer = null;
		// ready...
		this.socketChannel = socketChannel2;
		failedSend = false;
		failedRecv = false;
		failedConnect = false;
		selectorLock.lock();
		try {
			if (!this.socketChannel.isConnected())
				waitingOps = SelectionKey.OP_CONNECT;
			else 
				waitingOps = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
			reregisterInitial();
		}
		finally {
			selectorLock.unlock();
		}
	}
	public void checkConnect() {
		if (failedConnect)
			return;
		try {
			boolean done = socketChannel.finishConnect();
			if (done) {
				selectorLock.lock();
				try {
					waitingOps = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
					reregister();
				}
				finally {
					selectorLock.unlock();
				}
			}
		} catch (IOException e) {
			log_w(TAG,"finishConnect failed: "+e.getMessage());
			failedConnect = true;
			callOnFail();
		}
	}
	/** send message (queue, anyway) */
	public void sendMessage(Message m) {
		synchronized (sendMessageQueue) {
			sendMessageQueue.add(m);
		}
		registerSet(SelectionKey.OP_WRITE);
		// no, really!
		selector.wakeup();
	}
	private void registerSet(int op) {
		selectorLock.lock();
		try {
			if ((waitingOps & op)==0) {
				waitingOps |= op;
				reregister();
			}
		} finally {	
			selectorLock.unlock();
		}
	}		
	private void registerClear(int op) {
		selectorLock.lock();
		try {
			if ((waitingOps & op)!=0) {
				waitingOps &= ~op;
				reregister();
			}
		} finally {	
			selectorLock.unlock();
		}
	}		

	/** non-blocking - return null if not available */
	public Message getMessage() {
		synchronized (recvMessageQueue) {
			Message m = recvMessageQueue.poll();
			return m;
		}
	}
	/** call from scheduler thread */
	synchronized void checkSend() {
		logger.info("checkSend()");
		boolean blocking = false;
		boolean failed = failedSend;
		while (!blocking && !failed) {
			// current fragment complete?
			if (currentSendFragment!=null) {
				if (socketChannel!=null) {
					int cnt = 0;
					try {
						cnt = socketChannel.write(sendBuffer);
					} catch (IOException e) {
						log_w(TAG, "Error doing write: "+e.getMessage());
						failed = true;
						break;
					}
					if (cnt==0)
						blocking = true;
					else if (sendBuffer.remaining()==0) {
						if (!sendHeaderDone) {
							// paylod
							sendBuffer = ByteBuffer.wrap(currentSendFragment.payload, currentSendFragment.offset, currentSendFragment.length);
							sendHeaderDone = true;
						} else {
							currentSendFragment = null;
							sendBuffer = null;
						}
					}					
				}
				// else no point trying to send!
			}
			else {
				// line up a new fragment...
				// highest priority message?
				Message nm = null;
				synchronized (sendMessageQueue) {
					nm = sendMessageQueue.peek();
				}
				Fragment nf = sendFragmentQueue.peek();
				if (nm!=null && (nf==null || nm.priority>nf.priority)) {
					// fragment new message
					synchronized (sendMessageQueue) {
						// not really a race because only we take stuff from it
						nm = sendMessageQueue.remove();
					}
					Queue<Fragment> nfs;
					try {
						nfs = Marshaller.fragment(nm, nextMessageid++);
						sendFragmentQueue.addAll(nfs);
					} catch (IOException e) {
						log_e(TAG,"Error marshalling message to fragments: "+e.getMessage());
					}
				}
				// definitely fragments (if any) now
				nf = sendFragmentQueue.peek();
				if (nf!=null) {
					// make current
					nf = sendFragmentQueue.remove();
					currentSendFragment = nf;
					logger.info("Sending fragment "+nf);
					byte currentSendHeader[] = null;
					if (currentSendFragment.offset==HEADER_SIZE) {
						currentSendHeader = currentSendFragment.payload;
						sendHeaderDone = true;
						sendBuffer = ByteBuffer.wrap(currentSendFragment.payload, 0, HEADER_SIZE+currentSendFragment.length);
					} else {
						// separate header buffer
						currentSendHeader = new byte[HEADER_SIZE];
						sendHeaderDone = false;
						sendBuffer = ByteBuffer.wrap(currentSendHeader);
					}
					currentSendHeader[HEADER_FLAGS] = currentSendFragment.flags;
					currentSendHeader[HEADER_PAYLOADTYPE] = currentSendFragment.messagetype.getCode();
					currentSendHeader[HEADER_MESSAGEID_HI] = (byte)((currentSendFragment.messageid>>8) & 0xff);
					currentSendHeader[HEADER_MESSAGEID_LO] = (byte)((currentSendFragment.messageid) & 0xff);
					currentSendHeader[HEADER_FRAGMENTID_HI] = (byte)((currentSendFragment.fragmentid>>8) & 0xff);
					currentSendHeader[HEADER_FRAGMENTID_LO] = (byte)((currentSendFragment.fragmentid) & 0xff);
					currentSendHeader[HEADER_LENGTH_HI] = (byte)((currentSendFragment.length>>8) & 0xff);
					currentSendHeader[HEADER_LENGTH_LO] = (byte)((currentSendFragment.length) & 0xff);
				}
				else
					// nothing to do
					break;
			}
		}
		if (failed) {
			failedSend = true;
			callOnFail();
		}		
		if (blocking && !failed) {
			registerSet(SelectionKey.OP_WRITE);
		}
		else {
			registerClear(SelectionKey.OP_WRITE);
		}
	}
	private synchronized void callOnFail() {
		try {
			if (socketChannel!=null) {
				try {
					logger.info("Closing socketChannel on fail");
					socketChannel.close();					
				}
				catch (Exception e) {
					/* ignore */
				}
				socketChannel = null;
			}
			if (callback!=null)
				callback.onFail(this, failedSend, failedRecv, failedConnect);
		}
		catch (Exception e) {
			log_w(TAG,"Error on onFail callback: "+e.getMessage());
		}
	}
	private synchronized void callOnMessage() {
		try {
			if (callback!=null)
				callback.onRecvMessage(this);
		}
		catch (Exception e) {
			log_w(TAG,"Error on onRecvMessage callback: "+e.getMessage());
		}
	}
	/** call from scheduler thread */
	synchronized void checkRecv() {
		logger.info("checkRecv()");
		boolean blocking = false;
		boolean failed = failedRecv;
		while (!blocking && !failed) {
			if (currentRecvFragment==null) {
				currentRecvFragment = new Fragment();
				currentRecvFragment.payload = new byte[HEADER_SIZE];
				currentRecvFragment.offset = HEADER_SIZE;
				recvBuffer = ByteBuffer.wrap(currentRecvFragment.payload, 0, HEADER_SIZE);
				recvHeaderDone = false;
			} 
			else if (socketChannel!=null) {
				int cnt = 0;
				try {
					cnt = socketChannel.read(recvBuffer);
				} catch (IOException e) {
					log_w(TAG,"Error doing read: "+e.getMessage());
					failed = true;
					break;
				}
				if (cnt==0) 
					blocking = true;
				else if (recvBuffer.remaining()==0) {
					// buffer filled...
					if (!recvHeaderDone) {
						recvHeaderDone = true;
						currentRecvFragment.flags = currentRecvFragment.payload[HEADER_FLAGS];
						byte type = currentRecvFragment.payload[HEADER_PAYLOADTYPE];
						if (type==Message.Type.HELLO.getCode())
							currentRecvFragment.messagetype = Message.Type.HELLO;
						else if (type==Message.Type.MANAGEMENT.getCode())
							currentRecvFragment.messagetype = Message.Type.MANAGEMENT;
						else if (type==Message.Type.REQUEST.getCode())
							currentRecvFragment.messagetype = Message.Type.REQUEST;
						else if (type==Message.Type.RESPONSE.getCode())
							currentRecvFragment.messagetype = Message.Type.RESPONSE;
						else if (type==Message.Type.UNDEFINED.getCode())
							currentRecvFragment.messagetype = Message.Type.UNDEFINED;
						else {
							log_w(TAG,"Received unknown Fragment type "+(type & 0xff));
							failed = true;
							break;
						}
						currentRecvFragment.fragmentid = 
								((currentRecvFragment.payload[HEADER_FRAGMENTID_HI] & 0xff) << 8) |
								(currentRecvFragment.payload[HEADER_FRAGMENTID_LO] & 0xff);
						currentRecvFragment.messageid = 
								((currentRecvFragment.payload[HEADER_MESSAGEID_HI] & 0xff) << 8) |
								(currentRecvFragment.payload[HEADER_MESSAGEID_LO] & 0xff);
						currentRecvFragment.length = 
								((currentRecvFragment.payload[HEADER_LENGTH_HI] & 0xff) << 8) |
								(currentRecvFragment.payload[HEADER_LENGTH_LO] & 0xff);
						
						if (currentRecvFragment.length > currentRecvFragment.payload.length+HEADER_SIZE) {
							currentRecvFragment.payload = new byte[currentRecvFragment.length];
							currentRecvFragment.offset = 0;
						}
						recvBuffer = ByteBuffer.wrap(currentRecvFragment.payload, currentRecvFragment.offset, currentRecvFragment.payload.length-currentRecvFragment.offset);
					} else {
						// done body
						logger.info("Received fragment "+currentRecvFragment);
						// done message?
						Queue<Fragment> fs = null;
						// unfragmented?
						if (currentRecvFragment.fragmentid==0 || (currentRecvFragment.fragmentid==1 && (currentRecvFragment.flags & HEADER_FLAG_MORE)==0)) {
							if ((currentRecvFragment.flags & HEADER_FLAG_MORE)!=0) {
								log_w(TAG,"Fragment received with 0 fragmentid but MORE set");
								failed = true;
								break;
							}
							fs = new LinkedList<Fragment>();
							fs.add(currentRecvFragment);
						} else {
							// combine with pool
							PartMessage pm = recvPartMessages.get(currentRecvFragment.messageid);
							if (pm==null) {
								pm = new PartMessage(currentRecvFragment.messageid, currentRecvFragment);
								recvPartMessages.put(currentRecvFragment.messageid, pm);
								recvPartMessagesByTime.add(pm);
							}
							else {
								pm.fragments.add(currentRecvFragment);
								if ((currentRecvFragment.flags & HEADER_FLAG_MORE)==0) {
									// check present/in order
									for (int i=0; i<pm.fragments.size(); i++) {
										Fragment f = pm.fragments.get(i);
										if (i+1 != f.fragmentid) {
											log_w(TAG,"Received message "+pm.messageid+" fragment "+f.fragmentid+" out of order ("+(i+1)+")");
											failed = true;
											break;
										}
									}
									fs = pm.fragments;
									recvPartMessages.remove(pm.messageid);
									recvPartMessagesByTime.remove(pm);
								}
							}
						}
						currentRecvFragment = null;
						recvBuffer = null;
						if (fs!=null)
							// message...
							try {
								Message m = Marshaller.assemble(fs);
								synchronized (recvMessageQueue) {
									recvMessageQueue.add(m);
								}
								callOnMessage();
							}
							catch (IOException e) {
								log_w(TAG,"Error assembling single-fragment message: "+e.getMessage());
							}
					}
				}
			}
		}
		if (failed) {
			failedRecv= true;
			callOnFail();
		}
		
		if (blocking && !failed) {
			registerSet(SelectionKey.OP_READ);
		}
		else {
			registerClear(SelectionKey.OP_READ);
		}
	}
	synchronized int getWaitingOps() {
		return waitingOps;
	}
	private synchronized void reregister() {
		//selector.wakeup();
	}
	private synchronized void reregisterInitial() {
		if (socketChannel==null || !socketChannel.isOpen())
			return;
		selectorLock.lock();
		selector.wakeup();
		try {
			logger.info("About to register for "+waitingOps+", selector open="+selector.isOpen()+", socket open="+socketChannel.isOpen());
			selectionKey = socketChannel.register(selector, waitingOps, this);
		} catch (ClosedChannelException e) {
			if ((waitingOps & SelectionKey.OP_WRITE)!=0)
				failedSend = true;
			if ((waitingOps & SelectionKey.OP_READ)!=0)
				failedRecv = true;
			if ((waitingOps & SelectionKey.OP_CONNECT)!=0)
				failedConnect = true;
			log_w(TAG,"register failed (ops="+waitingOps+"): "+e);
			e.printStackTrace();
		} 
		finally {
			selectorLock.unlock();
		}
	}
	
	private static void log_w(String tag, String msg) {
		log("Warning", tag, msg);
	}
	private static void log_d(String tag, String msg) {
		log("Debug", tag, msg);
	}
	private static void log_e(String tag, String msg) {
		log("Error", tag, msg);
	}
	static void log(String level, String tag, String msg) {
		System.out.println(tag+" - "+level+": "+msg);
	}
}
