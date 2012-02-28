/**
 * 
 */
package uk.ac.horizon.ubihelper.httpserver;

/**
 * @author cmg
 *
 */
public interface HttpContinuation {
	void done(int status, String message, String body);
}
