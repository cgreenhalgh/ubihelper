/**
 * 
 */
package uk.ac.horizon.ubihelper.ui;

import java.util.Collections;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import uk.ac.horizon.ubihelper.R;
import uk.ac.horizon.ubihelper.channel.ChannelManager;
import uk.ac.horizon.ubihelper.service.BroadcastIntentSubscription;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * @author cmg
 *
 */
public class ChannelListActivity extends ChannelViewActivity {
	private ListView listView;
	private ArrayAdapter<String> listAdapter;
	
	public static Intent getStartActivityIntent(Context context) {
		Intent i = new Intent(context, ChannelListActivity.class);
		i.putExtra(BroadcastIntentSubscription.EXTRA_NAME, ChannelManager.CHANNEL_CHANNELS);
		return i;
	}
	public static Intent getStartActivityIntent(
			Context context, String name) {
		Intent i = new Intent(context, ChannelListActivity.class);
		i.putExtra(BroadcastIntentSubscription.EXTRA_NAME, name);
		return i;
	}

	
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(getIntent().getExtras().getString(BroadcastIntentSubscription.EXTRA_NAME));
		setContentView(R.layout.channel_list);
		listView = (ListView)findViewById(R.id.channel_list);
		listAdapter = new ArrayAdapter<String>(this, R.layout.channel_list_item);
		listView.setAdapter(listAdapter);
		
		listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (position>=0 && position<=listAdapter.getCount()) {
					String name = listAdapter.getItem(position);
					if (name.equals("peers") || name.endsWith("/peers")) {
						Intent i = ChannelPeerListActivity.getStartActivityIntent(ChannelListActivity.this, name);
						startActivity(i);						
					}
					else if (name.equals("channels") || name.endsWith("/channels")) {
						Intent i = ChannelListActivity.getStartActivityIntent(ChannelListActivity.this, name);
						startActivity(i);												
					}
					else {
						Intent i = ChannelValueActivity.getStartActivityIntent(ChannelListActivity.this, name);
						startActivity(i);
					}
				}
			}			
		});
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
