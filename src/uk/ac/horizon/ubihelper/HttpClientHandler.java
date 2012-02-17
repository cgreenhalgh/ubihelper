/**
 * 
 */
package uk.ac.horizon.ubihelper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.HashMap;

import android.util.Log;

/**
 * @author cmg
 *
 */
public class HttpClientHandler extends Thread {
	private static final String TAG = "ubihelper-httpclient";
	private Service service;
	private Socket s;
	private int mStatus = 0;
	private String mMessage = null;
	private String mResponseBody = null;

	public HttpClientHandler(Service service, Socket s) {
		this.service = service;
		this.s = s;
	}
	
	public void run() {
		try {
			// parse request
			BufferedInputStream bis = new BufferedInputStream(s.getInputStream());
			//InputStreamReader isr = new InputStreamReader(bis, "US-ASCII");
			// request line
			String request = readLine(bis);
			Log.d(TAG,"Request: "+request);
			String requestEls[] = request.split(" ");
			if (requestEls.length!=3) 
				throw HttpError.badRequest("Mal-formatted request line ("+requestEls.length+" elements)");
			if (!"GET".equals(requestEls[0]) && !"POST".equals(requestEls[0]))
				throw HttpError.badRequest("Unsupported operation ("+requestEls[0]+")");
			String path = requestEls[1];
			// header lines
			HashMap<String,String> headers = new HashMap<String,String>();
			while (true) {
				String header = readLine(bis);
				if (header.length()==0)
					break;
				Log.d(TAG,"Header line: "+header);
				int ix = header.indexOf(":");
				if (ix<0) 
					throw HttpError.badRequest("Mal-formed header line ("+header+")");
				String name = header.substring(0,ix).trim().toLowerCase();
				String value = header.substring(ix+1).trim();
				headers.put(name, value);
			}
			// content body
			int length = -1;
			String contentLength = headers.get("content-length");
			if (contentLength!=null)
				try {
					length = Integer.parseInt(contentLength);
				}
				catch (NumberFormatException nfe) {
					throw HttpError.badRequest("Invalid content-length ("+contentLength+")");
				}
			Log.d(TAG,"Read request body "+length+" bytes");
			byte buf[] = new byte[length>=0 ? length : 1000];
			int count = 0;
			while (length<0 || count<length) {
				int n = bis.read(buf, count, buf.length-count);
				if (n<0) {
					if (length<0)
						break;
					else
						throw HttpError.badRequest("Request body too short ("+count+"/"+length+")");
				}
				count += n;
				if (length<0 && count>=buf.length) {
					// grow buffer
					byte nbuf[] = new byte[buf.length*2];
					System.arraycopy(buf, 0, nbuf, 0, count);
					buf = nbuf;
				}
			}
			String requestBody = new String(buf,0,count,"UTF-8");
			Log.d(TAG,"get "+path+" with "+requestBody);
			
			synchronized (this) {
				service.postRequest(path, requestBody, new HttpContinuation() {

					public void done(int status, String message, String body) {
						mStatus = status;
						mMessage = message;
						mResponseBody = body;
						synchronized (HttpClientHandler.this) {
							HttpClientHandler.this.notify();
						}
					}
					
				});
				// timeout 10s?!
				try {
					wait(10000);
				} 
				catch (InterruptedException ie) {
					throw HttpError.badRequest("Handle request interrupted");
				}
			}
			if (mStatus==0)
				throw HttpError.badRequest("Handle request timed out");

			BufferedOutputStream bos = new BufferedOutputStream(s.getOutputStream());
			OutputStreamWriter osw = new OutputStreamWriter(bos, "US-ASCII");
			osw.write("HTTP/1.0 "+mStatus+" "+mMessage+"\r\n");
			byte resp [] = mResponseBody==null ? new byte[0] : mResponseBody.getBytes("UTF-8");
			osw.write("Content-Length: "+resp.length+"\r\n");
			osw.write("Content-Type: application/json\r\n");
			osw.write("\r\n");
			osw.flush();
			bos.write(resp);
			bos.close();
		}
		catch (IOException ie) {
			Log.d(TAG,"Error: "+ie.getMessage());
		} catch (HttpError e) {
			Log.d(TAG,"Sending error: "+e.getMessage());
			sendError(e.getStatus(), e.getMessage());
		}
		try {
			s.close();
		}
		catch (IOException e) {
			/* ignore */
		}
	}

	private void sendError(int status, String message) {
		try {
			BufferedOutputStream bos = new BufferedOutputStream(s.getOutputStream());
			OutputStreamWriter osw = new OutputStreamWriter(bos, "US-ASCII");
			osw.write("HTTP/1.0 "+status+" "+message+"\r\n");
			osw.write("\r\n");
			osw.close();
		} catch (Exception e) {
			Log.d(TAG,"Error sending error: "+e.getMessage());
			/* ignore */
		}
	}

	private String readLine(InputStreamReader isr) throws IOException {
		StringBuilder sb = new StringBuilder();
		while (true) {
			int c = isr.read();
			if (c<0)
				break;
			if (c=='\r')
				// skip
				continue;
			if (c=='\n')
				break;
			sb.append((char)c);
		}
		return sb.toString();
	}

	private String readLine(BufferedInputStream bis) throws IOException {
		StringBuilder sb = new StringBuilder();
		while (true) {
			int c = bis.read();
			if (c<0)
				break;
			if (c=='\r')
				// skip
				continue;
			if (c=='\n')
				break;
			sb.append((char)c);
		}
		return sb.toString();
	}

}
