/**
 * 
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
	private static final double DEFAULT_PERIOD = 0.2;
	private static final double DEFAULT_MIN_INTERVAL = 0.1;
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
