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
package uk.ac.horizon.ubihelper.service.channel;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Handler;

/**
 * @author cmg
 *
 */
public class TimeChannel extends PollingChannel {

	private static final String KEY_TIME = "time";

	/**
	 * @param handler
	 * @param name
	 */
	public TimeChannel(Handler handler, String name) {
		super(handler, name);
	}

	@Override
	protected boolean startPoll() {
		try {
			long now = System.currentTimeMillis();
			JSONObject value = new JSONObject();
			value.put(KEY_TIME, now);
			this.onNewValue(value);
		}
		catch (JSONException e) {
			// shouldn't
		}
		// no wait, not running
		return false;
	}

}
