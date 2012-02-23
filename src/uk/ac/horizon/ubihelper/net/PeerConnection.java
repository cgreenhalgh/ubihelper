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

//import android.util.Log;

/** Wrapper for a (sequence of) network connection(s) to a (possible) peer, supporting message passing
 * communication over non-blocking java.nio.channels.SocketChannel.
 * 
 * @author cmg
 *
 */
public class PeerConnection {
	
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
	private PriorityQueue<Message> sendMessageQueue = new PriorityQueue<Message>(0, new Message.PriorityComparator());
	/** send fragments */
	private PriorityQueue<Fragment> sendFragmentQueue = new PriorityQueue<Fragment>(0, new Fragment.PriorityComparator());
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
	/** wants to send (would block) */
	private int waitingOps;
	/** next send message id */
	private int nextMessageid;
	
	/** cons */
	public PeerConnection(Selector selector, ReentrantLock selectorLock) {
		this.selector = selector;
		this.selectorLock = selectorLock;
	}
	
	/** set (new) socket channel - start handshake, etc. */
	public synchronized void changeSocketChannel(SocketChannel socketChannel) {
		// tidy up
		try {
			if (socketChannel!=null)
				socketChannel.close();
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
		this.socketChannel = socketChannel;
		failedSend = false;
		failedRecv = false;
		waitingOps = 0;
		reregister();
		checkSend();
		checkRecv();
	}
	
	/** send message (queue, anyway) */
	public synchronized void sendMessage(Message m) {
		sendMessageQueue.add(m);
		checkSend();
	}
	/** non-blocking - return null if not available */
	public synchronized Message getMessage() {
		Message m = recvMessageQueue.poll();
		return m;
	}
	private synchronized void checkSend() {
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
				Message nm = sendMessageQueue.peek();
				Fragment nf = sendFragmentQueue.peek();
				if (nm!=null && (nf==null || nm.priority>nf.priority)) {
					// fragment new message
					nm = sendMessageQueue.remove();
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
					byte currentSendHeader[] = null;
					if (currentSendFragment.offset==HEADER_SIZE) {
						currentSendHeader = currentRecvFragment.payload;
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
		if (failed) 
			failedSend = true;
		
		if (blocking && !failed) {
			if ((waitingOps & SelectionKey.OP_WRITE)==0) {
				waitingOps |= SelectionKey.OP_WRITE;
				reregister();
			}
		}
		else {
			if ((waitingOps & SelectionKey.OP_WRITE)!=0) {
				waitingOps &= ~SelectionKey.OP_WRITE;
				reregister();
			}
		}
	}
	private synchronized void checkRecv() {
		boolean blocking = false;
		boolean failed = failedRecv;
		while (!blocking && !failed) {
			if (currentRecvFragment==null) {
				currentRecvFragment = new Fragment();
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
						byte type = currentRecvFragment.payload[HEADER_FLAGS];
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
								recvMessageQueue.add(m);
							}
							catch (IOException e) {
								log_w(TAG,"Error assembling single-fragment message: "+e.getMessage());
							}
					}
				}
			}
		}
		if (failed) 
			failedRecv= true;
		
		if (blocking && !failed) {
			if ((waitingOps & SelectionKey.OP_READ)==0) {
				waitingOps |= SelectionKey.OP_READ;
				reregister();
			}
		}
		else {
			if ((waitingOps & SelectionKey.OP_READ)!=0) {
				waitingOps &= ~SelectionKey.OP_READ;
				reregister();
			}
		}
	}
	private void reregister() {
		selectorLock.lock();
		selector.wakeup();
		try {
			selectionKey = socketChannel.register(selector, waitingOps, this);
		} catch (ClosedChannelException e) {
			if ((waitingOps & SelectionKey.OP_WRITE)!=0)
				failedSend = true;
			log_w(TAG,"register failed: "+e.getMessage());
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
