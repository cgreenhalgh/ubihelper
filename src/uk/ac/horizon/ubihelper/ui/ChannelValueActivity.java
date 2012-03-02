/**
 * 
 */
package uk.ac.horizon.ubihelper.ui;

import uk.ac.horizon.ubihelper.R;
import uk.ac.horizon.ubihelper.service.BroadcastIntentSubscription;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

/**
 * @author cmg
 *
 */
public class ChannelValueActivity extends ChannelViewActivity {
	private TextView nameView;
	private TextView valueView;

	public static Intent getStartActivityIntent(Context context, String channelName) {
		Intent i = new Intent(context, ChannelValueActivity.class);
		i.putExtra(BroadcastIntentSubscription.EXTRA_NAME, channelName);
		return i;
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.channel_value);
		nameView = (TextView)findViewById(R.id.channel_value_name);
		valueView = (TextView)findViewById(R.id.channel_value_value);
	}

	protected void refresh(Intent intent) {
		if (channelName!=null)
			nameView.setText(channelName);
		valueView.setText("");
		if (intent!=null) {
			String value = intent.getExtras().getString(BroadcastIntentSubscription.EXTRA_VALUE);
			if (value!=null) 
				valueView.setText(value);
		}
	}

}
