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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TreeSet;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.util.Log;
import uk.ac.horizon.ubihelper.R;
import uk.ac.horizon.ubihelper.channel.ChannelManager;
import uk.ac.horizon.ubihelper.channel.Subscription;
import uk.ac.horizon.ubihelper.ui.LoggingPreferences;
import uk.ac.horizon.ubihelper.ui.PeerRequestActivity;

/**
 * @author cmg
 *
 */
public class LogManager {
	static final String TAG = "ubihelper-log";
	public static final String LOG = "log";
	public static final String LOG_PERIOD = "log_period";
	public static final String LOG_FILE_PREFIX = "log_file_prefix";
	public static final String LOG_MAX_FILE_SIZE = "log_max_file_size";
	public static final String LOG_MAX_CACHE_SIZE = "log_max_cache_size";
	public static final String LOG_CHANNELS = "log_channels";
	public static final String LOG_DELETE_OLD_FILES = "log_delete_old_files";
	public static final String PUBLIC_FILES_DIR = "UbihelperLogs";
	public static final String FILES_DIR = "logs";
	private static final int DEFAULT_PERIOD = 1000;
	private static final double MIN_PERIOD_CHANGE = 0.001;
	public static final int MIN_NEW_FILE_SIZE = 10000;
	private static final int BUFFER_SIZE = 10000;
	private static final int DEFAULT_FLUSH_DELAY = 1000;

	private Service service;
	private ChannelManager channelManager;
	private boolean logging = false;
	private HashMap<String,LogSubscription> subscriptions = new HashMap<String,LogSubscription>();
	private File currentLogDir;
	private File currentLogFile;
	private long currentFileLength;
	private long currentCacheUsage;
	private OutputStream currentLogStream;
	private int maxFileSize;
	private int maxCacheSize;
	private List<File> cacheFiles;
	private boolean flushPosted;
	private static int ERROR_NOTIFICATION_ID = 1001;
	private boolean errorNotificationVisible;
	private String errorNotificationText;
	
	public LogManager(Service service, ChannelManager channelManager) {
		this.service = service;
		this.channelManager = channelManager;

		// debug
//	    Log.d(TAG, "These should work in all versions of Android:");
//	    Log.d(TAG, "Environment.getExternalStorageDirectory() = " 
//	    		+ Environment.getExternalStorageDirectory());
//	    Log.d(TAG, "Environment.getDataDirectory() = " 
//	    		+ Environment.getDataDirectory());
//	    Log.d(TAG, "Environment.getDownloadCacheDirectory() = " 
//	    		+ Environment.getDownloadCacheDirectory());
//	    Log.d(TAG, "Environment.getRootDirectory() = " 
//	    		+ Environment.getRootDirectory());
//
//	    Log.d(TAG, "These were added in FroYo (SDK v8):");
//	    Log.d(TAG,
//	    		"Environment.getExternalStoragePublicDirectory(PUBLIC_FILES_DIR) = " 
//	    				+ Environment.getExternalStoragePublicDirectory(FILES_DIR));
//	    Log.d(TAG, "getExternalCacheDir() = " + service.getExternalCacheDir());
//	    Log.d(TAG, "getExternalFilesDir(null) = " 
//	    		+ service.getExternalFilesDir(null));
//	    Log.d(TAG,
//	    		"getExternalFilesDir(FILES_DIR) = " 
//	    				+ service.getExternalFilesDir(FILES_DIR));

		maxFileSize = getMaxFileSize();
		maxCacheSize = getMaxCacheSize();
	}
	
	public synchronized void close() {
		if (logging) {
			logging = false;
			stop();
		}
		clearError();
	}	

	private static File getLogDirectory(Context context) {
		// may return null
		// Note: needs Write external storage permission
		return context.getExternalFilesDir(FILES_DIR);
	}
	private boolean getLog() {
		return PreferenceManager.getDefaultSharedPreferences(service).getBoolean(LOG, false);
	}
	private float getLogPeriod() {
		float period = DEFAULT_PERIOD;
		try {
			String value= PreferenceManager.getDefaultSharedPreferences(service).getString(LOG_PERIOD, Integer.toString(DEFAULT_PERIOD));
			period = Float.parseFloat(value);
		}
		catch (Exception e) {
			Log.e(TAG,"Error getting log_period preference: "+e);
		}
		return period;
	}
	private int getIntPreference(String name, int defaultValue) {
		int val= defaultValue;
		try {
			String sval= PreferenceManager.getDefaultSharedPreferences(service).getString(name, Integer.toString(defaultValue));
			val = Integer.parseInt(sval);
		}
		catch (Exception e) {
			Log.e(TAG,"Error getting "+name+" preference: "+e);
		}
		return val;
	}
	private int getMaxFileSize() {
		int size = getIntPreference(LOG_MAX_FILE_SIZE, 0);
		if (size<MIN_NEW_FILE_SIZE)
			size = MIN_NEW_FILE_SIZE;
		return size;
	}
	private int getMaxCacheSize() {
		int size = getIntPreference(LOG_MAX_CACHE_SIZE, 0);
		if (size<MIN_NEW_FILE_SIZE)
			size = MIN_NEW_FILE_SIZE;
		return size;
	}
	// called from Service when prefs changed
	public void checkPreferences(String changed) {
		if (LOG_CHANNELS.equals(changed) || LOG.equals(changed) || LOG_PERIOD.equals(changed))
			// may need immediate adjustment!
			checkPreferences();
		else if (LOG_FILE_PREFIX.equals(changed))
			// force re-open on next log event (will use new prefix)
			closeCurrentFile();
		else if (LOG_MAX_CACHE_SIZE.equals(changed))
			maxCacheSize = getMaxCacheSize();
		else if (LOG_MAX_FILE_SIZE.equals(changed))
			maxFileSize = getMaxFileSize();
	}
	
	private void closeCurrentFile() {
		boolean ok = true;
		if (currentLogStream!=null) {
			try {
				currentLogStream.flush();
				currentLogStream.close();
			}
			catch (Exception e) {
				Log.e(TAG,"Error closing currentLogStream: "+e);
				ok = false;
			}
			currentLogStream = null;
		}
		if (currentLogFile!=null) {
			try {
				currentCacheUsage += currentLogFile.length();
			}
			catch (Exception e) {
				Log.e(TAG,"Error updating cache usage on closeCurrentFile: "+e);
				ok = false;
			}
			currentLogFile = null;
			currentFileLength = 0;
		}
		if (!ok) {
			// reset
			currentLogDir = null;
			currentLogFile = null;
			currentCacheUsage = 0;
			currentFileLength = 0;
		}
	}

	// called from Service on start-up (and from change of prefs) 
	public synchronized void checkPreferences() {
		boolean log = getLog();
		if (!log && logging) {
			logging = false;
			stop();
		}
		if (log && !logging) {
			logging = true;
			start();
		}
		else if (logging)
			checkSubscriptions();
	}
	private String[] getChannels() {
		String cnpref = PreferenceManager.getDefaultSharedPreferences(service).getString(LOG_CHANNELS, "");
		Log.d(TAG,"log_channels="+cnpref);
		String cns[] = cnpref.split(";");
		return cns;
	}

	private synchronized void checkSubscriptions() {
		float period = getLogPeriod();
		String cns[] = getChannels();
		TreeSet<String> ecns = new TreeSet<String>();
		ecns.addAll(subscriptions.keySet());
		for (int i=0; i<cns.length; i++) {
			String cn = cns[i];
			if (cn.length()==0)
				continue;
			ecns.remove(cn);
			LogSubscription sub = subscriptions.get(cn);
			if (sub==null) {
				// add
				Log.d(TAG,"Start logging "+cn+" (update)");
				sub = new LogSubscription(this, cn);
				sub.updateConfiguration(period, period/2, 0);
				channelManager.addSubscription(sub);
				subscriptions.put(cn, sub);
				channelManager.refreshChannel(cn);
			} else {
				// update
				if (Math.abs(sub.getPeriod()-period)>MIN_PERIOD_CHANGE) {
					Log.d(TAG,"Update period for "+cn+" (update)");
					sub.updateConfiguration(period, period/2, 0);
					channelManager.refreshChannel(cn);
				}
			}
		}
		// remove?
		for (String cn : ecns) {
			Log.d(TAG,"Stop logging "+cn+" (update)");
			LogSubscription sub = subscriptions.remove(cn);
			if (sub!=null) 
				channelManager.removeSubscription(sub);
			channelManager.refreshChannel(cn);
		}
	}

	private synchronized void stop() {
		closeCurrentFile();
		for (Map.Entry<String,LogSubscription> e : subscriptions.entrySet()) {
			Log.d(TAG,"Stop logging "+e.getKey()+" (stop all)");
			channelManager.removeSubscription(e.getValue());
			channelManager.refreshChannel(e.getKey());
		}
		subscriptions.clear();
	}

	private synchronized void start() {
		float period = getLogPeriod();
		String cns[] = getChannels();
		for (int i=0; i<cns.length; i++) {
			String cn = cns[i];
			if (cn.length()==0)
				continue;
			Log.d(TAG,"Start logging "+cn+" (start all)");
			LogSubscription sub = new LogSubscription(this, cn);
			sub.updateConfiguration(period, period/2, 0);
			channelManager.addSubscription(sub);
			subscriptions.put(cn, sub);
			channelManager.refreshChannel(cn);
		}
	}

	// called from LogSubscription
	public synchronized void logValue(String channelName, JSONObject value) {
		if (!logging) {
			Log.d(TAG,"ignore logValue (not logging) "+channelName+"="+value);
			return;
		}
		Log.d(TAG,"logValue "+channelName+"="+value);

		// marshall
		long time = System.currentTimeMillis();
		JSONObject lval = new JSONObject();
		byte data[];
		try {
			lval.put("time", time);
			lval.put("name", channelName);
			lval.put("value", value);
			data = lval.toString().getBytes("UTF-8");
		} catch (Exception e) {
			Log.e(TAG,"marshalling log value: "+e);
			return;
		}		
		
		try {
			// quick write?
			if (writeValue(data))
				return;
		}
		catch (Exception e) {
			Log.w(TAG,"problem writing value: "+e);
		}
		// ensure no open file
		closeCurrentFile();

		// fix up...
		File dir = getLogDirectory(service);
		if (dir==null || !dir.exists() || !dir.canWrite()) {
			signalError("External storage not accessible/present");
			return;
		}
		if (currentLogDir==null && !dir.equals(currentLogDir)) {
			// initialise log dir, i.e. current size
			currentLogDir = dir;
			currentLogDir.mkdirs();
			try {
				cacheFiles = new ArrayList<File>();
				File files[] = currentLogDir.listFiles();
				for (int fi=0; fi<files.length; fi++) 
					cacheFiles.add(files[fi]);
				currentCacheUsage = 0;
				for (File f : cacheFiles)
					currentCacheUsage += f.length();
				Collections.sort(cacheFiles, new Comparator<File>() {
					public int compare(File f1, File f2) {
						return new Long(f1.lastModified()).compareTo(f2.lastModified());
					}
				});
			}
			catch (Exception e) {
				signalError("External storage not accessible/present (on check cache size)");
				return;
			}
		}
		// current log configuration
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(service);

		// is there space 
		StatFs fs = new StatFs(currentLogDir.getAbsolutePath());
		long availableFs = fs.getAvailableBlocks()*fs.getBlockSize();
		long availableCache = (maxCacheSize>0) ? maxCacheSize-currentCacheUsage : availableFs;
		
		if (availableCache < MIN_NEW_FILE_SIZE) {
			boolean deleteOldFiles = prefs.getBoolean(LOG_DELETE_OLD_FILES, false);
			if (!deleteOldFiles) {
				if (availableCache<availableFs)
					signalError("Log cache full (cannot delete old files)");
				else
					signalError("Log storage device full (cannot delete old files)");
				return;
			}
			// try deleting oldest file first
			while (cacheFiles.size()>0 && availableCache < MIN_NEW_FILE_SIZE) {
				File f = cacheFiles.remove(0);
				Log.i(TAG,"Deleting old cache file "+f+" (modified "+logDateFormat.format(new Date(f.lastModified()))+", length "+f.length()+")");

				long len = f.length();
				currentCacheUsage -= len;
				availableCache += len;
				if (!f.delete()) {
					signalError("Log cache full (failed to delete old file(s))");
					return;
				}
			}
			if (availableCache < MIN_NEW_FILE_SIZE) {
				signalError("Log cache full (no old files)");
				return;
			}
		}
		
		// new log file
		String filePrefix = prefs.getString(LOG_FILE_PREFIX, "log");
		
		int i=1;
		String filename = filePrefix+"_"+logDateFormat.format(new Date(time));
		currentLogFile = new File(currentLogDir, filename+".log");
		while (currentLogFile.exists()) {
			i++;
			currentLogFile = new File(currentLogDir, filename+"_"+i+".log");
		}
		
		try {
			OutputStream os = new FileOutputStream(currentLogFile);
			currentLogStream = new BufferedOutputStream(os, BUFFER_SIZE);
			cacheFiles.add(currentLogFile);
			currentFileLength = 0;
		}
		catch (Exception e) {
			Log.w(TAG,"Error opening log file "+currentLogFile+": "+e);
			signalError("Cannot create log file");
			closeCurrentFile();
			return;
		}
		
		// try again!
		try {
			// quick write?
			if (writeValue(data))
				return;
		}
		catch (Exception e) {
			Log.w(TAG,"problem writing value: "+e);
		}
		signalError("Cannot write to new log file");
		return;
	}

	private static SimpleDateFormat logDateFormat = new SimpleDateFormat("yyyy-MM-dd_HHmmss-SSS");
	
	private void signalError(String error) {
		// force re-check...
		currentLogDir = null;
		if (errorNotificationVisible && error.equals(errorNotificationText))
			return;
		errorNotificationText = error;
		
		int icon = R.drawable.log_error_notification_icon;
		CharSequence tickerText = error;
		long when = System.currentTimeMillis();

		Notification notification = new Notification(icon, tickerText, when);
		
		Context context = service;
		CharSequence contentTitle = "Ubihelper Logging";
		CharSequence contentText = error;
		Intent notificationIntent = new Intent(LoggingPreferences.INTENT);
		PendingIntent contentIntent = PendingIntent.getActivity(service, 0, notificationIntent, 0);

		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
		NotificationManager mNotificationManager = (NotificationManager) service.getSystemService(Service.NOTIFICATION_SERVICE);
		//service.(ci.notificationId, notification);	
		mNotificationManager.notify(ERROR_NOTIFICATION_ID, notification);
		errorNotificationVisible = true;
	}

	private static byte nl[] = new byte[] { (byte)'\n' };
	private synchronized boolean writeValue(byte[] data) throws IOException {
		if (currentLogStream==null)
			return false;

		int size = data.length+nl.length;
		if ((maxFileSize>0 && currentFileLength>=maxFileSize) ||
				(maxCacheSize>0 && currentFileLength+size+currentCacheUsage >= maxCacheSize)) {
			closeCurrentFile();
			return false;
		}
		
		currentLogStream.write(data);
		currentLogStream.write(nl);
		currentFileLength += size;
		
		if (!flushPosted) {
			service.postTaskDelayed(flushTask, DEFAULT_FLUSH_DELAY);
			flushPosted = true;
		}
		
		clearError();
		
		if (maxFileSize>0 && currentFileLength>=maxFileSize)
			// fast close
			closeCurrentFile();
		
		return true;
	}
	private Runnable flushTask = new Runnable() {
		public void run() {
			flushPosted = false;
			flush();
		}
	};
	private void clearError() {
		if (!errorNotificationVisible)
			return;
		NotificationManager mNotificationManager = (NotificationManager) service.getSystemService(Service.NOTIFICATION_SERVICE);
		mNotificationManager.cancel(ERROR_NOTIFICATION_ID);		
		errorNotificationVisible = false;
	}
	private synchronized void flush() {
		if (currentLogStream!=null) {
			try {
				currentLogStream.flush();
			}
			catch (Exception e) {
				Log.w(TAG,"Error flushing current stream: "+e);
				closeCurrentFile();
				signalError("Cannot persist values to log file");
			}
		}
	}
}
