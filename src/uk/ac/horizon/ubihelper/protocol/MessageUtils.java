/**
 * 
 */
package uk.ac.horizon.ubihelper.protocol;

import java.io.IOException;
import java.util.logging.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Base64;
import android.util.Log;
import uk.ac.horizon.ubihelper.net.Message;
import uk.ac.horizon.ubihelper.net.Message.Type;
import uk.ac.horizon.ubihelper.service.PeerManager;

/** Peer protocl message utilities, e.g. build and parse.
 *  
 * @author cmg
 *
 */
public class MessageUtils {
	static Logger logger = Logger.getLogger(MessageUtils.class.getName());
	
	/** management message types */
	public static final String MSG_INIT_PEER_REQ = "init_peer_req";
	private static final String MSG_RESP_PEER_REQ = "resp_peer_req";
	public static final String MSG_INIT_PEER_DONE = "init_peer_done";
	private static final String MSG_RESP_PEER_NOPIN = "resp_peer_nopin";
	public static final String MSG_RESP_PEER_PIN = "resp_peer_pin";
	public static final String MSG_RESP_PEER_DONE = "resp_peer_done";
	/** management message keys */
	public static final String KEY_TYPE = "type";
	public static final String KEY_ID = "id";
	public static final String KEY_NAME = "name";
	public static final String KEY_PORT = "port";
	public static final String KEY_PINDIGEST = "pindigest";
	public static final String KEY_PIN = "pin";
	public static final String KEY_PINNONCE = "pinnonce";
	public static final String KEY_SECRET = "secret";
	public static final String KEY_INFO = "info";

	public static Message getHelloMessage() {
		return new Message(Message.Type.HELLO, null, null, Message.getHelloBody());
	}

	public static boolean checkNegotiateProtocolResponse(Message m) {
		// should be hello
		if (m.type!=Message.Type.HELLO) {
			logger.warning("received "+m.type+" message when negotiating protocol");
			return false;
		}
		String protocol = Message.getHelloBody();
		// TODO versions
		if (!protocol.equals(m.body)) {
			logger.warning("received incompatible protocol: "+m.body);
			return false;
		}
		// OK
		return true;
	}

	public static class InitPeerReq {
		public String id;
		public String name;
		public int port;
		public String pindigest;
	}
	
	public static class InitPeerDone {
		public String pinnonce;
		public JSONObject info;
		public String secret;
	}
	
	public static Object parseManagementMessage(Message m) throws IOException, JSONException {
		if (m.type!=Message.Type.MANAGEMENT)
			throw new IOException("Received "+m.type+" message (expected MANAGEMENT)");
		JSONObject msg = new JSONObject(m.body);
		String type = msg.getString(MessageUtils.KEY_TYPE);
		if (MessageUtils.MSG_INIT_PEER_REQ.equals(type)) {
			InitPeerReq rec = new InitPeerReq();
			rec.id = msg.getString(MessageUtils.KEY_ID);
			rec.name =msg.getString(MessageUtils.KEY_NAME);
			rec.port = msg.getInt(MessageUtils.KEY_PORT);
			rec.pindigest = msg.getString(MessageUtils.KEY_PINDIGEST);
			return rec;
		}
		else if (MessageUtils.MSG_INIT_PEER_DONE.equals(type)) {
			InitPeerDone rec = new InitPeerDone();
			rec.pinnonce = msg.getString(MessageUtils.KEY_PINNONCE);
			rec.info = msg.getJSONObject(MessageUtils.KEY_INFO);
			rec.secret = msg.getString(MessageUtils.KEY_SECRET);
			return rec;
		}
		// TODO
		throw new IOException("Unknown message type "+type);
	}
	
	public static Message getRespPeerDone(JSONObject info, String secret) {
		// send response
		try {
			JSONObject resp = new JSONObject();
			resp.put(MessageUtils.KEY_TYPE, MessageUtils.MSG_RESP_PEER_DONE);
			if (info!=null)
				resp.put(MessageUtils.KEY_INFO, info);
			resp.put(MessageUtils.KEY_SECRET, secret);
			
			return new Message(Message.Type.MANAGEMENT, null, null, resp.toString());
		}
		catch (JSONException e) {
			// shouldn't happen!
			logger.warning("JSON error (shoulnd't be): "+e);			
			return null;
		}
	}
}
