/**
 * 
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
	private int nextSubscriptionId = 1;
	
	public ChannelManager() {		
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
			if (s.getChannelName().equals(name) && s.getId().equals(id))
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
