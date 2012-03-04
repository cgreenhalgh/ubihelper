/**
 * 
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
