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
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author cmg
 *
 */
public class ChannelManager implements ChannelListener {
	static Logger logger = Logger.getLogger(ChannelManager.class.getName());
	private LinkedList<NamedChannel> channels = new LinkedList<NamedChannel>();
	private LinkedList<Subscription> subscriptions = new LinkedList<Subscription>();
	private SharedVariableChannel channelsChannel;
	public static final String CHANNEL_CHANNELS = "channels";
	public static final String KEY_NAMES = "names";
	public static final String CHANNEL_PEERS = "peers";
	public static final String KEY_PEERS = "peers";
	private int nextSubscriptionId = 1;
	private ChannelFactory channelFactory;
	
	public ChannelManager(ChannelFactory channelFactory) {		
		this.channelFactory = channelFactory;
		channelsChannel = new SharedVariableChannel(CHANNEL_CHANNELS, getChannels());
		addChannel(channelsChannel);
	}
	public synchronized void close() {
		while(channels.size()>0) {
			NamedChannel nc = channels.remove();
			nc.stop();
			nc.close();
		}
	}
	private synchronized JSONObject getChannels() {
		JSONObject value = new JSONObject();
		JSONArray array = new JSONArray();
		for (NamedChannel nc : channels)
			array.put(nc.getName());
		try {
			value.put(KEY_NAMES, array);
		} catch (JSONException e) {
			// shouldn't
		}
		return value;
	}

	public synchronized void addChannel(NamedChannel channel) {
		channels.add(channel);
		channel.init(this);
		channelsChannel.setValue(getChannels());
	}
	
	public synchronized String getNextSubscriptionId() {
		return "SUB:"+(nextSubscriptionId++);
	}
	/** return subscription id */
	public synchronized void addSubscription(Subscription subscription) {
		subscriptions.add(subscription);
		String name = subscription.getChannelName();
		for (NamedChannel nc : channels) {
			if (nc.getName().equals(name)) {
				JSONObject value = nc.getImmediateValue();
				if (value!=null)
					subscription.addValue(value);
			}
		}
	}

	public synchronized void refreshChannel(String name) {
		boolean required = false;
		double period = 0;
		LinkedList<Subscription> ss = new LinkedList<Subscription>();
		for (Subscription s : subscriptions) {
			if (s.getChannelName().equals(name)) 
				ss.add(s);
		}
		for (Subscription s : ss) {
			if (s.isExpired()) {
				// remove
				logger.info("refreshChannel "+name+" expires subscription "+s.getId());
				removeSubscription(s);
			} else {
				required = true;
				if (period==0 || (s.getPeriod()>0 && s.getPeriod()<period))
					period = s.getPeriod();
			}
		}
		if (period==0) {
			period = 1; // default?!
		}
		if (required) {
			boolean found = false;
			for (NamedChannel nc : channels) {
				if (nc.getName().equals(name)) {
					found = true;
				}
			}
			if (!found && channelFactory!=null) {
				try {
					NamedChannel nc = channelFactory.createChannel(name);
					if (nc!=null) {
						addChannel(nc);
						found = true;
					}
				}
				catch (Exception e) {
					logger.warning("Error creating channel "+name+": "+e);
					e.printStackTrace();
				}
			}
		}
		for (NamedChannel nc : channels) {
			if (nc.getName().equals(name)) {
				if (required) {
					logger.info("refreshChannel "+name+" ensure start with period="+period);
					nc.start(period);
				}
				else {
					nc.stop();
					logger.info("refreshChannel "+name+" ensure stop");
				}
			}
		}
	}
	public synchronized void removeSubscription(Subscription subscription) {
		subscriptions.remove(subscription);
	}
	public synchronized Subscription findSubscription(String name, String id) {
		for (Subscription s : subscriptions)
			if (s.getChannelName().equals(name) && (s.getId()==id || (s.getId()!=null && s.getId().equals(id))))
				return s;
		return null;
	}
	public void onNewValue(ChannelValueEvent nve) {
		NamedChannel channel = nve.getChannel();
		String name = channel.getName();
		JSONObject value = nve.getValue();
		logger.info("onNewValue "+name+": "+value);
		boolean expired = false;
		synchronized (this) {
			for (Subscription s : subscriptions) {
				if (s.getChannelName().equals(name)) {
					if (!s.isExpired())
						s.addValue(value);
					else
						expired = true;
				}
			}
			if (subscriptions.size()==0)
				expired = true;
		}
		if (expired)
			refreshChannel(channel.getName());
	}
	
}
