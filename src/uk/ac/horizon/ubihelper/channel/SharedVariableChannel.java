/**
 * 
 */
package uk.ac.horizon.ubihelper.channel;

import org.json.JSONObject;

/**
 * @author cmg
 *
 */
public class SharedVariableChannel extends NamedChannel {

	private JSONObject value;
	
	public SharedVariableChannel(String name, JSONObject initialValue) {
		super(name);
		this.value = initialValue;
	}

	public void setValue(JSONObject value) {
		synchronized (this) {
			this.value = value;
		}
		this.onNewValue(value);
	}

	@Override
	public synchronized JSONObject getImmediateValue() {
		return value;
	}
	
}
