/**
 * 
 */
package uk.ac.horizon.ubihelper;

import java.util.LinkedList;

import org.json.JSONObject;

/** Named "channel" for values.
 * 
 * @author cmg
 *
 */
public class NamedChannel {
	protected String name;
	private int count = 1;
	protected double period = 1;
	private double timeout = 1;
	private long expires = 0;
	private LinkedList<JSONObject> values = new LinkedList<JSONObject>();
	/** cons */
	public NamedChannel(String name) {
		this.name = name;
	}
	/** add value */
	public synchronized void addValue(JSONObject value) {
		values.add(value);
		while (values.size()>count)
			values.removeFirst();
	}
	/** update metadata */
	public synchronized void updateConfiguration(int count, double period, double timeout) {
		this.count = count;
		while (values.size()>count)
			values.removeFirst();
		this.period = period;
		this.timeout = timeout;
		expires = System.currentTimeMillis()+(long)(1000*timeout);
	}
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}
	/**
	 * @return the expires
	 */
	public long getExpires() {
		return expires;
	}
	/**
	 * @return the values
	 */
	@SuppressWarnings("unchecked")
	public synchronized LinkedList<JSONObject> getValues() {
		return (LinkedList<JSONObject>)values.clone();
	}
	/** expired? */
	public synchronized boolean isExpired() {
		return System.currentTimeMillis()>expires;
	}
	public synchronized void clearValues() {
		values.clear();
	}
}
