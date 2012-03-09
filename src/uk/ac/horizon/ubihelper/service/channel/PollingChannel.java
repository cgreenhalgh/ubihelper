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

import android.os.Handler;
import uk.ac.horizon.ubihelper.channel.NamedChannel;

/** Polling channel, Android-specific (uses Handler).
 * 
 * @author cmg
 *
 */
public abstract class PollingChannel extends NamedChannel {
	private static final long MIN_POLL_INTERVAL = 20;
	private Handler handler;
	protected boolean pollInProgress;
	private long pollStartTime;
	private long pollInterval;
	/**
	 * @param name
	 */
	public PollingChannel(Handler handler, String name) {
		super(name);
		this.handler = handler;
	}
	@Override
	protected synchronized void handleStart() {
		pollInterval = (long)(1000*this.period);
		if (pollInterval < MIN_POLL_INTERVAL)
			pollInterval = MIN_POLL_INTERVAL;
		if (!pollInProgress) {
			handler.post(pollTask);
		}
	}
	private Runnable pollTask  = new Runnable() {
		public void run() {
			synchronized (PollingChannel.this) {
				if (!pollInProgress) {
					pollStartTime = System.currentTimeMillis();
					pollInProgress = startPoll();
				}
				if (active && !pollInProgress)
					handler.postDelayed(pollTask, pollInterval);
			}
		}
	};
	/** call pollComplete when done; return true if successfully started */
	protected abstract boolean startPoll();
	protected synchronized void pollComplete() {
		if (!pollInProgress)
			return;
		pollInProgress = false;
		handler.removeCallbacks(pollTask);
		if (active) {
			if (pollStartTime==0)
				handler.postDelayed(pollTask, pollInterval);
			else {
				long elapsed = System.currentTimeMillis()-pollStartTime;
				if (pollInterval <= elapsed)
					// immediate
					handler.post(pollTask);
				else
					handler.postDelayed(pollTask, (pollInterval-elapsed));
			}
		}
	}
	@Override
	protected synchronized void handleStop() {
		handler.removeCallbacks(pollTask);
	}

}
