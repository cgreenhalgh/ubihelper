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

import java.util.Arrays;
import java.util.Comparator;

import uk.ac.horizon.ubihelper.net.Message.Type;

/** peer network "link-layer" like fragment */
public class Fragment {
	public Message.Type messagetype;
	public byte flags;
	/** message ID, note only 16 transfered, but unsigned */
	public int messageid;
	/** fragment ID, note only 16 transfered, but unsigned */
	public int fragmentid;
	/** fragment payload length, note only 16 transfered, but unsigned */
	public int length;
	/** payload data array - length is payload */
	public byte payload[];
	/** payload data offset - may be zero for dedicated buffer */
	public int offset;
	/** priority (send-only) - as far as possible messages and fragments will be offered to the network in priority order.
	 * 0 (default) is low. 
	 */
	public int priority;
	
	public Fragment() {}

	/**
	 * @param messagetype
	 * @param flags
	 * @param messageid
	 * @param fragmentid
	 * @param length
	 * @param payload
	 * @param offset
	 */
	public Fragment(Type messagetype, byte flags, int messageid,
			int fragmentid, int length, byte[] payload, int offset) {
		super();
		this.messagetype = messagetype;
		this.flags = flags;
		this.messageid = messageid;
		this.fragmentid = fragmentid;
		this.length = length;
		this.payload = payload;
		this.offset = offset;
	}

	/**
	 * @param messagetype
	 * @param flags
	 * @param messageid
	 * @param fragmentid
	 * @param length
	 */
	public Fragment(Type messagetype, byte flags, int messageid,
			int fragmentid, int length) {
		super();
		this.messagetype = messagetype;
		this.flags = flags;
		this.messageid = messageid;
		this.fragmentid = fragmentid;
		this.length = length;
	};
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Fragment [messagetype=" + messagetype + ", flags=" + flags
				+ ", messageid=" + messageid + ", fragmentid=" + fragmentid
				+ ", length=" + length +/* ", payload="
				+ Arrays.toString(payload) +*/ ", offset=" + offset
				+ ", priority=" + priority + "]";
	}

	static class PriorityComparator implements Comparator<Fragment> {
		public int compare(Fragment f1, Fragment f2) {
			return new Integer(f1.priority).compareTo(f2.priority);
		}		
	}
}
