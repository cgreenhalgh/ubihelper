package uk.ac.horizon.ubihelper.protocol;

/** state of a ClientInfo, i.e. incoming peer connection */
public enum ClientState {
	STATE_NEGOTIATE_PROTOCOL, // waiting for HELLO
	STATE_NEGOTIATED_PROTOCOL, // Had HELLO & responded; wait for next
	STATE_PEER_NOPIN,
	STATE_PEER_PIN,
	STATE_PEER_DONE, 
	STATE_WAITING_FOR_PIN,
	STATE_PEERED
}