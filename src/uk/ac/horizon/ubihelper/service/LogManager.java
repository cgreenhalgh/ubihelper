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

package uk.ac.horizon.ubihelper.service;

import java.io.File;

import org.json.JSONObject;

import android.content.Context;
import android.preference.PreferenceManager;
import uk.ac.horizon.ubihelper.channel.ChannelManager;

/**
 * @author cmg
 *
 */
public class LogManager {

	private Service service;
	private boolean logging = false;
	
	public LogManager(Service service, ChannelManager channelManager) {
		this.service = service;
	}
	
	public synchronized void close() {
		if (logging) {
			stop();
			logging = false;
		}
	}	

	public static File getLogDirectory(Context context) {
		return context.getExternalFilesDir("logs");
	}
	private boolean getLog() {
		return PreferenceManager.getDefaultSharedPreferences(service).getBoolean("log", false);
	}

	public synchronized void checkPreferences() {
		boolean log = getLog();
		if (!log && logging) {
			stop();
			logging = false;
		}
		if (log && !logging) {
			start();
			logging = true;
		}
		else 
			checkSubscriptions();
	}

	private void checkSubscriptions() {
		// TODO Auto-generated method stub
		
	}

	private void stop() {
		// TODO Auto-generated method stub
		
	}

	private void start() {
		// TODO Auto-generated method stub
		
	}

	// called from LogSubscription
	public synchronized void logValue(String channelName, JSONObject value) {
		// TODO Auto-generated method stub
		
	}
}
