/**
 * 
 */
package uk.ac.horizon.ubihelper.channel;

import org.json.JSONObject;

/**
 * @author cmg
 *
 */
public class ChannelValueEvent {
	private NamedChannel channel;
	private JSONObject value;
	
	public ChannelValueEvent() {}

	/**
	 * @param channel
	 * @param value
	 */
	public ChannelValueEvent(NamedChannel channel, JSONObject value) {
		super();
		this.channel = channel;
		this.value = value;
	}

	/**
	 * @return the channel
	 */
	public NamedChannel getChannel() {
		return channel;
	}

	/**
	 * @return the value
	 */
	public JSONObject getValue() {
		return value;
	}
	

}
