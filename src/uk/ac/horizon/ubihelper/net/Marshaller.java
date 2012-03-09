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
package uk.ac.horizon.ubihelper.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import uk.ac.horizon.ubihelper.net.Message.Type;

/** To convert between a Message and one or more Fragments.
 * 
 * @author cmg
 *
 */
public class Marshaller {
	private static final String TAG = "ubihelper-marshall";

	private enum Stage { STAGE_FIRSTLINE, STAGE_FIRSTLINE_NL,
		STAGE_HEADERLINES, STAGE_HEADERLINES_NL, STAGE_HEADERLINES_END, 
		STAGE_BODY, STAGE_DONE };
	
	private final static int FRAGMENT_SIZE = 2000;
	
	
	public static Queue<Fragment> fragment(Message m, int messageid) throws IOException {
		Queue<Fragment> fs = new LinkedList<Fragment>();
		Stage stage = Stage.STAGE_FIRSTLINE;
		switch (m.type){
		case HELLO:
		case MANAGEMENT:
			if (m.firstLine!=null || (m.headerLines!=null && m.headerLines.size()>0))
				throw new IOException ("Message has first line or headers when shouldn't (HELLO or MANAGEMENT)");
			stage = Stage.STAGE_BODY;
			break;
		case UNDEFINED:
			throw new IOException ("Asked to fragment an Undefined message");
		case REQUEST:
		case RESPONSE:
			if (m.firstLine==null)
				throw new IOException("Request or response message has no first line");
			break;
		}
		int header = 0;
		int fragmentid = 0;
		Fragment f = getNewFragment(m, messageid, fragmentid);
		fs.add(f);
		Charset charset = Charset.forName("UTF-8");
		CharsetEncoder ce = charset.newEncoder();
		ce.reset();
		CharBuffer in = null;
		f.offset = PeerConnection.HEADER_SIZE;
		ByteBuffer out = ByteBuffer.wrap(f.payload, f.offset, f.payload.length-f.offset);
		boolean done = false;
		while (stage!=Stage.STAGE_DONE || in!=null) {
			CoderResult cr = null;
			if (in==null || in.remaining()==0) {
				// next chars
				switch(stage) {
				case STAGE_FIRSTLINE: 
					if (m.firstLine!=null) {
						in = CharBuffer.wrap(m.firstLine);
						stage = Stage.STAGE_FIRSTLINE_NL;
					}
					else 
						// will skip on
						stage = Stage.STAGE_HEADERLINES;
					break;
				case STAGE_FIRSTLINE_NL: 
					in = CharBuffer.wrap("\n");
					stage = Stage.STAGE_HEADERLINES;
					break;
				case STAGE_HEADERLINES:
					if (m.headerLines!=null && header<m.headerLines.size()) {
						in = CharBuffer.wrap(m.headerLines.get(header));
						header++;
						stage = Stage.STAGE_HEADERLINES_NL;
					}
					else if (m.firstLine!=null || (m.headerLines!=null && m.headerLines.size()>0))
						stage = Stage.STAGE_HEADERLINES_END;
					else
						stage = Stage.STAGE_BODY;
					break;
				case STAGE_HEADERLINES_NL: 
					in = CharBuffer.wrap("\n");
					stage = Stage.STAGE_HEADERLINES;
					break;
				case STAGE_HEADERLINES_END: 
					in = CharBuffer.wrap("\n");
					stage = Stage.STAGE_BODY;
					if (m.body==null)
						done = true;
					break;
				case STAGE_BODY:
					if (m.body!=null) {
						in = CharBuffer.wrap(m.body);
						done = true;
					}
					stage = Stage.STAGE_DONE;
				}				
			} else {
				cr = ce.encode(in, out, done);
				f.length = out.position()-f.offset;
				if (cr.isOverflow()) {
					if (fragmentid==0) {
						fragmentid = 1;
						f.fragmentid = fragmentid;
					}
					f.flags |= PeerConnection.HEADER_FLAG_MORE;
					fragmentid++;
					f = getNewFragment(m, messageid, fragmentid);
					fs.add(f);
					f.offset = PeerConnection.HEADER_SIZE;
					out = ByteBuffer.wrap(f.payload, f.offset, f.payload.length-f.offset);
				}
				else if (cr.isError())
					throw new IOException("Error mapping characters");
				else if (cr.isUnderflow()) {
					in = null;
					if (done) {
						ce.flush(out);
					}
				}
			}
		}
		return fs;
	}
	private static Fragment getNewFragment(Message m, int messageid, int fragmentid) {
		Fragment f= new Fragment();
		f.fragmentid = fragmentid;
		f.messageid = messageid;
		f.messagetype = m.type;
		f.flags = 0;
		f.payload = new byte[FRAGMENT_SIZE];
		f.length = 0;
		f.offset = 0;
		return f;
	}
	public static Message assemble(Queue<Fragment> fs) throws IOException {
		Message m = new Message();
		Fragment f = fs.peek();
		if (f==null)
			throw new IOException("assemble called with no fragments");
		m.type = f.messagetype;
		Stage stage = Stage.STAGE_FIRSTLINE;
		switch(m.type) {
		case HELLO:
		case MANAGEMENT:
			stage = Stage.STAGE_BODY;
			break;
		case UNDEFINED:
			throw new IOException("undefined message type in assemble");
		}
		Charset charset = Charset.forName("UTF-8");
		CharsetDecoder cd = charset.newDecoder();
		cd.reset();
		char cs[] = null;
		CharBuffer out = null;
		ByteBuffer in = null;
		boolean last = false;
		int pos = 0;
		int nextnl = 0;
		f = null;
		while (stage!=Stage.STAGE_DONE) {
			if (in==null) {
				if (f==null) {
					f = fs.poll();
					if (f==null) 
						break; //?
					pos = f.offset;
					log_d(TAG,"Next fragment, pos="+pos);
				}				
				// looking for newlines?!
				nextnl = -1;				
				last = false;
				if (stage!=Stage.STAGE_BODY) {
					for (int i=pos; i<f.offset+f.length; i++) {
						if (f.payload[i]=='\n') {
							nextnl = i;
							break;
						}
					}
				}
				in = ByteBuffer.wrap(f.payload, pos, nextnl>=0 ? nextnl-pos : f.length+f.offset-pos);
				log_d(TAG,"new buffer from "+pos+" length "+(nextnl>=0 ? nextnl-pos : f.length+f.offset-pos));
				if (nextnl<0 || nextnl+1>=f.offset+f.length) {
					log_d(TAG,"Fragment fully used");
					f = null;
					if (fs.peek()==null) {
						last = true;
						log_d(TAG,"And it was the Last fragment");
					}
				}
				else {
					pos = nextnl+1;
					last = true;
					log_d(TAG,"Next segment at "+pos);
				}
			}
			if (out==null) {
				cs = new char[100];
				out = CharBuffer.wrap(cs);
				if (out.isReadOnly())
					throw new IOException ("Sorry - char buffer is read-only");
			}
			CoderResult cr = cd.decode(in, out, last);
			if (cr.isError()) 
				throw new IOException("Error decoding characters");
			else if (cr.isOverflow()) {
				char ocs [] = cs;
				cs = new char[ocs.length*2];
				System.arraycopy(ocs, 0, cs, 0, ocs.length);
				//log_d(TAG,"Grow string on overflow to "+cs.length);
				int p = out.position();
				out = CharBuffer.wrap(cs);
				out.position(p);
			}
			else if (cr.isUnderflow()) {
				// input used up
				in = null;
				if (last) {
					// end of element
					cd.flush(out);
					cd.reset();
					String s = new String(cs, 0, out.position());
					out = null;
					log_d(TAG, "underflow at stage "+stage+" -> "+s);
					switch(stage) {
					case STAGE_FIRSTLINE:
						m.firstLine = s;
						stage = Stage.STAGE_HEADERLINES;
						break;
					case STAGE_HEADERLINES:
						if (m.headerLines==null)
							m.headerLines = new LinkedList<String>();
						if (s.length()==0)
							stage = Stage.STAGE_BODY;
						else
							m.headerLines.add(s);
						break;
					case STAGE_BODY:
						m.body = s;
						stage = Stage.STAGE_DONE;
						break;
					}
				}
				else
					log_d(TAG,"underflow but not last at stage "+stage);
			}
		}
		return m;
	}

	/** test */
	public static void main(String args[]) {
		Message m = new Message();
		m.type = Type.HELLO;
		m.body = "application/x-ubihelper;version=1";
		test(m);
		m = new Message();
		m.type = Type.REQUEST;
		m.firstLine = "GET /asd/as/dads//sa/d/dsa";
		m.headerLines = new LinkedList<String>();
		m.headerLines.add("asdpojasd: ApsojdoAIHSdoIHOIDSHOIsha");
		m.body = "p'asjdpajdpajs pjsa dpjasp jdpjdsa apsoj";
		test(m);
		char cs[] = new char[5000];
		Arrays.fill(cs, 'x');
		String s= new String(cs);
		m = new Message();
		m.type = Type.MANAGEMENT;
		m.body = s;
		test(m);
		m = new Message();
		m.type = Type.REQUEST;
		m.firstLine = "GET /asd/as/dads//sa/d/dsa";
		m.headerLines = new LinkedList<String>();
		m.body = s;
		test(m);
		m = new Message();
		m.type = Type.REQUEST;
		m.firstLine = "GET /asd/as/dads//sa/d/dsa";
		m.headerLines = new LinkedList<String>();
		m.headerLines.add("pajdspaojdpojapodjs: aiojdsoiashjodihasoidhosaihoi");
		m.headerLines.add("asiohdosaihoiadshoidshajsdopas: OAIShodihasoihdioshoaishd");
		m.headerLines.add("asdpojasd: ApsojdoAIHSdoIHOIDSHOIsha");
		m.body = s;
		test(m);
		
		m = new Message();
		m.type = Type.RESPONSE;
		m.firstLine = "200 OK apsjdpoajsd";
		m.headerLines = new LinkedList<String>();
		m.body = s;
		test(m);
	}
	public static void test(Message m) {
		Queue<Fragment> fs = new LinkedList<Fragment>();
		try {
			System.out.println("Message: "+m);
			fs = fragment(m, 1);
			System.out.println("-> Fragments: "+fs);
			Message m2 = assemble(fs);
			System.out.println("-> Message: "+m2);
			boolean ok = m.equals(m2);
			if (ok)
				System.out.println("Matches!");
			else {
				System.out.println("ERROR!");
				if (m.body!=null)
					System.out.println("- m.body length = "+m.body.length());
				if (m2.body!=null)
					System.out.println("- m2.body length = "+m2.body.length());
			}
		}
		catch (Exception e) {
			System.out.println("ERROR: "+e);
			e.printStackTrace();
		}
	}
	private static void log_w(String tag, String msg) {
		PeerConnection.log("Warning", tag, msg);
	}
	private static void log_d(String tag, String msg) {
		PeerConnection.log("Debug", tag, msg);
	}
	private static void log_e(String tag, String msg) {
		PeerConnection.log("Error", tag, msg);
	}
}
