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
package uk.ac.horizon.ubihelper.service.channel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import uk.ac.horizon.ubihelper.channel.NamedChannel;
import uk.ac.horizon.ubihelper.service.Service;

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
	private long lastValueTime;
	private long minInterval;

	public SensorChannel(String name, Service service, Sensor sensor) {
		super(name);
		mSensorManager = (SensorManager) service.getSystemService(Context.SENSOR_SERVICE);
		mSensor = sensor;
	}
	
	public SensorChannel(String name, Service service, int sensorType) {
		super(name);
		mSensorManager = (SensorManager) service.getSystemService(Context.SENSOR_SERVICE);
		if (mSensorManager!=null) {
			mSensor = mSensorManager.getDefaultSensor(sensorType);		
			if (mSensor==null)
				Log.w(TAG,"No sensor found for "+name+" (type "+sensorType+")");
		}
	}

	@Override
	protected void handleStart() {
		minInterval = (long)(1000*period);
		lastValueTime = 0;
		if (mSensor!=null) {
			mSensorManager.registerListener(this, mSensor, (int)(1000000*period));
			Log.d(TAG,"Start sensor "+name);
		}
	}

	/* (non-Javadoc)
	 * @see uk.ac.horizon.ubihelper.channel.NamedChannel#handleStop()
	 */
	@Override
	protected void handleStop() {
		if (mSensor!=null) {
			Log.d(TAG, "Stop sensor "+name);
			mSensorManager.unregisterListener(this);
		}
	}

	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// ? 
	}

	public void onSensorChanged(SensorEvent event) {
		long now = System.currentTimeMillis();
		long elapsed = now-lastValueTime;
		if (elapsed<minInterval) {
			Log.d(TAG,"Discard sensor value "+name+", elapsed "+elapsed+"/"+minInterval);
			return;
		}
		lastValueTime = now;
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
