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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Meant to be a simple version of a Websocket API that will run over: websocket (client only), 
 * raw tcp (java.nio.SocketChannel, client and server), 
 * Bluetooth socket (blocking input and output streams plus close).
 * 
 * Intermediate abstraction is (websocket client only) header read/to write and frame read/to write.
 * High-level follows Javascript Websocket API (http://www.w3.org/TR/websockets/) and is non-blocking and event-based.
 * 
 * Status: work in progress
 * 
 * Targets http://tools.ietf.org/html/rfc6455
 * 
 * @author cmg
 *
 */
public abstract class Websocket {
	static Logger logger = Logger.getLogger(Websocket.class.getName());
	protected ReadyState readyState;
	private WebsocketListener listener;
	
	protected Websocket(ReadyState initReadyState, WebsocketListener listener) {
		this.readyState = initReadyState;
		this.listener = listener;
	}
	
	public void close() {
		// TODO
	}
	
	public void send(String data) {
		// TODO
	}
	
	public ReadyState getReadyState() {
		return readyState;
	}
	protected void callOnopen() {
		try {
			if (listener!=null)
				listener.onopen(this);
		}
		catch (Exception e) {
			logger.log(Level.WARNING, "exception in listener.onopen", e);
		}
	}
	protected void callOnerror() {
		try {
			if (listener!=null)
				listener.onerror(this);
		}
		catch (Exception e) {
			logger.log(Level.WARNING, "exception in listener.onerror", e);
		}
	}
}
