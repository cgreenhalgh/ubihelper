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

import java.util.LinkedList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import uk.ac.horizon.ubihelper.channel.NamedChannel;
import uk.ac.horizon.ubihelper.service.Service;

import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.GpsStatus.Listener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

/**
 * @author cmg
 *
 */
public class GpsStatusChannel extends NamedChannel implements Listener, LocationListener {

	private static final String TAG = "ubihelper-gpsstatus";
	private static final float NOMINAL_DISTANCE = 100;
	private LocationManager location;
	
	public GpsStatusChannel(String name, Service service) {
		super(name);
		location = (LocationManager)service.getSystemService(Service.LOCATION_SERVICE);
	}

	@Override
	protected void handleStart() {
		if (location!=null) {
			try {
				location.addGpsStatusListener(this);
				Log.d(TAG,"Start gpsStatus updates");	
				// force start GPS?!
				location.requestLocationUpdates("gps", (long)(period*1000), NOMINAL_DISTANCE, this);				

			} catch (Exception e) {
				Log.w(TAG,"Error requesting gpsStatus updates: "+e);
			}
		}
	}

	@Override
	protected void handleStop() {
		Log.d(TAG, "Stop gpsStatus");
		if (location!=null) {
			location.removeGpsStatusListener(this);
			location.removeUpdates(this);
		}
	}

	public void onGpsStatusChanged(int type) {
		if (location!=null) {
			GpsStatus status = location.getGpsStatus(null);
			JSONObject value = new JSONObject();
			try {
				// event time is in nanoseconds
				value.put("timestamp", System.currentTimeMillis());
				value.put("status", type);
				value.put("maxSatellites", status.getMaxSatellites());
				value.put("timeToFirstFix", status.getTimeToFirstFix());
				JSONArray sats = new JSONArray();
				value.put("satellites", sats);
				for (GpsSatellite satellite : status.getSatellites()) {
					JSONObject s= new JSONObject();
					s.put("used", satellite.usedInFix());
					s.put("azimuth", satellite.getAzimuth());
					s.put("elevation", satellite.getElevation());
					s.put("prn", satellite.getPrn());
					s.put("snr", satellite.getSnr());
					sats.put(s);
				}
				onNewValue(value);

			} catch (JSONException e) {
				/* ignore */
			}
		}		
	}

	public void onLocationChanged(Location location) {
		// TODO Auto-generated method stub
		
	}

	public void onProviderDisabled(String provider) {
		// TODO Auto-generated method stub
		
	}

	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub
		
	}

	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub
		
	}
}
