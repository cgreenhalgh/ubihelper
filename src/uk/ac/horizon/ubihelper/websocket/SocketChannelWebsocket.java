/**
 * Copyright (c) 2012 The University of Nottingham
 * 
 * This file is part of ubihelper
 *
 *  ubihelper is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ubihelper is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with ubihelper. If not, see <http://www.gnu.org/licenses/>.
 *  
 *  @author Chris Greenhalgh (cmg@cs.nott.ac.uk), The University of Nottingham
 */
package uk.ac.horizon.ubihelper.websocket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Common stuff for Websocket (after handshake) over SocketChannel, i.e. selectable 
 * and non-blocking.
 * 
 * @author cmg
 *
 */
public class SocketChannelWebsocket extends Websocket {
	private static final int DEFAULT_BUFFER_SIZE = 10000;
	static Logger logger = Logger.getLogger(SocketChannelWebsocket.class.getName());
	protected SocketChannel socketChannel;
	private Selector selector;
	private ReentrantLock selectorLock;
	private LinkedList<ByteBuffer> writeQueue = new LinkedList<ByteBuffer>();
	protected LinkedList<ByteBuffer> readQueue = new LinkedList<ByteBuffer>();
	protected ByteBuffer readBuffer = null;
	
	protected SocketChannelWebsocket(ReadyState initialState, Selector selector, ReentrantLock selectorLock, WebsocketListener listener) {
		super(initialState, listener);
		this.selector = selector;
		this.selectorLock = selectorLock;
	}
	
	/** will perform accept - call when ready */
	public SocketChannelWebsocket getWebsocket(ServerSocketChannel serverSocketChannel, Selector selector, ReentrantLock selectorLock, WebsocketListener listener) {
		// TODO
		throw new RuntimeException("unimplemented: getWebsocket(ServerSocketChannel)");
	}
	
	/** will perform connect */
	public SocketChannelWebsocket getWebsocket(String host, int port, Selector selector, ReentrantLock selectorLock, WebsocketListener listener) throws IOException {
		SocketChannelWebsocket scw = new SocketChannelWebsocket(ReadyState.CONNECTING, selector, selectorLock, listener);
		scw.connect(host, port);
		return scw;
	}
	
	protected synchronized void connect(String host, int port) throws IOException {
		try {
			readyState = ReadyState.CONNECTING;
			socketChannel = SocketChannel.open();
			// non-blocking
			socketChannel.configureBlocking(false);
			InetSocketAddress address = new InetSocketAddress(host, port);
			socketChannel.connect(address);
			register(SelectionKey.OP_CONNECT);
		}
		catch (IOException ioe) {
			logger.info("connect("+host+","+port+") failed: "+ioe);
			try {
				socketChannel.close();
			}
			catch (Exception e) { /* */ }
			socketChannel = null;
			readyState = ReadyState.CLOSED;
			// re-throwing exception, not listener
			throw ioe;
		}
	}
	protected void register(int ops) throws ClosedChannelException {
		selectorLock.lock();
		try {
			socketChannel.register(selector, ops, this);
		}
		finally {
			selectorLock.unlock();
		}
	}

	/** on selector key callback */
	public synchronized void onSelect(SelectionKey key) {
		if (key.isConnectable() && readyState==ReadyState.CONNECTING) {
			logger.info("connected");
			try {
				socketChannel.finishConnect();
				readyState = ReadyState.OPEN;
				register(SelectionKey.OP_READ);
				callOnopen();
			}
			catch (Exception e) {
				logger.log(Level.WARNING, "error handling connect", e);
				error();
				return;
			}
		}
		if (key.isWritable()) {
			while (!writeQueue.isEmpty()) {
				try {
					ByteBuffer buf = writeQueue.removeFirst();
					int cnt = socketChannel.write(buf);
					logger.info("wrote "+cnt+" bytes, "+buf.remaining()+" remaining");
					if (buf.remaining()>0) {
						// would block - put back (rest)
						writeQueue.add(0, buf);;
						break;
					}
				}
				catch (Exception e) {
					logger.log(Level.WARNING,"Error writing", e);
					error();
					return;
				}
			}
			if (writeQueue.isEmpty()) {
				// no need to select for write
				try {
					register(SelectionKey.OP_READ);
				} catch (ClosedChannelException e) {
					logger.log(Level.WARNING,"Error registering for read (after write)", e);
					error();
					return;
				}
			}
		}
		if (key.isReadable()) {
			while (true) {
				if (readBuffer!=null && readBuffer.remaining()<=0) {
					queueReadBuffer();
				}
				if (readBuffer==null) {
					readBuffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
				} 
				try {
					int cnt = socketChannel.read(readBuffer);
					if (cnt<=0) {
						// done 
						logger.info("Did not read anything - open? "+socketChannel.isOpen()+", connected? "+socketChannel.isConnected()+" inputShutdown? "+socketChannel.socket().isInputShutdown()+" outputShutdown? "+socketChannel.socket().isOutputShutdown()+", position="+readBuffer.position()+", "+readBuffer.remaining()+" remaining");
						break;
					}
					logger.info("Read "+cnt+" bytes");
				} catch (IOException e) {
					logger.info("Error reading from socket: "+e);
					error();
					return;
				}
			}
			if (readQueue.isEmpty() && readBuffer.position()>0) {
				// promote new buffer
				queueReadBuffer();
			}
			if (!readQueue.isEmpty()) {
				handleReadQueue();
			}
		}
	}
	
	protected void queueReadBuffer() {
		readBuffer.limit(readBuffer.position());
		readBuffer.position(0);
		readQueue.add(readBuffer);
		readBuffer = null;
	}

	protected void handleReadQueue() {
		// TODO Auto-generated method stub
		
	}

	protected void error() {
		try {
			socketChannel.close();			
		}
		catch (Exception e) {
			/* */
		}
		socketChannel = null;
		if (readyState!=ReadyState.CLOSED) {
			readyState = ReadyState.CLOSED;
			callOnerror();
		}
	}
	
	protected synchronized void queue(byte data[]) throws ClosedChannelException {
		writeQueue.add(ByteBuffer.wrap(data));
		register(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
	}

}
