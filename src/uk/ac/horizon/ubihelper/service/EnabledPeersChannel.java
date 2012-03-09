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

import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import uk.ac.horizon.ubihelper.channel.NamedChannel;
import uk.ac.horizon.ubihelper.protocol.PeerInfo;

/**
 * @author cmg
 *
 */
public class EnabledPeersChannel extends NamedChannel {
	private Service service;
	private PeerManager peerManager;
	/**
	 * @param name
	 */
	public EnabledPeersChannel(Service service, PeerManager peerManager, String name) {
		super(name);
		this.service = service;
		this.peerManager = peerManager;
	}
	private static JSONObject getEnabledPeers(PeerManager peerManager) {
		JSONObject value = new JSONObject();
		if (peerManager!=null) {
			List<PeerInfo> peers = peerManager.getEnabledPeers();
			try {
				JSONArray ps =new JSONArray();
				value.put("peers", ps);
				for (PeerInfo peer : peers) {
					JSONObject p = new JSONObject();
					p.put("id", peer.id);
					p.put("name", peer.name);
					p.put("trusted", peer.trusted);
					if (peer.wifimac!=null)
						p.put("wifimac", peer.wifimac);
					if (peer.btmac!=null)
						p.put("btmac", peer.btmac);
					if (peer.imei!=null)
						p.put("imei", peer.imei);
					p.put("enabled", peer.enabled);
					ps.put(p);
				}
			}
			catch (JSONException e) {
				// shouldn't
			}
		}
		return value;
	}
	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			refresh();
		}
	};
	@Override
	protected void handleStart() {
		super.handleStart();
		IntentFilter filter =new IntentFilter(PeerManager.ACTION_PEERS_CHANGED);
		filter.addAction(PeerManager.ACTION_PEER_STATE_CHANGED);
		service.registerReceiver(receiver, filter);
	}
	
	@Override
	public synchronized JSONObject getImmediateValue() {
		return getEnabledPeers(peerManager);
	}
	protected void refresh() {
		onNewValue(getEnabledPeers(peerManager));
	}
	@Override
	protected void handleStop() {
		service.unregisterReceiver(receiver);
	}

}
