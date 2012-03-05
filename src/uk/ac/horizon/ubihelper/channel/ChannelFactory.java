/**
 * 
 */
package uk.ac.horizon.ubihelper.channel;

/**
 * @author cmg
 *
 */
public interface ChannelFactory {
	/** created channel, or null */
	public NamedChannel createChannel(String name);
}
