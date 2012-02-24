package uk.ac.horizon.ubihelper.net;

public interface OnPeerConnectionListener {
	void onRecvMessage(PeerConnection pc);
	void onFail(PeerConnection pc, boolean sendFailed, boolean recvFailed, boolean connectFailed);
}
