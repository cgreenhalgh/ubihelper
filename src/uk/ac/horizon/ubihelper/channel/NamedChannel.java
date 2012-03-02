/**
 * 
 */
package uk.ac.horizon.ubihelper.channel;

import java.util.logging.Logger;

import org.json.JSONObject;

/** Named "channel" for values.
 * 
 * @author cmg
 *
 */
public class NamedChannel {
	static Logger logger = Logger.getLogger(NamedChannel.class.getName());
	protected String name;
	protected double period = 1;
	protected boolean active = false;
	private ChannelListener listener;
	/** cons */
	public NamedChannel(String name) {
		this.name = name;
	}
	/** update metadata */
	public final synchronized void start(double period) {
		if (this.period!=period) {
			stop();
		}
		this.period = period;
		if (!this.active) { 
			this.active = true;
			handleStart();
		}
	}
	public final synchronized void stop() {
		if (!this.active)
			return;
		this.active = false;
		handleStop();
	}
	public synchronized JSONObject getImmediateValue() {
		return null;
	}
	protected void handleStart() {}
	protected void handleStop() {}
	/**
	 * @return the name
	 */
	public final String getName() {
		return name;
	}
	/** from ChannelManager */
	synchronized void init(ChannelListener listener) {
		this.listener = listener;
	}
	protected final void onNewValue(JSONObject value) {
		ChannelListener l =  null;
		synchronized (this) {
			l = listener;
		}
		if (l!=null) {
			try {
				l.onNewValue(new ChannelValueEvent(this, value));
			}
			catch (Exception e) {
				logger.warning("Error on onNewValue listener: "+e);
				e.printStackTrace();
			}
		}
	}
}
