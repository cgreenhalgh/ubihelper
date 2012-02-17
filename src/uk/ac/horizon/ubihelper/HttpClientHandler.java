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

import android.util.Log;

/**
 * @author cmg
 *
 */
public class HttpClientHandler extends Thread {
	private static final String TAG = "ubihelper-httpclient";
	private Service service;
	private Socket s;
	
	public HttpClientHandler(Service service, Socket s) {
		this.service = service;
		this.s = s;
	}
	
	public void run() {
		try {
			BufferedInputStream bis = new BufferedInputStream(s.getInputStream());
			InputStreamReader isr = new InputStreamReader(bis, "US-ASCII");
			String request = readLine(isr);
			Log.d(TAG,"Request: "+request);
			while (true) {
				String header = readLine(isr);
				if (header.length()==0)
					break;
				Log.d(TAG,"Header line: "+header);
			}
			// ...
			BufferedOutputStream bos = new BufferedOutputStream(s.getOutputStream());
			OutputStreamWriter osw = new OutputStreamWriter(bos, "US-ASCII");
			osw.write("HTTP/1.0 200 OK\r\n");
			osw.write("\r\n");
		}
		catch (IOException ie) {
			Log.d(TAG,"Error: "+ie.getMessage());
		}
		try {
			s.close();
		}
		catch (IOException e) {
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

}
