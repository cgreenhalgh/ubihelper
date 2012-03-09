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
package uk.ac.horizon.ubihelper.protocol;

import org.json.JSONObject;

import uk.ac.horizon.ubihelper.net.PeerConnection;

public class ClientInfo {
	public int notificationId;
	public ClientState state;
	// connect
	public PeerConnection pc;
	// peer request
	public String name;
	public String id;
	public int port;
	public String pindigest;
	public String pin;
	public String secret1;
	public String secret2;
	public JSONObject peerInfo;
	public String pinnonce;
	
	public ClientInfo(PeerConnection pc) {
		this.pc = pc;
		state = ClientState.STATE_NEGOTIATE_PROTOCOL;
	}
	
	public ClientInfo(ClientInfo ci) {
		state = ci.state;
		name = ci.name;
		id = ci.id;
		port = ci.port;
	}
}