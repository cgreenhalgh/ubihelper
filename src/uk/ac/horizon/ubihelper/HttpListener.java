/**
 * 
 */
package uk.ac.horizon.ubihelper;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

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
	}
	/** set port */
	public synchronized void setPort(int port) {
		this.port = port;
		this.interrupt();
	}
	/** run */
	public void run() {
		Log.d(TAG,"Started thread");
		while (!stopped) {
			// may interrupt...
			try {
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
				}
				if (socket!=null)
					try {
						Socket s = socket.accept();
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