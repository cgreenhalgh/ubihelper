/**
 * 
 */
package uk.ac.horizon.ubihelper.protocol;

import java.net.InetAddress;

import org.json.JSONObject;

import uk.ac.horizon.ubihelper.net.PeerConnection;

/** Information about a Peer (established relationship, not ClientInfo or PeerRequestInfo).
 * See also PeersOpenHelper for database schema.
 * 
 * @author cmg
 *
 */
public class PeerInfo {
	/** row id (DB internal) */
	public long _id = -1;
	/** device ID */
	public String id;
	/** subjective name (optional?) */
	public String nickname;
	/** device own instance name */
	public String name;
	/** device constant info, e.g. IMEI, Mac addresses */
	public JSONObject info;
	/** BT MAC address */
	public String btmac;
	/** Wifi Mac Address */
	public String wifimac;
	/** IMEI */
	public String imei;
	/** shared secret (at least if trusted) */
	public String secret;
	/** trusted? */
	public boolean trusted;
	/** enabled? */
	public boolean enabled;
	/** created (java)time */
	public long createdTimestamp;
	/** manual entry (vs discovery) */
	public boolean manual;
	/** IP address */
	public String ip;
	/** time IP address (last) established */
	public long ipTimestamp;
	/** server port */
	public int port;
	/** time port (last) established */
	public long portTimestamp;

	// connect
	public PeerConnection pc;

	// debug
	public String detail;

	public PeerInfo() {}

	@Override
	public String toString() {
		return "PeerInfo [_id=" + _id + ", id=" + id + ", nickname=" + nickname
				+ ", name=" + name + ", info=" + info + ", btmac=" + btmac
				+ ", wifimac=" + wifimac + ", imei=" + imei + ", secret="
				+ secret + ", trusted=" + trusted + ", enabled=" + enabled
				+ ", createdTimestamp=" + createdTimestamp + ", manual="
				+ manual + ", ip=" + ip + ", ipTimestamp=" + ipTimestamp
				+ ", port=" + port + ", portTimestamp=" + portTimestamp
				+ ", pc=" + pc + ", detail=" + detail + "]";
	}

}
