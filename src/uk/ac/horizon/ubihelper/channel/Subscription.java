/**
 * 
 */
package uk.ac.horizon.ubihelper.channel;

import java.util.logging.Logger;

import org.json.JSONObject;

/** Subscription to a channel.
 * 
 * @author cmg
 *
 */
public abstract class Subscription {
	static Logger logger = Logger.getLogger(Subscription.class.getName());
	private String channelName;
	private String id;
	private double period;
	private long minInterval;
	private long expires = 0;
	private long lastValueTime = 0;
	/**
	 * @param channel
	 * @param id
	 * @param period
	 * @param minInterval
	 */
	public Subscription(String channelName) {
		super();
		this.channelName = channelName;
	}

	/** update metadata */
	public synchronized void updateConfiguration(double period, double minInterval, double timeout) {
		this.period = period;
		this.minInterval = (long)(1000*minInterval);
		if (timeout==0) 
			expires = Long.MAX_VALUE;
		else
			expires = System.currentTimeMillis()+(long)(1000*timeout);
	}

	/** expired? */
	synchronized boolean isExpired() {
		return System.currentTimeMillis()>expires;
	}

	/** new value */
	synchronized void addValue(JSONObject value) {
		long now = System.currentTimeMillis();
		long elapsed = now-lastValueTime;
		if (elapsed<minInterval) {
			logger.warning("Discarded value, elapsed="+elapsed+" vs "+minInterval+"ms");
			return;
		}
		lastValueTime = now;
		handleAddValue(value);
	}
	
	protected abstract void handleAddValue(JSONObject value);

	/**
	 * @return the channel
	 */
	public String getChannelName() {
		return channelName;
	}
	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}
	/** from ChannelManager.addSubscription */
	void init(String id) {
		this.id = id;
	}
	/**
	 * @return the period
	 */
	public double getPeriod() {
		return period;
	}

	/**
	 * @return the expires
	 */
	public long getExpires() {
		return expires;
	}
	
}
