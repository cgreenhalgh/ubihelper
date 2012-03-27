/**
 * 
 */
package uk.ac.horizon.ubihelper.service.channel;

import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONException;
import org.json.JSONObject;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioRecord.OnRecordPositionUpdateListener;
import android.media.MediaRecorder;
import android.os.Handler;
import android.util.Log;
import uk.ac.horizon.ubihelper.channel.NamedChannel;
import uk.ac.horizon.ubihelper.service.Service;

/** microphone input.
 * Note: there is a known issue with some phones including the galaxy s i have at the moment
 * that AudioRecord.read blocks. Consequently this code uses a separate thread (a Timer)
 * to do the actual Audio I/O.
 * Also the latency on Android is generally really bad, but that is another issue. 
 * 
 * @author cmg
 *
 */
public class MicChannel extends NamedChannel {
	static final String TAG = "ubihelper-mic";
	static final int SAMPLE_RATE = 8000;//44100;
	static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
	static final int AUDIO_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
	static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
	static final int BUFFER_SAMPLES = SAMPLE_RATE; // 1s
	private short [] samples;
	private MicTimer timer;
	private Handler handler;
	private int notificationSample;
	private class MicTimer extends Timer implements OnRecordPositionUpdateListener  {
		private AudioRecord audio;
		private boolean recording;
		boolean closed;
		public MicTimer() {			
		}
		synchronized void close() {
			handleStop();
			if (audio!=null) {
				audio.release();
				audio = null;
			}
			closed = true;
			cancel();
		}
		synchronized void handleStart() {
			if (audio==null) {
				int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AUDIO_CHANNELS, AUDIO_FORMAT);
				int defaultBuffer = BUFFER_SAMPLES*(AUDIO_FORMAT==AudioFormat.ENCODING_PCM_16BIT ? 2 : 1)*(AUDIO_CHANNELS==AudioFormat.CHANNEL_IN_STEREO ? 2 : 1);
				if (defaultBuffer < minBuffer)
					defaultBuffer = minBuffer;
				samples = new short[defaultBuffer / (AUDIO_FORMAT==AudioFormat.ENCODING_PCM_16BIT ? 2 : 1)];
				try {
					audio = new AudioRecord(AUDIO_SOURCE, SAMPLE_RATE, AUDIO_CHANNELS, AUDIO_FORMAT, defaultBuffer);
					Log.d(TAG,"Created AudioRecord");
					if (audio.getState()!=AudioRecord.STATE_INITIALIZED) 
						Log.e(TAG,"Audio state is NOT initialised ("+audio.getState()+")");
				}
				catch (Exception e) {
					Log.e(TAG,"Error opening AudioRecord: "+e);
					return;
				}
			}
			try {
				notificationSample = (int)(SAMPLE_RATE*period);
				if (notificationSample > BUFFER_SAMPLES/2)
					notificationSample = BUFFER_SAMPLES/2;
				//audio.setNotificationMarkerPosition(notificationSample);
				//audio.setPositionNotificationPeriod(notificationSample);
				audio.setRecordPositionUpdateListener(this);
				audio.startRecording();
				Log.d(TAG,"Started record (notify at "+notificationSample+")");
			}
			catch (Exception e) {
				Log.e(TAG,"Error starting recording: "+e);
				return;
			}
			recording = true;
			
			readAudioTask = new ReadAudioTask();
			scheduleAtFixedRate(readAudioTask, 0, (long)(period*1000));
		}
		synchronized void handleStop() {
			if (readAudioTask!=null) {
				readAudioTask.cancel();
				readAudioTask = null;
			}
			if (recording) {
				recording = false;
				try {
					audio.stop();
					Log.d(TAG,"Stopped recording");
				}
				catch (Exception e) {
					Log.e(TAG,"stopping AudioRecord: "+e);
				}
			}
		}
		public void onMarkerReached(AudioRecord arg0) {
			Log.d(TAG,"onMarkerReached");
			readAudio(audio.getNotificationMarkerPosition());
		}
		public void onPeriodicNotification(AudioRecord arg0) {
			Log.d(TAG,"onPeriodicNotification");
			readAudio(audio.getPositionNotificationPeriod());
		}
		private TimerTask readAudioTask;
		class ReadAudioTask extends TimerTask {
			public void run() {
				readAudio(notificationSample);
			}			
		};
		//static int STEP = 4;
		private void readAudio(int max) {
			if (max<=0)
				max = samples.length;
			long t1 = System.currentTimeMillis();
			int cnt = audio.read(samples, 0, max);
//			int bsize = max*(AUDIO_FORMAT==AudioFormat.ENCODING_PCM_16BIT ? 2 : 1)*(AUDIO_CHANNELS==AudioFormat.CHANNEL_IN_STEREO ? 2 : 1);
//			byte buf[] = new byte[bsize];
//			int cnt = audio.read(buf, 0, bsize);
			//ByteBuffer buffer = ByteBuffer.allocateDirect(bsize);
			//int cnt = audio.read(buffer, bsize);
			if (cnt<=0) {
				Log.w(TAG,"Read "+cnt+" samples from audio");
				return;
			}
			long t2 = System.currentTimeMillis();
			Log.d(TAG,"Read "+cnt+" audio samples, "+(t2-t1)+"ms");
			// mean
			float sum = 0, sumsq = 0;
			float absmax = 0;
			for (int i=0; i<cnt; i++) {
				float v = (float)samples[i];
				sum += v;
				sumsq += v*v;
				if (v>absmax)
					absmax = v;
				else if (-v>absmax)
					absmax = -v;
			}
			//cnt /= STEP;
			float ave = sum/cnt;
			float avesq = sumsq/cnt;
			float ms = avesq-ave*ave;
			// max is MAX_SHORT*MAX_SHORT = 1<<30
			float rmsdb = (float)(10*Math.log10(ms/(Short.MAX_VALUE*Short.MAX_VALUE))/2);
			JSONObject value = new JSONObject();
			try {
				// RMS level, average over sample block, no window, in dB (ish)
				value.put("rmsdb", rmsdb);
				//value.put("ms", ms);
				value.put("samples", cnt);
				// Max (abs) value of any single sample, normalised to supported range = 1
				value.put("max", absmax/Short.MAX_VALUE);
			} catch (JSONException e) {
				// shouldn't
			}
			MicChannel.this.onNewValue(value);
	    }
	}
	/**
	 * @param name
	 */
	public MicChannel(Handler handler, String name) {
		super(name);
		this.handler = handler;
		timer = new MicTimer();
	}
	@Override
	public synchronized void close() {
		handleStop();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				timer.close();
			}		
		}, 0);
	}
	@Override
	protected synchronized void handleStart() {
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				timer.handleStart();
			}		
		}, 0);	
	}
	@Override
	protected synchronized void handleStop() {
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				timer.handleStop();
			}		
		}, 0);	
	}
}
