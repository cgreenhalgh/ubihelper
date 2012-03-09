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
package uk.ac.horizon.ubihelper.service;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;

import uk.ac.horizon.ubihelper.dns.DnsProtocol;
import uk.ac.horizon.ubihelper.dns.DnsServer;
import uk.ac.horizon.ubihelper.dns.DnsUtils;
import uk.ac.horizon.ubihelper.dns.DnsProtocol.SrvData;
import uk.ac.horizon.ubihelper.ui.MainPreferences;
import uk.ac.horizon.ubihelper.ui.WifiStatusActivity;
import android.bluetooth.BluetoothAdapter;
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
	// default device name
	private String deviceName;
	private DnsProtocol.RR ptrRR;
//	private BluetoothAdapter bluetooth;
	private BroadcastReceiver deviceNameReceiver = new DeviceNameReceiver();
	private int serverPort;
	
	public WifiDiscoveryManager(Service service) {
		this.service = service;
		wifi = (WifiManager)service.getSystemService(Service.WIFI_SERVICE);
		// NB on my Nexus S, if you don't get the FULL_HIGH_PERF mode
		// then multicast reception stops when the screen goes off, even
		// if you have the multicast lock.
		int wifiMode = WifiManager.WIFI_MODE_FULL;
		try {
			Field f = WifiManager.class.getField("WIFI_MODE_FULL_HIGH_PERF");
			wifiMode = f.getInt(null);
			Log.d(TAG,"Found WIFI_MODE_FULL_HIGH_PERF");
		} catch (Exception e) {
			Log.d(TAG,"Did not find WIFI_MODE_FULL_HIGH_PERF");
		}
		wifiLock = wifi.createWifiLock(wifiMode, "ubihelper-wifidisc");
		multicastLock = wifi.createMulticastLock("ubihelper-wifidisc");

		// leave name (and any bluetooth stuff) to Preferences
//		bluetooth = BluetoothAdapter.getDefaultAdapter();
		deviceName = service.getDeviceName();
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
		service.unregisterReceiver(deviceNameReceiver);
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
		
		IntentFilter nameFilter = new IntentFilter(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
		nameFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		nameFilter.addAction(MainPreferences.ACTION_NAME_CHANGED);
		service.registerReceiver(deviceNameReceiver, nameFilter);

		//multicastLock.acquire();
		//Log.d(TAG, "Multicast lock held: "+multicastLock.isHeld());
		//dnsServer = new DnsServer();
		//dnsServer.start();
		updateDnsServer();
	}
	
	private class DeviceNameReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			synchronized (this) {
				String oldName = deviceName;
				deviceName = service.getDeviceName();
				if (!oldName.equals(deviceName))
					updateDnsServerNameRecords();
			}
		}
	}

	public synchronized void updateDnsServer() {
		if (dnsServer!=null) {
			dnsServer.close();
			dnsServer = null;
		}

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
			wifiLock.acquire();
			// apparently it is important to (re)acquire this lock after any
			// change of network
			multicastLock.acquire();
			Log.d(TAG, "Multicast lock held: "+multicastLock.isHeld());

			dnsServer = new DnsServer();
			
			byte addr[] = new byte[4];
			addr[0] = (byte)(ip & 0xff);
			addr[1]= (byte)((ip >> 8) & 0xff);
			addr[2]= (byte)((ip >> 16) & 0xff);
			addr[3]= (byte)((ip >> 24) & 0xff);
			
			NetworkInterface ni = DnsUtils.getNetworkInterface(ip);
			if (ni!=null)
				dnsServer.setNeworkInterface(ni);
			dnsServer.start();
			
			String name = getDomainNameForMac(mac);
			Log.d(TAG,"Discoverable "+WifiStatusActivity.ip2string(ip)+" as "+name);
			dnsServer.add(new DnsProtocol.RR(name, DnsProtocol.TYPE_A, 
					DnsProtocol.CLASS_IN, DEFAULT_TTL, addr));

			// TODO port
			String servicename = DnsUtils.getServiceDiscoveryName();
			SrvData srv = new SrvData(1, 1, serverPort, name);
			Log.d(TAG,"Discoverable "+name+" as "+servicename);
			dnsServer.add(new DnsProtocol.RR(servicename, DnsProtocol.TYPE_SRV, 
					DnsProtocol.CLASS_IN, DEFAULT_TTL, DnsProtocol.srvToData(srv)));

			updateDnsServerNameRecords();

			// TODO other discoverable namespaces, e.g. Bluetooth MAC, IMEI, nick-name
		}
		else {
			Log.d(TAG,"No IP - not discoverable");
			//dnsServer.clear();
		}
	}
	
	public synchronized void updateDnsServerNameRecords() {
		if (dnsServer==null)
			return;
		if (ptrRR!=null) {
			dnsServer.remove(ptrRR);
			ptrRR = null;
		}
		String servicename = DnsUtils.getServiceDiscoveryName();
		String instancename = deviceName;
		Log.d(TAG,"Discoverable as "+instancename+" "+servicename);
		ptrRR = new DnsProtocol.RR(servicename, DnsProtocol.TYPE_PTR, 
				DnsProtocol.CLASS_IN, DEFAULT_TTL, DnsProtocol.ptrToData(instancename, servicename));
		dnsServer.add(ptrRR);
	}

	public static String getDomainNameForMac(String mac) {
		return mac.replace(":", "")+".wifimac._ubihelper.local";
	}


	public synchronized void close() {
		closed = true;
		setEnabled(false);
	}

	public synchronized void setServerPort(int serverPort) {
		this.serverPort = serverPort;
	}
}
