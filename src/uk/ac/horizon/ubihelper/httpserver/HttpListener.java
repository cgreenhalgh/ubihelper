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

package uk.ac.horizon.ubihelper.httpserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import uk.ac.horizon.ubihelper.service.Service;

import android.util.Log;

/**
 * @author cmg
 *
 */
public class HttpListener extends Thread {
	private static final String TAG = "ubihelper-http";
	private Service service;
	private int port;
	private ServerSocket socket = null;
	private boolean stopped;
	/** cons */
	public HttpListener(Service service, int port) {
		this.service = service;
		this.port = port;
	}
	/** stop */
	public synchronized void close() {
		stopped = true;
		this.interrupt();
		closeInternal();
	}
	/** set port */
	public synchronized void setPort(int port) {
		this.port = port;
		this.interrupt();
		closeInternal();
	}
	/** run */
	public void run() {
		Log.d(TAG,"Started thread");
		while (!stopped) {
			// may interrupt...
			try {
				ServerSocket ss = null;
				synchronized (this) {
					if (socket!=null && socket.getLocalPort()!=port) 
						closeInternal();
					if (socket==null) {
						try {
							socket = new ServerSocket(port);
							Log.d(TAG,"Opened server socket on port "+socket.getLocalPort());
						}
						catch (IOException e) {
							Log.e(TAG,"Unable to open server socket on port "+port+": "+e.getMessage());
							// TODO notification
							// delay to avoid racing...
							// NB holding lock!
							wait(1000);
						}
					}
					// clone reference in critical section!
					ss = socket;
				}
				if (ss!=null)
					try {
						// may have been closed concurrently, so don't worry too much
						Socket s = ss.accept();
						handleClient(s);
					} catch (IOException e) {
						Log.e(TAG,"Error in accept: "+e.getMessage());
						closeInternal();
					}
			}
			catch (InterruptedException ie) {
				// round the loop...
				Log.d(TAG,"interrupted");
			}
		}
		closeInternal();
		Log.d(TAG,"Exited thread");
	}
	
	private void handleClient(Socket s) {
		Log.d(TAG,"New client at "+s.getRemoteSocketAddress().toString()+":"+s.getPort());
		new HttpClientHandler(service, s).start();
	}
	/** close internal */
	private synchronized void closeInternal() {
		if (socket!=null) {
			Log.d(TAG,"Close socket on port "+socket.getLocalPort());
			try {
				socket.close();
			}
			catch (IOException e) {
				Log.e(TAG,"Error closing socket: "+e.getMessage());
			}
			socket = null;
		}
	}
}
