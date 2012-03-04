/**
 * 
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

	public PullSubscription(String channelName, int count) {
		super(channelName);
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
	@SuppressWarnings("unchecked")
	public synchronized LinkedList<JSONObject> takeValues() {
		LinkedList<JSONObject> oldValues = values;
		values = new LinkedList<JSONObject>();
		return oldValues;
	}
}
