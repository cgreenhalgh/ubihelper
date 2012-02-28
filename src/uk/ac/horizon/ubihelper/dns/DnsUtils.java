/**
 * 
 */
package uk.ac.horizon.ubihelper.dns;

import java.net.InetAddress;
import java.net.NetworkInterface;

import uk.ac.horizon.ubihelper.ui.WifiStatusActivity;

import android.util.Log;

/**
 * @author cmg
 *
 */
public class DnsUtils {
	public static final String UBIHELPER_SERVICE_NAME = "_ubihelper";
	public static final String TCP_PROTOCOL_NAME = "_tcp";
	public static final String LOCAL_DOMAIN_NAME = "local";
	private static final String TAG = "ubihelper-dnsutils";
	
	public static String getServiceDiscoveryName() {
		return UBIHELPER_SERVICE_NAME+"."+TCP_PROTOCOL_NAME+"."+LOCAL_DOMAIN_NAME;
	}
	public static NetworkInterface getNetworkInterface(int ip) {
		try {
			InetAddress inet = InetAddress.getByName(WifiStatusActivity.ip2string(ip));
			NetworkInterface ni = NetworkInterface.getByInetAddress(inet);
			return ni;
		} catch (Exception e) {
			Log.w(TAG, "Error getting network interface "+WifiStatusActivity.ip2string(ip)+": "+e.getMessage());
		}
		return null;
	}
}
