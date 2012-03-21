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

import java.util.Collections;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import uk.ac.horizon.ubihelper.R;
import uk.ac.horizon.ubihelper.channel.ChannelManager;
import uk.ac.horizon.ubihelper.protocol.PeerInfo;
import uk.ac.horizon.ubihelper.service.BroadcastIntentSubscription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CompoundButton.OnCheckedChangeListener;

/**
 * @author cmg
 *
 */
public class LoggingChannelListActivity extends ChannelViewActivity {
	private ListView listView;
	private ArrayAdapter<String> listAdapter;
	private SharedPreferences prefs ;
	static final String LOG_CHANNELS_PREFERENCE = "log_channels";
	
	public static Intent getStartActivityIntent(Context context) {
		Intent i = new Intent(context, LoggingChannelListActivity.class);
	//	i.putExtra(BroadcastIntentSubscription.EXTRA_NAME, ChannelManager.CHANNEL_CHANNELS);
		return i;
	}
	
	@Override
	protected String getChannelName() {
		return ChannelManager.CHANNEL_CHANNELS;
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.log_channel_list);
		listView = (ListView)findViewById(R.id.log_channel_list);
		
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		final OnCheckedChangeListener enableListener = new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				String cn= (String)buttonView.getTag();
				Log.d(TAG,"onCheckedChanged "+cn+" -> "+isChecked);
				// TODO - update preference
				if (isChecked && !isLogChannel(cn))
					enableChannel(cn);
				else if (!isChecked && isLogChannel(cn))
					disableChannel(cn);
				
			}
		};
		//listAdapter = new ArrayAdapter<String>(this, R.layout.channel_list_item);
		listAdapter = new ArrayAdapter<String>(this, R.layout.log_channel_item, R.id.log_channel_item_name) {
			@Override 
			public View getView(int position, View convertView, ViewGroup parent) {
				View v = convertView;
				if (v==null) {
					v = LoggingChannelListActivity.this.getLayoutInflater().inflate(R.layout.log_channel_item, null);
				}
				String cn = getItem(position);
				CheckBox enabled = (CheckBox)v.findViewById(R.id.log_channel_item_enabled);
				enabled.setTag(cn);
				enabled.setChecked(isLogChannel(cn));
				enabled.setOnCheckedChangeListener(enableListener);
				TextView name = (TextView)v.findViewById(R.id.log_channel_item_name);
				name.setText(cn);
				//TextView description = (TextView)v.findViewById(R.id.peer_item_description);
				//description.setText(pi.trusted ? "Trusted peer" : "Untrusted peer");
				v.setClickable(true);
				v.setTag(cn);
				//v.setOnClickListener(clickListener);
				return v;
			}			
		};
		listView.setAdapter(listAdapter);
		
	}

	protected void disableChannel(String dcn) {
		String cns [] = prefs.getString(LOG_CHANNELS_PREFERENCE, "").split(";");
		StringBuilder sb = new StringBuilder();
		for (int i=0; i<cns.length; i++) {
			String cn = cns[i];
			if (cn.length()==0)
				continue;
			if (cn.equals(dcn))
				continue;
			if (sb.length()>0)
				sb.append(";");
			sb.append(cn);
		}
		prefs.edit().putString(LOG_CHANNELS_PREFERENCE, sb.toString()).commit();	
	}

	protected void enableChannel(String cn) {
		String cns = prefs.getString(LOG_CHANNELS_PREFERENCE, "");
		if (cns.length()==0)
			prefs.edit().putString(LOG_CHANNELS_PREFERENCE, cn).commit();
		else
			prefs.edit().putString(LOG_CHANNELS_PREFERENCE, cns+";"+cn).commit();
	}

	protected boolean isLogChannel(String cn) {
		String cns [] = prefs.getString(LOG_CHANNELS_PREFERENCE, "").split(";");
		for (int i=0; i<cns.length; i++) 
			if (cns[i].equals(cn))
				return true;
		return false;
	}

	@Override
	protected void refresh(Intent intent) {
		listAdapter.clear();
		if (intent!=null) {
			String value= intent.getExtras().getString(BroadcastIntentSubscription.EXTRA_VALUE);
			if (value!=null) {
				try {
					JSONObject val = new JSONObject(value);
					JSONArray names = val.getJSONArray(ChannelManager.KEY_NAMES);
					TreeSet<String> nameset = new TreeSet<String>();
					for (int i=0; i<names.length(); i++) 
						nameset.add(names.getString(i));
					for (String name : nameset) 
						listAdapter.add(name);
				} catch (JSONException e) {
					Log.w(TAG,"Parsing channels value "+value+": "+e);
				}
			}
		}
	}


}
