/**
 * Copyright (c) 2012 The University of Nottingham
 * 
 * This file is part of ubihelper
 *
 *  ubihelper is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ubihelper is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with ubihelper. If not, see <http://www.gnu.org/licenses/>.
 *  
 *  @author Chris Greenhalgh (cmg@cs.nott.ac.uk), The University of Nottingham
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
