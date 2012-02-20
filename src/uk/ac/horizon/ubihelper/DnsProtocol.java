/**
 * 
 */
package uk.ac.horizon.ubihelper;

import java.io.IOException;
//import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/** Partial DNS protocol implementation for mDNS peer discovery
 * @author cmg
 *
 */
public class DnsProtocol {
	public static final short CLASS_IN = 1;
	public static final short TYPE_A = 1;
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

	public byte [] bytes;
	public int len;
	public boolean response;
	
	// to bytes
	public void marshall() {
		bytes = new byte[512];
		int pos [] = new int[1];
		// header
		// id
		marshall2(id,pos);
		// flags
		int flags = 0;
		if (response)
			flags |= 0x8000; // high bit
		marshall2(flags,pos);
		// nq
		marshall2(queries.length, pos);
		// na
		marshall2(answers.length, pos);
		marshall2(0,pos);
		marshall2(0,pos);
		// queries
		for (int i=0; i<queries.length; i++) {
			Query q = queries[i];
			marshall(q.name, pos);
			marshall2(q.type, pos);
			marshall2(q.rclass, pos);
		}
		// answers
		for (int i=0; i<answers.length; i++) {
			RR r = answers[i];
			marshall(r.name, pos);
			marshall2(r.type, pos);
			marshall2(r.rclass, pos);
			marshall4(r.ttl, pos);
			marshall2(r.rdata.length, pos);
			System.arraycopy(r.rdata, 0, bytes, pos[0], r.rdata.length);
			pos[0] += r.rdata.length;
		}
		len = pos[0];
	}
	private void marshall(String name, int pos[]) {
		String ns[] = name.split("\\.");
		for (int i=0; i<ns.length; i++) {
			// us-ascii?!
			//byte bs[];
			//try {
				int l = ns[i].length();
				//bs = ns[i].getBytes("US-ASCII");
				bytes[pos[0]++] = (byte)l;
				//System.arraycopy(bs, 0, bytes, pos[0], bs.length);
				//pos[0] += bs.length;
				for (int j=0; j<l; j++)
					bytes[pos[0]++] = (byte)(ns[i].charAt(j) & 0x7f);
			//} catch (UnsupportedEncodingException e) {
				/* ignore?! */
			//}
		}
		bytes[pos[0]++] = 0;
	}
	private void marshall2(int value, int offset[]) {
		bytes[offset[0]++] = (byte)((value >> 8) & 0xff);
		bytes[offset[0]++] = (byte)(value & 0xff);
	}
	private void marshall4(int value, int offset[]) {
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
		id = (short)unmarshall2(pos);
		// flags
		int flags = unmarshall2(pos);
		response = (flags & 0x8000)!=0;
		// nq
		int nq = unmarshall2(pos);
		queries = new Query[nq];
		// na
		int na = unmarshall2(pos);
		answers = new RR[na];
		unmarshall2(pos);
		unmarshall2(pos);
		// queries
		for (int i=0; i<queries.length; i++) {
			Query q = new Query();
			queries[i] = q;
			q.name = unmarshall(pos);
			q.type = (short)unmarshall2(pos);
			q.rclass = (short)unmarshall2(pos);
		}
		// answers
		for (int i=0; i<answers.length; i++) {
			RR r = new RR();
			answers[i] = r;
			r.name = unmarshall(pos);
			r.type = (short)unmarshall2(pos);
			r.rclass = (short)unmarshall2(pos);
			r.ttl = unmarshall4(pos);
			int rdatalen = unmarshall2(pos);
			if (pos[0]+rdatalen>len)
				throw new IOException("Truncated bytes in answer "+i+" rdata at "+pos[0]);
			r.rdata = new byte[rdatalen];
			System.arraycopy(bytes, pos[0], r.rdata, 0, r.rdata.length);
			pos[0] += r.rdata.length;
		}
	}
	private String unmarshall(int pos[]) throws IOException {
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
	private int unmarshall2(int offset[]) throws IOException {
		if (offset[0]+2>len)
			throw new IOException("Truncated bytes in int16 at "+offset[0]);
		return ((bytes[offset[0]++] & 0xff) << 8) | (bytes[offset[0]++] & 0xff);
	}
	private int unmarshall4(int offset[]) throws IOException {
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
