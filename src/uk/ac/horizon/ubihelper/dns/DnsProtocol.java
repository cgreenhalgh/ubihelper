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

package uk.ac.horizon.ubihelper.dns;

import java.io.IOException;
import java.net.InetAddress;
//import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Vector;

/** Partial DNS protocol implementation for mDNS peer discovery
 * @author cmg
 *
 */
public class DnsProtocol {
	public static final short CLASS_IN = 1;
	public static final short TYPE_A = 1;
	public static final short TYPE_PTR = 12;
	public static final short TYPE_SRV = 33;
	public static class Query {
		public String name;
		public short type;
		public short rclass;
		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return "Query [name=" + name + ", type=" + type + ", rclass="
					+ rclass + "]";
		}
	}
	public Query queries[];
	public static class RR {
		public String name;
		public short type;
		public short rclass;
		public int ttl;
		public byte rdata[];
		public InetAddress src;
		
		public RR() {}
		
		public RR(String name, short type, short rclass, int ttl, byte[] rdata) {
			super();
			this.name = name;
			this.type = type;
			this.rclass = rclass;
			this.ttl = ttl;
			this.rdata = rdata;
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return "RR [name=" + name + ", type=" + type + ", rclass=" + rclass
					+ ", ttl=" + ttl + ", rdata=" + Arrays.toString(rdata)
					+ "]";
		}
		
	}
	public RR answers[];
	public short id;
	
	static DnsProtocol getAQuery(String name) {
		DnsProtocol p = new DnsProtocol();
		p.queries = new Query[1];
		Query q = new Query();
		p.queries[0] = q;
		q.name = name;
		q.type = TYPE_A;
		q.rclass = CLASS_IN;
		p.answers = new RR[0];
		return p;
	}

	static DnsProtocol getAResponse(short id, String name, byte [] rdata) {
		DnsProtocol p = new DnsProtocol();
		p.response = true;
		p.id = id;
		p.queries = new Query[0];
		p.answers = new RR[1];
		RR r = new RR();
		r.name = name;
		r.type = TYPE_A;
		r.rclass = CLASS_IN;
		r.ttl = 600;
		r.rdata = rdata;
		p.answers[0] = r;
		return p;
	}

	public static class SrvData {
		public int priority; // 16 bit
		public int weight; // 16 bit
		public int port; // 16 bit
		public String target;
		public SrvData() {}
		/**
		 * @param priority
		 * @param weight
		 * @param port
		 * @param target
		 */
		public SrvData(int priority, int weight, int port, String target) {
			super();
			this.priority = priority;
			this.weight = weight;
			this.port = port;
			this.target = target;
		}
		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return "SvrData [priority=" + priority + ", weight=" + weight
					+ ", port=" + port + ", target=" + target + "]";
		}
	}
	public static byte[] srvToData(SrvData srv) {
		int len = 2+2+2+getMarhsalledLength(srv.target);
		byte b[] = new byte[len];
		int p[] = new int[1];
		marshall2(b, srv.priority, p);
		marshall2(b, srv.weight, p);
		marshall2(b, srv.port, p);
		marshall(b, srv.target, p);
		return b;
	}
	public static SrvData srvFromData(byte b[]) throws IOException {
		SrvData srv = new SrvData();
		int p[] = new int[1];
		srv.priority = unmarshall2(b, p, b.length);
		srv.weight = unmarshall2(b, p, b.length);
		srv.port = unmarshall2(b, p, b.length);
		// TODO this doesn't handle references properly (should be in main packet,
		// not just rdata)
		srv.target = unmarshall(b, p, b.length);
		return srv;
	}
	public static byte[] ptrToData(String instance, String domain) {
		int len = getMarhsalledLength(instance,  domain);
		byte b[] = new byte[len];
		int p[] = new int[1];
		marshallWord(b, instance, p);
		marshall(b, domain, p);
		return b;
	}
	public static String[] ptrFromData(byte b[]) throws IOException {
		int p[] = new int[1];
		return unmarshallArray(b, p, b.length);
	}
	/** for dns-sd ptr record */
	public static int getMarhsalledLength(String s1, String s2) {
		String [] ns = s2.split("\\.");
		return 1+s1.length()+getMarshalledLength(ns);
	}
	public static int getMarhsalledLength(String s) {
		String [] ns = s.split("\\.");
		return getMarshalledLength(ns);
	}
	public static int getMarshalledLength(String ns[]) {
		int len = 1;
		for (int i=0; i<ns.length; i++) 
			len = len+1+ns[i].length();
		return len;
	}
	
	public byte [] bytes;
	public int len;
	public boolean response;
	
	// to bytes
	public void marshall() {
		bytes = new byte[512];
		int pos [] = new int[1];
		// header
		// id
		marshall2(bytes, id,pos);
		// flags
		int flags = 0;
		if (response)
			flags |= 0x8000; // high bit
		marshall2(bytes, flags,pos);
		// nq
		marshall2(bytes, queries.length, pos);
		// na
		marshall2(bytes, answers.length, pos);
		marshall2(bytes, 0,pos);
		marshall2(bytes, 0,pos);
		// queries
		for (int i=0; i<queries.length; i++) {
			Query q = queries[i];
			marshall(bytes, q.name, pos);
			marshall2(bytes, q.type, pos);
			marshall2(bytes, q.rclass, pos);
		}
		// answers
		for (int i=0; i<answers.length; i++) {
			RR r = answers[i];
			marshall(bytes, r.name, pos);
			marshall2(bytes, r.type, pos);
			marshall2(bytes, r.rclass, pos);
			marshall4(bytes, r.ttl, pos);
			marshall2(bytes, r.rdata.length, pos);
			System.arraycopy(r.rdata, 0, bytes, pos[0], r.rdata.length);
			pos[0] += r.rdata.length;
		}
		len = pos[0];
	}
	private static void marshall(byte bytes[], String name, int pos[]) {
		String ns[] = name.split("\\.");
		marshall(bytes, ns, pos);
	}
	private static void marshall(byte bytes[], String ns[], int pos[]) {
		for (int i=0; i<ns.length; i++) {
			// us-ascii?!
			//byte bs[];
			//try {
				marshallWord(bytes, ns[i], pos);
			//} catch (UnsupportedEncodingException e) {
				/* ignore?! */
			//}
		}
		bytes[pos[0]++] = 0;
	}
	private static void marshallWord(byte[] bytes, String ns, int[] pos) {
		int l = ns.length();
		//bs = ns[i].getBytes("US-ASCII");
		bytes[pos[0]++] = (byte)l;
		//System.arraycopy(bs, 0, bytes, pos[0], bs.length);
		//pos[0] += bs.length;
		for (int j=0; j<l; j++)
			bytes[pos[0]++] = (byte)(ns.charAt(j) & 0x7f);
	}

	private static void marshall2(byte bytes[], int value, int offset[]) {
		bytes[offset[0]++] = (byte)((value >> 8) & 0xff);
		bytes[offset[0]++] = (byte)(value & 0xff);
	}
	private static void marshall4(byte bytes[], int value, int offset[]) {
		bytes[offset[0]++] = (byte)((value >> 24) & 0xff);
		bytes[offset[0]++] = (byte)((value >> 16) & 0xff);
		bytes[offset[0]++] = (byte)((value >> 8) & 0xff);
		bytes[offset[0]++] = (byte)(value & 0xff);
	}
	// to bytes
	public void unmarshall() throws IOException {
		int pos [] = new int[1]; 
		if (len>bytes.length)
			throw new IOException("Length > byte array size");
		// header
		// id
		id = (short)unmarshall2(bytes, pos, len);
		// flags
		int flags = unmarshall2(bytes, pos, len);
		response = (flags & 0x8000)!=0;
		// nq
		int nq = unmarshall2(bytes, pos, len);
		queries = new Query[nq];
		// na
		int na = unmarshall2(bytes, pos, len);
		answers = new RR[na];
		unmarshall2(bytes, pos, len);
		unmarshall2(bytes, pos, len);
		// queries
		for (int i=0; i<queries.length; i++) {
			Query q = new Query();
			queries[i] = q;
			q.name = unmarshall(bytes, pos, len);
			q.type = (short)unmarshall2(bytes, pos, len);
			q.rclass = (short)unmarshall2(bytes, pos, len);
		}
		// answers
		for (int i=0; i<answers.length; i++) {
			RR r = new RR();
			answers[i] = r;
			r.name = unmarshall(bytes, pos, len);
			r.type = (short)unmarshall2(bytes, pos, len);
			r.rclass = (short)unmarshall2(bytes, pos, len);
			r.ttl = unmarshall4(bytes, pos, len);
			int rdatalen = unmarshall2(bytes, pos, len);
			if (pos[0]+rdatalen>len)
				throw new IOException("Truncated bytes in answer "+i+" rdata at "+pos[0]);
			r.rdata = new byte[rdatalen];
			System.arraycopy(bytes, pos[0], r.rdata, 0, r.rdata.length);
			pos[0] += r.rdata.length;
		}
	}
	private static String unmarshall(byte bytes[], int pos[], int len) throws IOException {
		StringBuilder sb = new StringBuilder();
		int p = pos[0];
		boolean refed = false;
		while (p<len) {
			int l = bytes[p++] & 0xff;
			if (!refed)
				pos[0] = p;
			if (l==0)
				break;
			if ((l & 0xc0)==0xc0) {
				// reference
				int ref = ((l & ~0xc0) << 8) | (bytes[pos[0]++] & 0xff);
				if (ref<0 || ref>=len)
					throw new IOException("Reference out of range: "+ref);
				p = ref;
				refed = true;
				continue;
			}
			if (p+l>len)
				throw new IOException("Truncated bytes in name at "+p);
			if (sb.length()>0)
				sb.append(".");
			sb.append(new String(bytes, p, l));
			p += l;
			if (!refed)
				pos[0] = p;
		}
		return sb.toString();
	}
	private static String [] unmarshallArray(byte bytes[], int pos[], int len) throws IOException {
		Vector<String> ns =new Vector<String>();
		int p = pos[0];
		boolean refed = false;
		while (p<len) {
			int l = bytes[p++] & 0xff;
			if (!refed)
				pos[0] = p;
			if (l==0)
				break;
			if ((l & 0xc0)==0xc0) {
				// reference
				int ref = ((l & ~0xc0) << 8) | (bytes[pos[0]++] & 0xff);
				if (ref<0 || ref>=len)
					throw new IOException("Reference out of range: "+ref);
				p = ref;
				refed = true;
				continue;
			}
			if (p+l>len)
				throw new IOException("Truncated bytes in name at "+p);
			ns.add(new String(bytes, p, l));
			p += l;
			if (!refed)
				pos[0] = p;
		}
		return ns.toArray(new String[ns.size()]);
	}
	private static int unmarshall2(byte bytes[], int offset[], int len) throws IOException {
		if (offset[0]+2>len)
			throw new IOException("Truncated bytes in int16 at "+offset[0]);
		return ((bytes[offset[0]++] & 0xff) << 8) | (bytes[offset[0]++] & 0xff);
	}
	private static int unmarshall4(byte bytes[], int offset[], int len) throws IOException {
		if (offset[0]+2>len)
			throw new IOException("Truncated bytes in int32 at "+offset[0]);
		return ((bytes[offset[0]++] & 0xff) << 24) | 
				((bytes[offset[0]++] & 0xff) << 16) | 
				((bytes[offset[0]++] & 0xff) << 8) | 
				(bytes[offset[0]++] & 0xff);
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "DnsProtocol [queries=" + Arrays.toString(queries)
				+ ", answers=" + Arrays.toString(answers) + ", id=" + id
				+ ", bytes=" + (bytes!=null ? bytes.length+"b" : "null") /*Arrays.toString(bytes)*/ 
				+ ", len=" + len
				+ ", response=" + response + "]";
	}
	public static void dump(byte b[], int offset, int len) {
		for (int i=0; i < len; i++) {
			if (i % 10 == 0) {
				if (i>0)
					System.out.println();
				System.out.print(i+": ");
			}
			dump(b[offset+i]);
		}
		System.out.println();
	}
	public static void dump(byte b) {
		System.out.print(""+hex(b >> 4)+hex(b));
		System.out.print(" ");
	}
	public static char hex(int b) {
		b = b&0xf;
		if (b>=10)
			return (char)('A'+b-10);
		return (char)('0'+b);
	}
	
	/** some tests */
	public static void main(String[] args) {
		DnsProtocol p = DnsProtocol.getAQuery("some.name.or.other");
		System.out.println("Generated Dns packet: "+p);
		p.marshall();
		System.out.println("-> "+p.len+" bytes:");
		dump(p.bytes, 0, p.len);
		try {
			System.out.println("Unmarshall...");
			p.unmarshall();
			System.out.println("-> "+p);
		} catch (IOException e) {
			System.out.println("error unmarshalling: "+e);
			e.printStackTrace();
		}
		byte addr[] = new byte[4];
		addr[0] = 0x1; addr[1] = 0x2; addr[2] = 0x3; addr[3] = 0x4;
		DnsProtocol q = DnsProtocol.getAResponse(p.id, p.queries[0].name, addr);
		System.out.println("Generated Dns response: "+q);
		q.marshall();
		System.out.println("-> "+q.len+" bytes:");
		dump(q.bytes, 0, q.len);
		try {
			System.out.println("Unmarshall...");
			q.unmarshall();
			System.out.println("-> "+q);
		} catch (IOException e) {
			System.out.println("error unmarshalling: "+e);
			e.printStackTrace();
		}
		
	}
}
