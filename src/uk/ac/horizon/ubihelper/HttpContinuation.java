/**
 * 
 */
package uk.ac.horizon.ubihelper;

/**
 * @author cmg
 *
 */
public interface HttpContinuation {
	void done(int status, String message, String body);
}
