package uk.ac.horizon.ubihelper.net;

import java.util.Comparator;
import java.util.List;

/** 
 * A message as sent over a PeerConnection.
 * 
 * @author cmg
 *
 */
public class Message {
	public enum Type {  
		UNDEFINED(0),
		HELLO(1),
		MANAGEMENT(2),
		REQUEST(3),
		RESPONSE(4);
		
		private byte code;
		Type(int code) { this.code = (byte)code; }
		public byte getCode() { return code; }
	}
	
	/** type - for all messages */
	public Type type;
	/** request/status line - for REQUEST/RESPONSE only */
	public String firstLine;
	/** headers - for REQUEST/RESPONSE only (and optional there, as well) */
	public List<String> headerLines;
	/** body of message - whole for HELLO, MANAGEMENT */
	public String body;
	/** priority (send-only) - as far as possible messages and fragments will be offered to the network in priority order.
	 * 0 (default) is low. 
	 */
	public int priority;
	
	public Message() {}

	/**
	 * @param type
	 * @param firstLine
	 * @param headerLines
	 * @param body
	 */
	public Message(Type type, String firstLine, List<String> headerLines,
			String body) {
		super();
		this.type = type;
		this.firstLine = firstLine;
		this.headerLines = headerLines;
		this.body = body;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Message [type=" + type + ", firstLine=" + firstLine
				+ ", headerLines=" + headerLines + ", body=" + body + "]";
	}
	
	static class PriorityComparator implements Comparator<Message> {
		public int compare(Message m1, Message m2) {
			return new Integer(m1.priority).compareTo(m2.priority);
		}		
	}
}
