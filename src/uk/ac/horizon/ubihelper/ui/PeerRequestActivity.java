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
import uk.ac.horizon.ubihelper.R.id;
import uk.ac.horizon.ubihelper.R.layout;
import uk.ac.horizon.ubihelper.service.PeerManager;
import uk.ac.horizon.ubihelper.service.Service;
import uk.ac.horizon.ubihelper.service.Service.LocalBinder;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * @author cmg
 *
 */
public class PeerRequestActivity extends Activity {
	private TextView nameView;
	private EditText pinText;
	private PeerManager peerManager;

	static final int DIALOG_NO_PIN = 1;
	protected static final String TAG = "ubihelper-peerreqact";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.peer_request);
		nameView = (TextView)findViewById(R.id.peer_request_name);
		pinText = (EditText)findViewById(R.id.peer_request_pin);
		Button accept = (Button)findViewById(R.id.peer_request_accept);
		accept.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				String pin = pinText.getText().toString();
				if (pin==null || pin.length()==0) {
					showDialog(DIALOG_NO_PIN);
					return;
				}
				if (peerManager!=null)
					peerManager.acceptPeerRequest(getIntent(), pin);
				finish();
			}
		});
		Button reject = (Button)findViewById(R.id.peer_request_reject);
		reject.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				if (peerManager!=null)
					peerManager.rejectPeerRequest(getIntent());
				finish();
			}
		});
	}
	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		switch (id) {
		case DIALOG_NO_PIN: {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("You need to enter the PIN to accept the connection")
			       .setCancelable(true)
			       .setPositiveButton("OK", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			        	   dialog.cancel();
			           }
			       });
			dialog = builder.create();	
			break;
		}
		default:
			dialog = super.onCreateDialog(id);				
		}
		return dialog;
	}
	

	/** monitor binding to service (used for preference update push) */
	private ServiceConnection mServiceConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName name, IBinder binder) {
			peerManager = ((Service.LocalBinder)binder).getService().getPeerManager();
			Log.d(TAG,"Service connected");
		}
		public void onServiceDisconnected(ComponentName name) {
			peerManager = null;
		}		
	};

	@Override
	protected void onStart() {
		super.onStart();
		String name = getIntent().getExtras().getString(PeerManager.EXTRA_NAME);
		nameView.setText(name);
		bindService(new Intent(this, Service.class), mServiceConnection, 0);
	}

	@Override
	protected void onStop() {
		super.onStop();
		unbindService(mServiceConnection);
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
	}

}
