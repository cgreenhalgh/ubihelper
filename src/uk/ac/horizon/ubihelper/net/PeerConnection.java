/**
 * 
 */
package uk.ac.horizon.ubihelper.net;

import java.io.IOException;
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

import android.util.Log;

/** Wrapper for a (sequence of) network connection(s) to a (possible) peer, supporting message passing
 * communication over non-blocking java.nio.channels.SocketChannel.
 * 
 * @author cmg
 *
 */
public class PeerConnection {
	
	private static final String TAG = "ubihelper-peerconn";
	private static final int HEADER_SIZE = 8;
	private static final int HEADER_FLAGS = 0;
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
	/** current fragment header bytes */
	private byte currentSendHeader[];
	/** next byte to send in currentSendFragment; including header bytes! (i.e. length+8) */
	private int currentSendOffset;
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
	/** current fragment header bytes */
	private byte currentRecvHeader[];
	/** next byte to send in currentSendFragment; including header bytes! (i.e. length+8) */
	private int currentRecvOffset;
	
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
	/** wants to send (would block) */
	private int waitingOps;
	
	/** cons */
	public PeerConnection(Selector selector, ReentrantLock selectorLock) {
		this.selector = selector;
		this.selectorLock = selectorLock;
	}
	
	/** set (new) socket channel - start handshake, etc. */
	public synchronized void changeSocketChannel(SocketChannel socketChannel) {
		// ...
		this.socketChannel = socketChannel;
		failedSend = false;
		waitingOps = 0;
		reregister();
		checkSend();
	}
	
	/** send message (queue, anyway) */
	public synchronized void sendMessage(Message m) {
		sendMessageQueue.add(m);
		checkSend();
	}
	
	private synchronized void checkSend() {
		boolean blocking = false;
		boolean failed = failedSend;
		while (!blocking && !failed) {
			// current fragment complete?
			if (currentSendFragment!=null) {
				if (currentSendOffset>=currentSendHeader.length+currentSendFragment.length) {
					// done
					currentSendFragment = null;
					currentSendHeader = null;
					currentSendOffset = 0;
				} else if (!blocking && !failed && socketChannel!=null) {
					if (currentSendOffset<currentSendHeader.length) {
						// send rest of header?
						try {
							ByteBuffer hbuf = ByteBuffer.wrap(currentSendHeader, currentSendOffset, currentSendHeader.length);
							int cnt = socketChannel.write(hbuf);
							if (cnt==0)
								blocking = true;
							else if (cnt>0) {
								currentSendOffset += cnt;
							}
							else throw new IOException ("Write returned "+cnt);
						}
						catch (IOException e) {
							Log.w(TAG,"Error writing header bytes: "+e.getMessage());
							failed = true;
						}
					}
					else {
						// send rest of body 
						try {
							ByteBuffer hbuf = ByteBuffer.wrap(currentSendFragment.payload, currentSendOffset-currentSendHeader.length, currentSendFragment.length);
							int cnt = socketChannel.write(hbuf);
							if (cnt==0)
								blocking = true;
							else if (cnt>0) {
								currentSendOffset += cnt;
							}
							else throw new IOException ("Write returned "+cnt);
						}
						catch (IOException e) {
							Log.w(TAG,"Error writing payload bytes: "+e.getMessage());
							failed = true;
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
					Queue<Fragment> nfs = Marshaller.fragment(nm);
					sendFragmentQueue.addAll(nfs);
				}
				// definitely fragments (if any) now
				nf = sendFragmentQueue.peek();
				if (nf!=null) {
					// make current
					nf = sendFragmentQueue.remove();
					currentSendFragment = nf;
					currentSendOffset = 0;
					currentSendHeader = new byte[HEADER_SIZE];
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

	private void reregister() {
		selectorLock.lock();
		selector.wakeup();
		try {
			selectionKey = socketChannel.register(selector, waitingOps, this);
		} catch (ClosedChannelException e) {
			if ((waitingOps & SelectionKey.OP_WRITE)!=0)
				failedSend = true;
			Log.w(TAG,"register failed: "+e.getMessage());
		} 
		finally {
			selectorLock.unlock();
		}
	}
}
