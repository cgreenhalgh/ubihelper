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
	public static final String HELLO_BODY_PREFIX = "application/x-ubihelper;version=";
	public static final int VERSION = 1;
	
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
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((body == null) ? 0 : body.hashCode());
		result = prime * result
				+ ((firstLine == null) ? 0 : firstLine.hashCode());
		result = prime * result
				+ ((headerLines == null) ? 0 : headerLines.hashCode());
		result = prime * result + priority;
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Message other = (Message) obj;
		if (body == null) {
			if (other.body != null)
				return false;
		} else if (!body.equals(other.body))
			return false;
		if (firstLine == null) {
			if (other.firstLine != null)
				return false;
		} else if (!firstLine.equals(other.firstLine))
			return false;
		if (headerLines == null) {
			if (other.headerLines != null)
				return false;
		} else if (!headerLines.equals(other.headerLines))
			return false;
		if (priority != other.priority)
			return false;
		if (type != other.type)
			return false;
		return true;
	}

	static class PriorityComparator implements Comparator<Message> {
		public int compare(Message m1, Message m2) {
			return new Integer(m1.priority).compareTo(m2.priority);
		}		
	}

	public static String getHelloBody() {
		return HELLO_BODY_PREFIX+VERSION;
	}
}
