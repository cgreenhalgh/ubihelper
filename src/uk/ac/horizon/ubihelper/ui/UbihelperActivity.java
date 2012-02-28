package uk.ac.horizon.ubihelper.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import uk.ac.horizon.ubihelper.R;
import uk.ac.horizon.ubihelper.R.id;
import uk.ac.horizon.ubihelper.R.layout;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/** dummy activity */
public class UbihelperActivity extends Activity {
	
	private Handler mHandler;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();
        setContentView(R.layout.main);
        Button test = (Button)findViewById(R.id.test_button);
        test.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				new Thread() {
					public void run() {
						doTest();
					}
				}.start();
			}
        });
    }

	protected void doTest() {
		DefaultHttpClient httpClient = new DefaultHttpClient();
	    HttpPost httppost = new HttpPost("http://127.0.0.1:8180/ubihelper");
	    try {
			httppost.setEntity(new StringEntity(
					"[{\"name\":\"magnetic\",\"period\":0.5,\"count\":1,\"timeout\":20},"+
					"{\"name\":\"accelerometer\",\"period\":0.5,\"count\":1,\"timeout\":20}]"
					, "UTF-8"));
			HttpResponse response = httpClient.execute(httppost);
			final int status = response.getStatusLine().getStatusCode();
			final String message = response.getStatusLine().getReasonPhrase();
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			response.getEntity().writeTo(baos);
			final String resp = baos.toString("UTF-8");
			mHandler.post(new Runnable() {
				public void run() {
					TextView text = (TextView)findViewById(R.id.test_text);
					text.setText("Got "+status+" ("+message+"): "+resp);
				}				
			});
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}