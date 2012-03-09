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

package uk.ac.horizon.ubihelper.channel;

import org.json.JSONObject;

/**
 * @author cmg
 *
 */
public class ChannelValueEvent {
	private NamedChannel channel;
	private JSONObject value;
	
	public ChannelValueEvent() {}

	/**
	 * @param channel
	 * @param value
	 */
	public ChannelValueEvent(NamedChannel channel, JSONObject value) {
		super();
		this.channel = channel;
		this.value = value;
	}

	/**
	 * @return the channel
	 */
	public NamedChannel getChannel() {
		return channel;
	}

	/**
	 * @return the value
	 */
	public JSONObject getValue() {
		return value;
	}
	

}
