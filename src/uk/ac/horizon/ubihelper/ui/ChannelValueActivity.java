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
