/**
 * 
 */
package uk.ac.horizon.ubihelper.service;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import uk.ac.horizon.ubihelper.channel.NamedChannel;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * @author cmg
 *
 */
public class SensorChannel extends NamedChannel implements SensorEventListener {

	private static final String TAG = "ubihelper-sensor";
	private SensorManager mSensorManager;
	private Sensor mSensor;
	
	public SensorChannel(String name, Service service, int sensorType) {
		super(name);
		mSensorManager = (SensorManager) service.getSystemService(Context.SENSOR_SERVICE);
		mSensor = mSensorManager.getDefaultSensor(sensorType);		
		if (mSensor==null)
			Log.w(TAG,"No sensor found for "+name+" (type "+sensorType+")");
	}

	@Override
	protected void handleStart() {
		mSensorManager.registerListener(this, mSensor, (int)(1000000*period));
		Log.d(TAG,"Start sensor "+name);
	}

	/* (non-Javadoc)
	 * @see uk.ac.horizon.ubihelper.channel.NamedChannel#handleStop()
	 */
	@Override
	protected void handleStop() {
		Log.d(TAG, "Stop sensor "+name);
		mSensorManager.unregisterListener(this);
	}

	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// ? 
	}

	public void onSensorChanged(SensorEvent event) {
		JSONObject value = new JSONObject();
		try {
			// event time is in nanoseconds
			value.put("timestamp", event.timestamp/1000000);
			JSONArray values = new JSONArray();
			for (int i=0; i<event.values.length; i++)
				values.put(event.values[i]);
			value.put("values", values);
			value.put("accuracy", event.accuracy);
			Log.d(TAG,"onSensorChanged("+name+"): "+value);
		} catch (JSONException e) {
			/* ignore */
		}
		onNewValue(value);
	}
}
