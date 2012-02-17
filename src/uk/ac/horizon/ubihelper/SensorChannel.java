/**
 * 
 */
package uk.ac.horizon.ubihelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
	private boolean active = false;
	
	public SensorChannel(String name, Service service, int sensorType) {
		super(name);
		mSensorManager = (SensorManager) service.getSystemService(Context.SENSOR_SERVICE);
		mSensor = mSensorManager.getDefaultSensor(sensorType);		
		if (mSensor==null)
			Log.w(TAG,"No sensor found for "+name+" (type "+sensorType+")");
	}

	public synchronized void updateConfiguration(int count, double period,
			double timeout) {
		double oldPeriod = this.period;
		super.updateConfiguration(count, period, timeout);
		if ((isExpired() || oldPeriod!=period) && active) {
			mSensorManager.unregisterListener(this);
			active = false;
			clearValues();
			Log.d(TAG,"Stop on update sensor "+name);
		}
		else if (!active && !isExpired() && mSensor!=null) {
			mSensorManager.registerListener(this, mSensor, (int)(1000000*period));
			active = true;
			Log.d(TAG,"Start sensor "+name);
		}
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
		addValue(value);
		if (isExpired()) {
			mSensorManager.unregisterListener(this);
			active = false;
			clearValues();
			Log.d(TAG,"Stop on change sensor "+name);
		}
	}
}
