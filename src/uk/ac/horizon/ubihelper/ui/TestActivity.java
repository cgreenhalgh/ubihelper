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
public class TestActivity extends Activity {
	
	private Handler mHandler;
	private TextView text;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();
        setContentView(R.layout.main);
        text = (TextView)findViewById(R.id.test_text);
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

    private void setText(final String t) {
		mHandler.post(new Runnable() {
			public void run() {
				text.setText(t);
			}				
		});    	
    }
    private void append(final String t) {
		mHandler.post(new Runnable() {
			public void run() {
				text.append(t);
			}				
		});    	
    }
	protected void doTest() {
		DefaultHttpClient httpClient = new DefaultHttpClient();
		String url= "http://127.0.0.1:8180/ubihelper";
	    HttpPost httppost = new HttpPost(url);
	    String request = "[{\"name\":\"magnetic\",\"period\":0.5,\"count\":1,\"timeout\":20},"+
				"{\"name\":\"accelerometer\",\"period\":0.5,\"count\":1,\"timeout\":20}]";
	    setText("POST "+url+"\n"+request);
	    try {
			httppost.setEntity(new StringEntity(request, "UTF-8"));
			HttpResponse response = httpClient.execute(httppost);
			final int status = response.getStatusLine().getStatusCode();
			final String message = response.getStatusLine().getReasonPhrase();
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			response.getEntity().writeTo(baos);
			
			final String resp = baos.toString("UTF-8");
			append("\nGot "+status+" ("+message+"): "+resp);
		} catch (ClientProtocolException e) {
			e.printStackTrace();
			append("\nError: "+e);
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
			append("\nError: "+e1);
		} catch (IOException e) {
			e.printStackTrace();
			append("\nError: "+e);
		}
	}
}