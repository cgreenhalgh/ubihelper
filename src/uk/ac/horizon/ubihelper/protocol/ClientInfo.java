package uk.ac.horizon.ubihelper.protocol;

import org.json.JSONObject;

import uk.ac.horizon.ubihelper.net.PeerConnection;

public class ClientInfo {
	public int notificationId;
	public ClientState state;
	// connect
	public PeerConnection pc;
	// peer request
	public String name;
	public String id;
	public int port;
	public String pindigest;
	public String pin;
	public String secret1;
	public String secret2;
	public JSONObject peerInfo;
	public String pinnonce;
	
	public ClientInfo(PeerConnection pc) {
		this.pc = pc;
		state = ClientState.STATE_NEGOTIATE_PROTOCOL;
	}
	
	public ClientInfo(ClientInfo ci) {
		state = ci.state;
		name = ci.name;
		id = ci.id;
		port = ci.port;
	}
}