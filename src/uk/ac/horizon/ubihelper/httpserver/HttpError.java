/**
 * 
 */
package uk.ac.horizon.ubihelper.httpserver;

/**
 * @author cmg
 *
 */
public class HttpError extends Exception {
	private int status;
	private String message;
	/**
	 * @param status
	 * @param message
	 */
	public HttpError(int status, String message) {
		super(message+" ("+status+")");
		this.status = status;
		this.message = message;
	}
	/**
	 * @return the status
	 */
	public int getStatus() {
		return status;
	}
	/**
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}
	public static HttpError badRequest(String msg) {
		return new HttpError(300, msg);
	}
	
}
