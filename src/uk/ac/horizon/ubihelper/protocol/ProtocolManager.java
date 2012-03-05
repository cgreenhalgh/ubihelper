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

/** Home for peer protocol logic/state machine.
 * 
 * @author cmg
 *
 */
public abstract class ProtocolManager {
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
			} else if (pc.attachment() instanceof PeerInfo) {
				PeerInfo pi = (PeerInfo)pc.attachment();
				logger.info("onMessage PeerInfo "+pi.id);
				pm.checkPeerMessages(pi);
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
	private void clientCheckMessages(ClientInfo ci) {
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

	public void checkPeerMessages(PeerInfo pi) {
		// TODO Auto-generated method stub
		while (true) {
			Message m = pi.pc.getMessage();
			if (m==null)
				return;
			logger.info("Received message "+m);
			// TODO
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
	private boolean clientHandlePeerReq(ClientInfo ci, MessageUtils.InitPeerReq req) {
		ci.id = req.id;
		ci.name = req.name;
		ci.port = req.port;
		ci.pindigest = req.pindigest;
		boolean prompted = clientPromptForPin(ci);
		if (prompted) {
			ci.state = ClientState.STATE_WAITING_FOR_PIN;
			return true;
		}
		else {
			// can't do pin!
			// send response
			ci.secret2 = getSecret();
			Message r = MessageUtils.getRespPeerNopin(getId(), getPort(), getName(), getInfo(), ci.secret2);
			ci.pc.sendMessage(r);

			ci.state = ClientState.STATE_PEERED;

			//  sub-class handle peered
			return clientHandlePeered(ci);
		}
	}

	/** prompt for PIN, e.g. from user. return false (default) if cannot prompt. */
	protected boolean clientPromptForPin(ClientInfo ci) {
		return false;
	}
	protected abstract byte [] base64Decode(String str);
	protected abstract String base64Encode(byte [] bs);

	/** called on receipt of message after resp_peer_pin by client */
	private boolean clientHandlePeerPinResponse(ClientInfo ci, MessageUtils.InitPeerDone rec) {
		ci.pinnonce = rec.pinnonce;
		ci.peerInfo = rec.info;
		ci.secret1 = rec.secret;
		logger.warning("Received init_peer_done in state peer_pin with info="+ci.peerInfo);
		// check pin
		try {
			byte nbuf[] = base64Decode(ci.pinnonce);
			messageDigest.reset();
			messageDigest.update(nbuf);
			messageDigest.update(ci.pin.getBytes("UTF-8"));
			byte dbuf[] = messageDigest.digest();
			String pindigest = base64Encode(dbuf);
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
		ci.secret2 = getSecret();
		Message r = MessageUtils.getRespPeerDone(getInfo(), ci.secret2);
		ci.pc.sendMessage(r);

		ci.state = ClientState.STATE_PEERED;

		//  sub-class handle peered
		return clientHandlePeered(ci);
	}
	private String getSecret() {
		byte sbuf[] = new byte[8];
		getRandom(sbuf);
		return base64Encode(sbuf);
	}
	public String combineSecrets(String secret1, String secret2) {
		int len = 0;
		if (secret1==null) {
			if (secret2==null)
				return null;
			logger.warning("combineSecrets with secret1==null");
			return secret2;
		}
		if (secret2==null) {
			logger.warning("combineSecrets with secret2==null");
			return secret1;			
		}
		byte bs1[] = base64Decode(secret1);
		byte bs2[] = base64Decode(secret2);
		len = bs1.length;
		if (bs2.length > bs1.length) {
			byte bt[] = bs1;
			bs1 = bs2;
			bs2 = bt;
		}
		// shorter in bs2; XOR for now!
		for (int i=0; i<bs2.length; i++) 
			bs1[i] = (byte)(bs1[i] ^ bs2[i]);

		return base64Encode(bs1);
	}

	
	/** called when successfully peered */
	protected abstract boolean clientHandlePeered(ClientInfo ci);

	protected abstract JSONObject getInfo();
	protected abstract String getName();
	protected abstract int getPort();
	protected abstract String getId();
}
