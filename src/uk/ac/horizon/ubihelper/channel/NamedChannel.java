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

import java.util.logging.Logger;

import org.json.JSONObject;

/** Named "channel" for values.
 * 
 * @author cmg
 *
 */
public class NamedChannel {
	static Logger logger = Logger.getLogger(NamedChannel.class.getName());
	protected String name;
	protected double period = 1;
	protected boolean active = false;
	private ChannelListener listener;
	/** cons */
	public NamedChannel(String name) {
		this.name = name;
	}
	/** update metadata */
	public final synchronized void start(double period) {
		if (this.period!=period) {
			stop();
		}
		this.period = period;
		if (!this.active) { 
			this.active = true;
			handleStart();
		}
	}
	public final synchronized void stop() {
		if (!this.active)
			return;
		this.active = false;
		handleStop();
	}
	public synchronized void close() {}
	public synchronized JSONObject getImmediateValue() {
		return null;
	}
	protected void handleStart() {}
	protected void handleStop() {}
	/**
	 * @return the name
	 */
	public final String getName() {
		return name;
	}
	/** from ChannelManager */
	synchronized void init(ChannelListener listener) {
		this.listener = listener;
	}
	protected final void onNewValue(JSONObject value) {
		ChannelListener l =  null;
		synchronized (this) {
			l = listener;
		}
		if (l!=null) {
			try {
				l.onNewValue(new ChannelValueEvent(this, value));
			}
			catch (Exception e) {
				logger.warning("Error on onNewValue listener: "+e);
				e.printStackTrace();
			}
		}
	}
}
