/**
 * 
 */
package uk.ac.horizon.ubihelper.ui;

import uk.ac.horizon.ubihelper.R;
import uk.ac.horizon.ubihelper.dns.DnsUtils;
import uk.ac.horizon.ubihelper.service.PeerManager;
import uk.ac.horizon.ubihelper.service.Service;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.net.InetAddress;

/**
 * @author cmg
 *
 */
public class PeerManualAddActivity extends Activity {
	private static String TAG = "ubihelper-manual";
	private TextView localHostView, localPortView;
	private EditText hostText, portText;
	private static final int DIALOG_BAD_HOST = 1;
	private static final int DIALOG_BAD_PORT = 2;
	private static final int DIALOG_ADD_PEER_ERROR = 3;
	private PeerManager peerManager;
	private BroadcastReceiver updateReceiver = new UpdateReceiver();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.peer_manual_add);
		localHostView = (TextView)findViewById(R.id.peermanualadd_local_host);
		localPortView = (TextView)findViewById(R.id.peermanualadd_local_port);
		hostText = (EditText)findViewById(R.id.peermanualadd_host);
		portText = (EditText)findViewById(R.id.peermanualadd_port);
		Button accept = (Button)findViewById(R.id.peermanualadd_ok);
		accept.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				InetAddress host = null;
				try {
					host = InetAddress.getByName(hostText.getText().toString());
					Log.d(TAG,"Look up host "+hostText.getText()+" -> "+host);
				}
				catch (Exception e) {
					showDialog(DIALOG_BAD_HOST);
					return;
				}
				if (host==null) {
					showDialog(DIALOG_BAD_HOST);
					return;					
				}
				int port = 0;
				try {
					port = Integer.parseInt(portText.getText().toString());
				}
				catch (Exception e) {
					showDialog(DIALOG_BAD_PORT);
					return;					
				}
				if (peerManager!=null) {
					Intent i = peerManager.addPeer(host, port);
					if (i!=null) {
						startActivity(i);
						finish();
					}
					else
						showDialog(DIALOG_ADD_PEER_ERROR);
				}
				else {
					showDialog(DIALOG_ADD_PEER_ERROR);
					return;
				}
			}
		});
		Button reject = (Button)findViewById(R.id.peermanualadd_cancel);
		reject.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				finish();
			}
		});
	}
	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		switch (id) {
		case DIALOG_BAD_HOST: {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("Sorry, I could not understand the host name/IP")
			       .setCancelable(true)
			       .setPositiveButton("OK", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			        	   dialog.cancel();
			           }
			       });
			dialog = builder.create();	
			break;
		}
		case DIALOG_BAD_PORT: {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("Sorry, I could not understand the port number")
			       .setCancelable(true)
			       .setPositiveButton("OK", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			        	   dialog.cancel();
			           }
			       });
			dialog = builder.create();	
			break;
		}
		case DIALOG_ADD_PEER_ERROR: {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("Sorry, there was a problem adding the peer")
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
			refresh();
		}
		public void onServiceDisconnected(ComponentName name) {
			peerManager = null;
		}		
	};
	/** broadcast receiver */
	private class UpdateReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG,"Received broadcast "+intent.getAction());
			PeerManualAddActivity.this.runOnUiThread(new Runnable() {
				public void run() {
					refresh();
				}
			});
		}		
	}
	@Override
	protected void onStart() {
		super.onStart();
		bindService(new Intent(this, Service.class), mServiceConnection, 0);
	}

	private void refresh() {
		WifiManager wifi = (WifiManager)getSystemService(WIFI_SERVICE);
		if(wifi!=null) {
			WifiInfo ci = wifi.getConnectionInfo();
			if (ci!=null) {
				int ip = ci.getIpAddress();
				if(ip==0)
					localHostView.setText("Address not yet available");
				else {
					String host = DnsUtils.ip2string(ci.getIpAddress());
					localHostView.setText(host);
				}
			}
			else 
				localHostView.setText("Wifi not connected");
		}
		else {
			localHostView.setText("No Wifi adapter");
		}
		if (peerManager!=null)
		{
			int port = peerManager.getServerPort();
			if (port==0)
				localPortView.setText("Sorry, no server running");
			else
				localPortView.setText(Integer.toString(port));
		}
		else
			localPortView.setText("Sorry, no peer manager");
	}
	@Override
	protected void onStop() {
		super.onStop();
		unbindService(mServiceConnection);
	}
	@Override
	protected void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		registerReceiver(updateReceiver, filter);
		refresh();
	}
	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(updateReceiver);
	}
	
}
