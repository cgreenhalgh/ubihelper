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
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.security.SecureRandom;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author cmg
 *
 */
public class ClientWebsocket extends SocketChannelWebsocket {
	static Logger logger = Logger.getLogger(ClientWebsocket.class.getName());
	/** non-secure / http */
	static final int DEFAULT_PORT = 80; 
	private boolean headerSent = false;
	private boolean responseReceived = false;
	private URI url;
	private byte [] nonce;
	private static SecureRandom srandom;
	private static Random random;
	static {
		random = new Random(System.currentTimeMillis());
		try {
			srandom = SecureRandom.getInstance("SHA1PRNG");
		}
		catch (Exception e) {
			logger.warning("Could not get SecureRandom: "+e);
		}
	}
	public ClientWebsocket(URI url, Selector selector, ReentrantLock selectorLock, WebsocketListener listener) throws IOException {
		super(ReadyState.CONNECTING, selector, selectorLock, listener);
		this.url = url;
		// TODO initialise TCP connection using SocketChannel to server
		int port = url.getPort();
		if (port<0)
			port = DEFAULT_PORT;
		connect(url.getHost(), port);
		// TODO create HTTP request header and schedule for sending
		// TODO configure suitable hooks to receive/handle failure, connection, receipt of response
	}

	/** on selector key callback */
	@Override
	public synchronized void onSelect(SelectionKey key) {
		if (key.isConnectable() && readyState==ReadyState.CONNECTING) {
			if (!headerSent) {
				logger.info("connected (client)");
				try {
					socketChannel.finishConnect();
				}
				catch (Exception e) {
					logger.log(Level.WARNING, "error handling connect", e);
					error();
					return;
				}
				queueHttpRequest();
				headerSent = true;
			}
			else {
				logger.warning("key.isConnectable when CONNECTING but headerSent");
			}
		}
		else
			super.onSelect(key);
	}

	static byte [] END_OF_HEADER = new byte [] { (byte)'\r', (byte)'\n', (byte)'\r', (byte)'\n' };
	@Override
	protected void handleReadQueue() {
		if (!responseReceived) {
			// looking for \r\n\r\n...
			int bufi = 0;
			int bytei = 0;
			int pati = 0;
			ByteBuffer buf = null;
			while (pati < END_OF_HEADER.length) {
				if (buf==null) {
					if (bufi>=readQueue.size()) {
						if (readBuffer!=null) {
							queueReadBuffer();
							continue;
						}
						logger.info("Still waiting for end of header");
						return;
					}				 
					buf = readQueue.get(bufi);
					bytei = buf.position();
					bufi++;
				}
				if (bytei>=buf.limit()) {
					buf = null;
					continue;
				}
				if (buf.get(bytei)==END_OF_HEADER[pati]) 
					pati++;
				else
					pati = 0;
				bytei++;
			}
			// got it - stringify
			ByteBuffer bufs [] = new ByteBuffer[bufi];
			int nbytes = 0;
			for (int i=0; i<bufi; i++) {
				bufs[i] = readQueue.removeFirst();
				nbytes += bufs[i].remaining();
			}
			logger.info("Header size "+nbytes+" bytes");
			if (bytei<buf.limit()) {
				// push back rest
				buf.mark();
				buf.position(bytei);
				ByteBuffer nbuf = buf.slice();
				readQueue.add(0, nbuf);
				logger.info("Pushed back remaining "+nbuf.remaining()+" bytes");
				buf.limit(bytei);
				buf.reset();
			}
			Charset charset = Charset.forName("US-ASCII");
			if (charset==null) {
				logger.severe("Charset is null (US-ASCII) - bad!");
			}
			CharsetDecoder cd = charset.newDecoder();
			cd.reset();
			int maxChars = (int)Math.ceil(cd.maxCharsPerByte()*nbytes);
			CharBuffer cb = CharBuffer.allocate(maxChars);
			for (int i =0; i<bufs.length; i++) {
				logger.info("decode "+bufs[i].remaining()+" bytes from buffer "+i+" position "+bufs[i].position());
				CoderResult cr = cd.decode(bufs[i], cb, (i+1)==bufs.length);
				if (cr!=CoderResult.UNDERFLOW) {
					logger.warning("Error decoding response in buffer "+i+"/"+bufs.length);
					error();
					return;
				}
			}
			cb.limit(cb.position());
			cb.position(0);
			logger.info("Got response, "+cb.limit()+" chars: "+cb.toString());
			// TODO
			
			responseReceived = true;
			if (readyState==ReadyState.CONNECTING) {
				readyState = ReadyState.OPEN;
				callOnopen();
			}
			else
				logger.warning("Completed header read in state "+readyState);
		}		
	}

	
	private void queueHttpRequest() {
		// TODO Auto-generated method stub
		StringBuilder req = new StringBuilder();
		req.append("GET ");
		String path = url.getPath();
		if (!path.startsWith("/"))
			req.append("/");
		req.append(url.getPath());
		req.append(" HTTP/1.1\r\n");
		req.append("Host: ");
		req.append(url.getHost());
		if (url.getPort()>=0) {
			req.append(":");
			req.append(url.getPort());
		}
		req.append("\r\n");
		req.append("Upgrade: websocket\r\n");
		req.append("Connection: Upgrade\r\n");
		// "a nonce consisting of a randomly selected 16-byte value that has
        // been base64-encoded"
		nonce = new byte[16];
		if (srandom!=null)
			srandom.nextBytes(nonce);
		else
			random.nextBytes(nonce);
		req.append("Sec-WebSocket-Key: ");
		req.append(base64Encode(nonce));
		req.append("\r\n");
		req.append("Sec-WebSocket-Version: 13\r\n");
		req.append("Sec-WebSocket-Protocol: echo-protocol\r\n");
		req.append("\r\n");
		//opt.req.append("Origin: ...\r\n");
		//opt.
		try {
			byte data[] = req.toString().getBytes("US-ASCII");
			queue(data);
			logger.info("Queued request: "+req.toString());
		} catch (Exception e) {
			logger.warning("Error sending request header: "+e);
			error();
		}
	}

	public String base64Encode(byte data[]) {
		return uk.ac.horizon.ubihelper.j2se.Base64.encode(data);
	}
	
	/** test main */
	public static void main(String [] args) {
		if (args.length!=1) {
			logger.severe("Usage: ws://<host>:<port>");
			System.exit(-1);
		}
		try {
			logger.info("Connect to "+args[0]);
			URI url = new URI(args[0]);
			Selector selector = Selector.open();
			ReentrantLock selectorLock = new ReentrantLock();
			ClientWebsocket cw = new ClientWebsocket(url, selector, selectorLock, new WebsocketListener() {
				public void onmessage(Websocket ws, String data) {
					logger.info("onmessage("+data+")");
				}

				public void onerror(Websocket ws) {
					logger.info("onerror()");
				}

				public void onopen(Websocket ws) {
					logger.info("onopen()");
				}

				public void onclose(Websocket ws) {
					logger.info("onclose()");
				}				
			});
			// standard selector
			while (true) {
				selectorLock.lock();
				selectorLock.unlock();
				if (selector.select()!=0) {
					Set<SelectionKey> keys = selector.selectedKeys();
					for (SelectionKey key : keys) {
						if (key.attachment() instanceof SocketChannelWebsocket) {
							SocketChannelWebsocket scw = (SocketChannelWebsocket)key.attachment();
							logger.info("Select for "+key.readyOps());
							scw.onSelect(key);
						}
					}
				}
				
			}
		}
		catch (Exception e) {
			logger.log(Level.SEVERE, "Error: "+e, e);
		}
	}
}
