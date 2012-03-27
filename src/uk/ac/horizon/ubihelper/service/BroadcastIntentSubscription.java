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

import java.util.logging.Logger;

import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;

import uk.ac.horizon.ubihelper.channel.Subscription;

/**
 * @author cmg
 *
 */
public class BroadcastIntentSubscription extends Subscription {
	static Logger logger = Logger.getLogger(BroadcastIntentSubscription.class.getName());
	public static final String ACTION_CHANNEL_NEW_VALUE = "uk.ac.horizon.ubihelper.actions.CHANNEL_NEW_VALUE";
	public static final String EXTRA_NAME = "uk.ac.horizon.ubihelper.extras.NAME";
	public static final String EXTRA_VALUE = "uk.ac.horizon.ubihelper.extras.VALUE";
	private static final double DEFAULT_PERIOD = 0.25;
	private static final double DEFAULT_MIN_INTERVAL = DEFAULT_PERIOD/2;
	private Context context;
	
	public BroadcastIntentSubscription(Context context, String channelName) {
		super(channelName);
		this.context = context;
		updateConfiguration(DEFAULT_PERIOD, DEFAULT_MIN_INTERVAL, 0);
	}

	@Override
	protected void handleAddValue(JSONObject value) {
		logger.info("handleAddValue for "+getChannelName()+" "+getId()+": "+value);
		Intent i = new Intent(ACTION_CHANNEL_NEW_VALUE);
		i.putExtra(EXTRA_NAME, getChannelName());
		i.putExtra(EXTRA_VALUE, value.toString());
		context.sendBroadcast(i);
	}
}
