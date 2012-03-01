/**
 * 
 */
package uk.ac.horizon.ubihelper.dns;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.logging.Logger;

/**
 * @author cmg
 *
 */
public class DnsUtils {
	static Logger logger = Logger.getLogger(DnsUtils.class.getName());
	public static final String UBIHELPER_SERVICE_NAME = "_ubihelper";
	public static final String TCP_PROTOCOL_NAME = "_tcp";
	public static final String LOCAL_DOMAIN_NAME = "local";
	//private static final String TAG = "ubihelper-dnsutils";
	
	public static String getServiceDiscoveryName() {
		return UBIHELPER_SERVICE_NAME+"."+TCP_PROTOCOL_NAME+"."+LOCAL_DOMAIN_NAME;
	}
	public static String ip2string(int ip) {
		// NB at least on my samsung google s the high-byte is in the low bits
		StringBuilder sb = new StringBuilder();
		sb.append(Integer.toString((ip) & 0xff));
		sb.append(".");
		sb.append(Integer.toString((ip >> 8) & 0xff));
		sb.append(".");
		sb.append(Integer.toString((ip >> 16) & 0xff));
		sb.append(".");
		sb.append(Integer.toString((ip >> 24) & 0xff));
		return sb.toString();
	}
	public static NetworkInterface getNetworkInterface(int ip) {
		try {
			InetAddress inet = InetAddress.getByName(ip2string(ip));
			NetworkInterface ni = NetworkInterface.getByInetAddress(inet);
			return ni;
		} catch (Exception e) {
			logger.warning("Error getting network interface "+ip2string(ip)+": "+e.getMessage());
		}
		return null;
	}
}
