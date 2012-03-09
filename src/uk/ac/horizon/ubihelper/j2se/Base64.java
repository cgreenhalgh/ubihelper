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

package uk.ac.horizon.ubihelper.j2se;

/**
 * @author cmg
 *
 */
public class Base64 {
	static char chars[] = new char[] {
		'A','B','C','D','E','F','G','H',//8
		'I','J','K','L','M','N','O','P',//16
		'Q','R','S','T','U','V','W','X',//24
		'Y','Z','a','b','c','d','e','f',//32
		'g','h','i','j','k','l','m','n',//40
		'o','p','q','r','s','t','u','v',//48
		'w','x','y','z','0','1','2','3',//56
		'4','5','6','7','8','9','+','/' //64
	};
	static int bytes[] = new int[128];
	static {
		for (int i=0; i<chars.length; i++)
			bytes[(int)chars[i]] = i;
		bytes[(int)'='] = 0;
	}
	public static String encode(byte data[]) {
		StringBuilder sb = new StringBuilder();
		int pos = 0;
		while(pos+2<data.length) {
			int bs = ((data[pos++] & 0xff) << 16) |
					((data[pos++] & 0xff) << 8) |
					((data[pos++] & 0xff));
			sb.append(chars[(bs >> 18) & 0x3f]);
			sb.append(chars[(bs >> 12) & 0x3f]);
			sb.append(chars[(bs >> 6) & 0x3f]);
			sb.append(chars[(bs) & 0x3f]);			
		}
		if (pos<data.length) {
			if (pos+1<data.length) {
				// 2 bytes
				int bs = ((data[pos++] & 0xff) << 16) |
						((data[pos++] & 0xff) << 8);
				sb.append(chars[(bs >> 18) & 0x3f]);
				sb.append(chars[(bs >> 12) & 0x3f]);
				sb.append(chars[(bs >> 6) & 0x3f]);
				sb.append("=");
			} else {
				// 1 byte
				int bs = ((data[pos++] & 0xff) << 16);
				sb.append(chars[(bs >> 18) & 0x3f]);
				sb.append(chars[(bs >> 12) & 0x3f]);
				sb.append("=");
				sb.append("=");
			}
		}
		return sb.toString();
	}
	/** NB no errors are raised for badly formatted base64.
	 * E.g. = included in body will cause early termination.
	 * @param s
	 * @return
	 */
	public static byte[] decode(String s) {
		// size?
		int spos = 0;
		int size = 0;
		while (spos<s.length()) {
			char c = s.charAt(spos++);
			if (Character.isWhitespace(c))
				continue;
			if (c=='=')
				break;
			size++;
		}
		int bsize = (size*3)/4;
		byte data[] = new byte[bsize];
		int bpos = 0;
		spos = 0;
		char cs[] = new char[4];
		int cpos = 0;
		while (spos<s.length()) {
			char c = s.charAt(spos++);
			if (Character.isWhitespace(c))
				continue;
			cs[cpos++] = c;
			if (cpos==4) {
				int bs = (bytes[cs[0] & 0x7f] << 18) |
						(bytes[cs[1] & 0x7f] << 12) |
						(bytes[cs[2] & 0x7f] << 6) |
						(bytes[cs[3] & 0x7f]);
				data[bpos++] = (byte)((bs >> 16) & 0xff);
				if (cs[2]=='=') 
					break;
				data[bpos++] = (byte)((bs >> 8) & 0xff);
				if (cs[3]=='=')
					break;
				data[bpos++] = (byte)((bs) & 0xff);					
				cpos = 0;
			}
		}
		return data;
	}
	
	public static void main(String args[]) {
		byte bs1[] = new byte[8];
		test(bs1);
		byte bs2[] = new byte[9];
		test(bs2);
		byte bs3[] = new byte[10];
		test(bs3);
	}
	private static void test(byte[] bs) {
		for (int i=0; i<bs.length; i++)
			bs[i] = (byte)(i+1);
		String enc = encode(bs);
		System.out.println("Test "+bs.length+" bytes -> "+enc);
		byte rs[] = decode(enc);
		if (rs.length!=bs.length) {
			System.err.println("Error: encoded "+bs.length+" bytes -> "+rs.length+" bytes");
			return;
		}
		else {
			for (int i=0; i<bs.length; i++) {
				if (bs[i]!=rs[i]) {
					System.err.println("Error: byte "+i+" wrong ("+bs[i]+" vs "+rs[i]+")");
					return;
				}
			}
		}
		System.out.println("OK");
	}
}
