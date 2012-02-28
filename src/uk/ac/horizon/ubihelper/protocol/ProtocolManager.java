/**
 * 
 */
package uk.ac.horizon.ubihelper.protocol;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Random;
import java.util.logging.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import uk.ac.horizon.ubihelper.net.Message;
import uk.ac.horizon.ubihelper.net.OnPeerConnectionListener;
import uk.ac.horizon.ubihelper.net.PeerConnection;
import android.util.Base64;

/** Home for peer protocol logic/state machine.
 * 
 * @author cmg
 *
 */
public class ProtocolManager {
	static Logger logger = Logger.getLogger(ProtocolManager.class.getName());
	
	private SecureRandom srandom;
	private Random random;
	private MessageDigest messageDigest;

	public ProtocolManager() {
		random = new Random(System.currentTimeMillis());
		try {
			srandom = SecureRandom.getInstance("SHA1PRNG");
		}
		catch (Exception e) {
			logger.warning("Could not get SecureRandom: "+e);
		}
		try {
			messageDigest = MessageDigest.getInstance("MD5");
		}
		catch (Exception e) {
			logger.warning("Could not get MessageDigest: "+e);
		}

	}
	
	public void getRandom(byte buf[]) {
		if (srandom!=null)
			srandom.nextBytes(buf);
		else
			random.nextBytes(buf);
	}

	public static class ClientConnectionListener implements OnPeerConnectionListener {
		private ProtocolManager pm;
		public ClientConnectionListener(ProtocolManager pm) {
			this.pm = pm;
		}
		public void onRecvMessage(PeerConnection pc) {
			if (pc.attachment() instanceof ClientInfo) {
				ClientInfo ci = (ClientInfo)pc.attachment();
				logger.info("onMessage ClientInfo "+ci);
				pm.clientCheckMessages(ci);
			}
		}
		
		public void onFail(PeerConnection pc, boolean sendFailed,
				boolean recvFailed, boolean connectFailed) {
			if (pc.attachment() instanceof ClientInfo) {
				ClientInfo ci = (ClientInfo)pc.attachment();
				logger.info("onFail ClientInfo "+ci);
				// TODO
			}
		}

		public void onConnected(PeerConnection pc) {
		}
	}

	/** called on indication of avilable messages at client */ 
	protected void clientCheckMessages(ClientInfo ci) {
		Message m = null;
		Object msg;
		try {
			while ((m=ci.pc.getMessage())!=null) {
				switch (ci.state) {
				case STATE_NEGOTIATE_PROTOCOL: {
					boolean ok = MessageUtils.checkNegotiateProtocolResponse(m);
					if (!ok) {
						removeClient(ci);
						return;
					}
					ci.pc.sendMessage(MessageUtils.getHelloMessage());
					ci.state = ClientState.STATE_NEGOTIATED_PROTOCOL;
					break;
				}
				case STATE_NEGOTIATED_PROTOCOL: {
					msg = MessageUtils.parseManagementMessage(m);
					if (msg instanceof MessageUtils.InitPeerReq) {
						MessageUtils.InitPeerReq req = (MessageUtils.InitPeerReq)msg;
						if (!clientHandlePeerReq(ci, req))
							return;
					}
					else 
						throw new IOException("Received first management message of type "+msg.getClass().getName());
					break;
				}
				case STATE_PEER_PIN: {
					msg = MessageUtils.parseManagementMessage(m);
					if (msg instanceof MessageUtils.InitPeerDone) {
						MessageUtils.InitPeerDone req = (MessageUtils.InitPeerDone)msg;
						if (!clientHandlePeerPinResponse(ci, req))
							return;
					}
					else 
						throw new IOException("Received peer_pin response of type "+msg.getClass().getName());
					break;
				}
				}
			}
		}
		catch (Exception e) {
			logger.warning("Error handling client message: "+e);
			e.printStackTrace();
			removeClient(ci);
		}
	}

	public void removeClient(ClientInfo ci) {
		try {
			ci.pc.close();
		}
		catch (Exception e) {
			/* ignore */
		}
	}
	/** called from handleFirstMessage on receipt of init_peer_req message by client */
	protected boolean clientHandlePeerReq(ClientInfo ci, MessageUtils.InitPeerReq req) {
		ci.id = req.id;
		ci.name = req.name;
		ci.port = req.port;
		ci.pindigest = req.pindigest;
		ci.state = ClientState.STATE_WAITING_FOR_PIN;
		// NEEDS MORE - override!
		return true;
	}
	/** called on receipt of message after resp_peer_pin by client */
	protected boolean clientHandlePeerPinResponse(ClientInfo ci, MessageUtils.InitPeerDone rec) {
		ci.pinnonce = rec.pinnonce;
		ci.peerInfo = rec.info;
		ci.secret1 = rec.secret;
		logger.warning("Received init_peer_done in state peer_pin with info="+ci.peerInfo);
		// check pin
		try {
			byte nbuf[] = Base64.decode(ci.pinnonce, Base64.DEFAULT);
			messageDigest.reset();
			messageDigest.update(nbuf);
			messageDigest.update(ci.pin.getBytes("UTF-8"));
			byte dbuf[] = messageDigest.digest();
			String pindigest = Base64.encodeToString(dbuf, Base64.DEFAULT);
			if (!ci.pindigest.equals(pindigest)) {
				logger.warning("Reject peer with incorrect pindigest");
				removeClient(ci);
				return false;
			}
		} catch (UnsupportedEncodingException e) {
			// shouldn't happen!
			logger.warning("Unsupported encoding (shoulnd't be): "+e);
		}
		// send response
		byte sbuf[] = new byte[8];
		getRandom(sbuf);
		ci.secret2 = Base64.encodeToString(sbuf, Base64.DEFAULT);
		Message r = MessageUtils.getRespPeerDone(getInfo(), ci.secret2);
		ci.pc.sendMessage(r);

		ci.state = ClientState.STATE_PEERED;
		return true;
	}

	protected JSONObject getInfo() {
		return new JSONObject();
	}
}
