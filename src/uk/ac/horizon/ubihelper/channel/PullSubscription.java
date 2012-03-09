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

import java.util.LinkedList;

import org.json.JSONObject;

/**
 * @author cmg
 *
 */
public class PullSubscription extends Subscription {
	private int count = 1;
	private LinkedList<JSONObject> values = new LinkedList<JSONObject>();

	public PullSubscription(String channelName, String subscriptionId, int count) {
		super(channelName, subscriptionId);
	}

	/**
	 * @param count the count to set
	 */
	public synchronized void setCount(int count) {
		this.count = count;
		checkCount();
	}

	private synchronized void checkCount() {
		while (values.size()>count)
			values.removeFirst();
	}

	/** implementation of add value */
	@Override
	protected synchronized void handleAddValue(JSONObject value) {
		values.add(value);
		checkCount();
	}
	
	/**
	 * @return the values
	 */
	public synchronized LinkedList<JSONObject> takeValues() {
		LinkedList<JSONObject> oldValues = values;
		values = new LinkedList<JSONObject>();
		return oldValues;
	}
}
