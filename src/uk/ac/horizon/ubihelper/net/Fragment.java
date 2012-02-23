package uk.ac.horizon.ubihelper.net;

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
	
	static class PriorityComparator implements Comparator<Fragment> {
		public int compare(Fragment f1, Fragment f2) {
			return new Integer(f1.priority).compareTo(f2.priority);
		}		
	}
}
