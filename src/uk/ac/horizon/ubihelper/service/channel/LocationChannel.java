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

import org.json.JSONException;
import org.json.JSONObject;

import uk.ac.horizon.ubihelper.channel.NamedChannel;
import uk.ac.horizon.ubihelper.service.Service;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

/**
 * @author cmg
 *
 */
public class LocationChannel extends NamedChannel implements LocationListener {

	private static final String TAG = "ubihelper-location";
	static final float MIN_DISTANCE = 0;
	private String provider;
	private LocationManager location;
	
	public static List<String> getAllProviders(Service service) {
		LocationManager location = (LocationManager)service.getSystemService(Service.LOCATION_SERVICE);
		if (location!=null) {
			return location.getAllProviders();
		}
		else {
			Log.w(TAG, "No LocationManager");
			return new LinkedList<String>();
		}
	}
	public LocationChannel(String name, Service service, String provider) {
		super(name);
		this.provider = provider;
		location = (LocationManager)service.getSystemService(Service.LOCATION_SERVICE);
	}

	@Override
	protected void handleStart() {
		if (location!=null) {
			try {
				location.requestLocationUpdates(provider, (long)(period*1000), MIN_DISTANCE, this);				
				Log.d(TAG,"Start location updates "+name);	
			} catch (Exception e) {
				Log.w(TAG,"Error requesting updates for "+provider+": "+e);
			}
			if (!location.isProviderEnabled(provider)) {
				Log.d(TAG,"Provider "+provider+" not enabled");
			}
		}
	}

	@Override
	protected void handleStop() {
		Log.d(TAG, "Stop location "+name);
		if (location!=null)
			location.removeUpdates(this);
	}

	public void onLocationChanged(Location loc) {
		// TODO Auto-generated method stub
		JSONObject value = new JSONObject();
		try {
			// event time is in nanoseconds
			value.put("timestamp", System.currentTimeMillis());
			value.put("time", loc.getTime());
			value.put("lat", loc.getLatitude());
			value.put("lon", loc.getLongitude());
			value.put("provider", loc.getProvider());
			if (loc.hasAltitude())
				value.put("altitude", loc.getAltitude());
			if (loc.hasAccuracy())
				value.put("accuracy", loc.getAccuracy());
			Log.d(TAG,"onSensorChanged("+name+"): "+value);
		} catch (JSONException e) {
			/* ignore */
		}
		onNewValue(value);
		
	}
	public void onProviderDisabled(String arg0) {
		// TODO Auto-generated method stub
		
	}
	public void onProviderEnabled(String arg0) {
		// TODO Auto-generated method stub
		
	}
	public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
		// TODO Auto-generated method stub
		
	}
}
