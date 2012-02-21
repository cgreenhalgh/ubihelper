/**
 * 
 */
package uk.ac.horizon.ubihelper;

import java.util.ArrayList;

import android.app.Activity;
import android.app.ListActivity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.HeaderViewListAdapter;
import android.widget.ListView;
import android.widget.ListView.FixedViewInfo;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author cmg
 *
 */
public class SearchPeersActivity extends ListActivity {
	private View searchView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// TODO from table
		ArrayAdapter<String> aa = new ArrayAdapter<String>(this, R.layout.peer_item);
		aa.add("Hello list 2");
		ListView lv = getListView();
		// following line fails with addView not supported in AdapterView
		searchView = getLayoutInflater().inflate(R.layout.search_peer_item, null);
		((TextView)searchView).setText("Searching...");
		lv.addHeaderView(searchView, "Add a new item", true);
		lv.setAdapter(aa);
		
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (view==searchView) {
					
				}
				else {
					Toast.makeText(getApplicationContext(), ((TextView) view).getText(), Toast.LENGTH_SHORT).show();
				}
			}			
		});
	}

}
