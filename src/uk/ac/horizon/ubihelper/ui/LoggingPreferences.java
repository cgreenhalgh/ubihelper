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

package uk.ac.horizon.ubihelper.ui;

import uk.ac.horizon.ubihelper.R;
import uk.ac.horizon.ubihelper.service.LogManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.text.InputType;
import android.util.Log;

/**
 * @author cmg
 *
 */
public class LoggingPreferences extends PreferenceActivity {
	//public static final String LOG_DIRECTORY = "log_directory";
	protected static final String TAG = "ubihelper-logprefs";
	private EditTextPreference logPeriod;
	private EditTextPreference logFilePrefix;
	private EditTextPreference logMaxFileSize;
	private EditTextPreference logMaxCacheSize;
	//private Preference logDirectory;
	
	public static final String INTENT = "uk.ac.horizon.ubihelper.CONFIGURE_LOGGING";
	
	/* (non-Javadoc)
	 * @see android.preference.PreferenceActivity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.loggingpreferences);        
        logPeriod = (EditTextPreference)getPreferenceScreen().findPreference(LogManager.LOG_PERIOD);
        logPeriod.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        updateLogPeriodSummary();
        logFilePrefix = (EditTextPreference)getPreferenceScreen().findPreference(LogManager.LOG_FILE_PREFIX);
        updateLogFilePrefixSummary();
        logMaxFileSize = (EditTextPreference)getPreferenceScreen().findPreference(LogManager.LOG_MAX_FILE_SIZE);
        logMaxFileSize.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        updateLogMaxFileSizeSummary();
        logMaxCacheSize = (EditTextPreference)getPreferenceScreen().findPreference(LogManager.LOG_MAX_CACHE_SIZE);
        logMaxCacheSize.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        updateLogMaxCacheSizeSummary();
        //logDirectory = getPreferenceScreen().findPreference(LOG_DIRECTORY);
        //updateLogDirectory();
        Preference logChannels = getPreferenceScreen().findPreference(LogManager.LOG_CHANNELS);
        logChannels.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference p) {
				Intent i = LoggingChannelListActivity.getStartActivityIntent(LoggingPreferences.this);
				startActivity(i);
				return true;
			}
        });
        
	}

//	private void updateLogDirectory() {
//		File dir = LogManager.getLogDirectory(this);
//		if (dir!=null)
//			logDirectory.setSummary(dir.toString());
//		else
//			logDirectory.setSummary("Unknown (external storage may not be available)");
//	}

	private void updateLogPeriodSummary() {
		logPeriod.setSummary(logPeriod.getText()+" seconds");
	}
	private void updateLogFilePrefixSummary() {
		logFilePrefix.setSummary(logFilePrefix.getText());
	}
	private void updateLogMaxFileSizeSummary() {
		logMaxFileSize.setSummary(logMaxFileSize.getText()+" byes");
	}
	private void updateLogMaxCacheSizeSummary() {
		logMaxCacheSize.setSummary(logMaxCacheSize.getText()+" byes");
	}

	/** preference change listener */
	private final OnSharedPreferenceChangeListener onRunChangeListener = 
			new OnSharedPreferenceChangeListener() {

				public void onSharedPreferenceChanged(SharedPreferences prefs,
						String key) {
					Log.d(TAG,"Preference changed: "+key);
					if (LogManager.LOG_PERIOD.equals(key))
						updateLogPeriodSummary();
					else if (LogManager.LOG_FILE_PREFIX.equals(key))
						updateLogFilePrefixSummary();
					else if (LogManager.LOG_MAX_FILE_SIZE.equals(key))
						updateLogMaxFileSizeSummary();
					else if (LogManager.LOG_MAX_CACHE_SIZE.equals(key))
						updateLogMaxCacheSizeSummary();
				}
	};
	
	@Override
	protected void onStart() {
		super.onStart();
		SharedPreferences prefs = this.getPreferenceManager().getSharedPreferences();
		prefs.registerOnSharedPreferenceChangeListener(onRunChangeListener);		
	}

	@Override
	protected void onStop() {
		super.onStop();
		SharedPreferences prefs = this.getPreferenceManager().getSharedPreferences();
		prefs.unregisterOnSharedPreferenceChangeListener(onRunChangeListener);
	}

}
