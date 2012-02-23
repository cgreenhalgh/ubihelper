/**
 * 
 */
package uk.ac.horizon.ubihelper.net;

import java.util.LinkedList;
import java.util.Queue;

/** To convert between a Message and one or more Fragments.
 * 
 * @author cmg
 *
 */
public class Marshaller {
	private enum Stage { STAGE_START, STAGE_FIRSTLINE, STAGE_HEADERLINES, STAGE_BODY, STAGE_DONE };
	// TODO
	public static Queue<Fragment> fragment(Message m) {
		Queue<Fragment> fs = new LinkedList<Fragment>();
		Stage stage = Stage.STAGE_START;		
		int pos = 0;
		int header = 0;
		while (stage!=Stage.STAGE_DONE) {
			switch(stage) {
			case STAGE_START:
			case STAGE_FIRSTLINE:
				
			}
		}
		return fs;
	}
}
