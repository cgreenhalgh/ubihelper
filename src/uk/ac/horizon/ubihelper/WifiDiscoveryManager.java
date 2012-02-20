/**
 * 
 */
package uk.ac.horizon.ubihelper;

import uk.ac.horizon.ubihelper.DnsProtocol.SrvData;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

/** Managed by service; manages DnsServer.
 * 
 * @author cmg
 *
 */
public class WifiDiscoveryManager {
	static final String TAG = "ubihelper-wifi";
	static final String UBIHELPER_SERVICE_NAME = "_ubihelper";
	static final String TCP_SERVICE_NAME = "_tcp";
	private static final int DEFAULT_TTL = 60;
	private Service service;
	private boolean enabled = false;
	private boolean closed = false;
	private BroadcastReceiver updateReceiver = new UpdateReceiver();
	private DnsServer dnsServer = null;
	private WifiManager wifi;
	private WifiManager.WifiLock wifiLock = null;
	private WifiManager.MulticastLock multicastLock = null;
	
	public WifiDiscoveryManager(Service service) {
		this.service = service;
		wifi = (WifiManager)service.getSystemService(Service.WIFI_SERVICE);
		wifiLock = wifi.createWifiLock("ubihelper-wifidisc");
		multicastLock = wifi.createMulticastLock("ubihelper-wifidisc");
	}
	
	public synchronized void setEnabled(boolean enabled) {
		boolean wasEnabled = this.enabled;
		this.enabled = enabled;
		if (!wasEnabled && enabled)
			enableInternal();
		else if (wasEnabled && !enabled)
			disableInternal();
	}
	
	/** broadcast receiver */
	private class UpdateReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG,"Received broadcast "+intent.getAction());
			updateDnsServer();
		}		
	}
	
	private synchronized void disableInternal() {
		service.unregisterReceiver(updateReceiver);
		if (dnsServer!=null) {
			dnsServer.close();
			dnsServer = null;
		}
		if (wifiLock!=null)
			wifiLock.release();
		if (multicastLock!=null)
			multicastLock.release();
	}

	private synchronized void enableInternal() {
		IntentFilter filter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		service.registerReceiver(updateReceiver, filter);
		wifiLock.acquire();
		multicastLock.acquire();
		Log.d(TAG, "Multicast lock held: "+multicastLock.isHeld());
		dnsServer = new DnsServer();
		dnsServer.start();
	}

	public synchronized void updateDnsServer() {
		if (dnsServer==null)
			return;

		wifiLock.acquire();
		multicastLock.acquire();
		Log.d(TAG, "Multicast lock held: "+multicastLock.isHeld());

		//int state = wifi.getWifiState();
		WifiInfo ci = wifi.getConnectionInfo();
		// this seems to be an empty string if disabled (inc. on emulator)
		//String ssid = ci!=null ? ci.getSSID() : "-";
		int ip = ci!=null ? ci.getIpAddress() : 0;
		// this seems to be avabile in usual xx:xx:xx:xx:xx:xx format on real device
		// even with wifi disabled, but not on emulator
		String mac = ci!=null ? ci.getMacAddress() : "-";
		// this information is cached and returned on real device even 
		// when no longer valid, e.g. if wifi now disabled.
		//DhcpInfo di = wifi.getDhcpInfo();
		//int ip2 = di!=null ? di.ipAddress : 0;
		//int netmask = di!=null ? di.netmask : 0;
		//int gateway = di!=null ? di.gateway : 0;

		if (ip!=0) {
			byte addr[] = new byte[4];
			addr[0] = (byte)(ip & 0xff);
			addr[1]= (byte)((ip >> 8) & 0xff);
			addr[2]= (byte)((ip >> 16) & 0xff);
			addr[3]= (byte)((ip >> 24) & 0xff);
			
			String name = getDomainNameForMac(mac);
			Log.d(TAG,"Discoverable "+WifiStatusActivity.ip2string(ip)+" as "+name);
			dnsServer.add(new DnsProtocol.RR(name, DnsProtocol.TYPE_A, 
					DnsProtocol.CLASS_IN, DEFAULT_TTL, addr));

			// TODO port
			String servicename = UBIHELPER_SERVICE_NAME+"."+TCP_SERVICE_NAME+".local";
			SrvData srv = new SrvData(1, 1, 8180, name);
			Log.d(TAG,"Discoverable "+name+" as "+servicename);
			dnsServer.add(new DnsProtocol.RR(servicename, DnsProtocol.TYPE_SRV, 
					DnsProtocol.CLASS_IN, DEFAULT_TTL, DnsProtocol.srvToData(srv)));

			// TODO name
			String instancename = "Ubihelper";
			Log.d(TAG,"Discoverable "+name+" as "+instancename+" "+servicename);
			dnsServer.add(new DnsProtocol.RR(servicename, DnsProtocol.TYPE_PTR, 
					DnsProtocol.CLASS_IN, DEFAULT_TTL, DnsProtocol.ptrToData(instancename, servicename)));

			// TODO other discoverable namespaces, e.g. Bluetooth MAC, IMEI, nick-name
		}
		else {
			Log.d(TAG,"No IP - not discoverable");
			dnsServer.clear();
		}
	}
	
	public static String getDomainNameForMac(String mac) {
		return mac.replace(":", "")+".wifimac._ubihelper.local";
	}


	public synchronized void close() {
		closed = true;
		setEnabled(false);
	}
}
