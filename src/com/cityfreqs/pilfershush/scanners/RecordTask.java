package com.cityfreqs.pilfershush.scanners;

import java.util.ArrayList;
import java.util.HashMap;

import com.cityfreqs.pilfershush.MainActivity;
import com.cityfreqs.pilfershush.assist.AudioSettings;
import com.cityfreqs.pilfershush.scanners.FreqDetector.RecordTaskListener;

import android.annotation.SuppressLint;
import android.media.AudioRecord;
import android.os.AsyncTask;
import android.util.Log;

public class RecordTask extends AsyncTask<Void, Integer, String> {
	private static final String TAG = "RecordTask";
	
	private short[] bufferArray;
	private double[] recordScan;
	private RecordTaskListener recordTaskListener;		
	private AudioRecord audioRecord;
	private AudioSettings audioSettings;
	private int bufferRead;
	private int freqStepper;
	private int candidateFreq;
	private double minMagnitude;
	private double[] scanArray;
	private byte[] byteBuffer;
	private ArrayList<short[]> bufferStorage;
	private HashMap<Integer, Integer> freqMap;
	
	public RecordTask(AudioSettings audioSettings, int freqStepper, double magnitude) {	
		this.audioSettings = audioSettings;
		this.freqStepper = freqStepper;
		minMagnitude = magnitude;
		bufferArray = new short[audioSettings.getBufferSize()];
		bufferStorage = new ArrayList<short[]>();
		
		if (audioRecord == null) {
			try {
				audioRecord = new AudioRecord(audioSettings.getAudioSource(), 
						audioSettings.getSampleRate(), 
						audioSettings.getChannel(), 
						audioSettings.getEncoding(), 
						audioSettings.getBufferSize());
				
				logger("RecordTask ready.");
			}
			catch (Exception ex) {
				ex.printStackTrace();
				logger("RecordTask failed.");
			}
		}
	}
	
	public void setOnResultsListener(RecordTaskListener recordTaskListener) {
		this.recordTaskListener = recordTaskListener;
	}
	
	public boolean runCurrentBufferScan(ArrayList<Integer> freqList) {
		// get rid of audioRecord
		if (audioRecord != null) {
			audioRecord = null;
		}
		if (bufferStorage != null) {
			activityLogger("run Buffer Scan...");
			 return magnitudeBufferScan(AudioSettings.DEFAULT_WINDOW_TYPE, freqList);
		}
		else {
			activityLogger("Buffer Scan storage null.");
			return false;
		}		
	}
	
/********************************************************************/	
	
	protected boolean hasBufferStorage() {
		if (bufferStorage != null) {
			return !bufferStorage.isEmpty();
		}
		return false;
	}
	
	protected ArrayList<short[]> getBufferStorage() {
		return bufferStorage;
	}	
	
	protected boolean hasFrequencyCountMap() {
		if (freqMap != null) {
			return freqMap.size() > 0;
		}
		return false;
	}
	
	protected int getFrequencyCountMapSize() {
		if (freqMap != null) {
			return freqMap.size();
		}
		return 0; 
	}
	
	protected HashMap<Integer, Integer> getFrequencyCountMap() {
		return freqMap;
	}
	
	protected double getMinMagnitude() {
		return minMagnitude;
	}
	
	protected void setMinMagnitude(double magnitude) {
		// checks here?
		minMagnitude = magnitude;
	}

	
/********************************************************************/	
	
	@Override
	protected void onPreExecute() {
		super.onPreExecute();
	}
	
	@Override
	protected void onProgressUpdate(Integer... paramArgs) {
		if (recordTaskListener == null) {
			logger("onProgress listener null.");
			return;
		}

		if (paramArgs[0] != null) {
			recordTaskListener.onSuccess(paramArgs[0].intValue());
		}
		else {
			recordTaskListener.onFailure("RecordTaskListener failed, no params.");
			logger("listener onFailure.");
		}
	}
	
	@Override
	protected String doInBackground(Void... paramArgs) {	
		if (isCancelled()) {
			// check
			logger("isCancelled check");
			return "isCancelled()";
		}
		// check audioRecord object first
		if ((audioRecord != null) || (audioRecord.getState() == AudioRecord.STATE_INITIALIZED)) {
			try {
				audioRecord.startRecording();
				logger("audioRecord started...");
				audioRecord.setPositionNotificationPeriod(audioSettings.getBufferSize() / 2);
				audioRecord.setRecordPositionUpdateListener(new AudioRecord.OnRecordPositionUpdateListener() {
					public void onMarkerReached(AudioRecord audioRecord) {
						logger("marker reached");
					}
						
					public void onPeriodicNotification(AudioRecord audioRecord) {
						magnitudeRecordScan(AudioSettings.DEFAULT_WINDOW_TYPE);
						MainActivity.visualiserView.updateVisualiser(byteBuffer);
					}
				});
				// bufferArray IS short[], NOT byte[]
				do {
					bufferRead = audioRecord.read(bufferArray, 0, audioSettings.getBufferSize());
				} while (!isCancelled());
			}
			catch (IllegalStateException exState) {
				exState.printStackTrace();
				logger("AudioRecord start recording failed.");
			}
		}
		return "RecordTask finished";
	}
	
	@Override
	protected void onPostExecute(String paramString) {
		logger("Post execute: " + paramString);
	}
	
	@Override
	protected void onCancelled() {
		logger("onCancelled called.");
		bufferRead = 0;
		if (audioRecord != null) {
			audioRecord.stop();
			audioRecord.release();
			logger("audioRecord stop and release.");
		}
		else {
			logger("audioRecord is null.");
		}
	}
	
/********************************************************************/	

	private void magnitudeRecordScan(int windowType) {	
		if (bufferRead > 0) {
			recordScan = new double[audioSettings.getBufferSize()];
			byteBuffer = new byte[audioSettings.getBufferSize()];
			
			for (int i = 0; i < recordScan.length; i++) {
				recordScan[i] = (double)bufferArray[i];
				byteBuffer[i] = (byte)bufferArray[i];
			}

			recordScan = windowArray(windowType, recordScan);				
			candidateFreq = AudioSettings.DEFAULT_FREQUENCY_MIN;
			Goertzel goertzel;	
			double candidateMag;
			
			while (candidateFreq <= AudioSettings.DEFAULT_FREQUENCY_MAX) {	
				goertzel = new Goertzel((float)audioSettings.getSampleRate(), (float)candidateFreq, recordScan);
				goertzel.initGoertzel();
				candidateMag = goertzel.getOptimisedMagnitude();
				
				if (candidateMag >= minMagnitude) {
					publishProgress(new Integer[]{Integer.valueOf(candidateFreq)});
					// store containing array for later magnitudeBufferScan
					bufferStorage.add(bufferArray);
				}								
				candidateFreq += freqStepper;
			}				
		}
		else {
			logger("bufferRead empty");
		}
	}
	
	@SuppressLint("UseSparseArrays")
	private boolean magnitudeBufferScan(int windowType, ArrayList<Integer> freqList) {	
		if ((freqList == null) || (freqList.isEmpty())) {
			activityLogger("Buffer Scan list empty.");
			return false;
		}
		
		if (bufferStorage != null) {
			activityLogger("Start buffer scanning in " + bufferStorage.size() + " buffers.");
			// each Integer array *may* contain a binMod signal
			freqMap = new HashMap<Integer, Integer>();
			int freq;
			double candidateMag;
			ArrayList<Integer> freqCounter;
			Goertzel goertzel;
			
			//TODO
			// may want a maximum on this cos it could get big and ugly...			
			for (short[] arrayShort : bufferStorage) {
				scanArray = new double[arrayShort.length];
				for (int i = 0; i < scanArray.length; i++) {
					scanArray[i] = (double)arrayShort[i];					
				}
				scanArray = windowArray(windowType, scanArray);
								
				for (int checkFreq : freqList) {
					freq = 0;
					freqCounter = new ArrayList<Integer>();
					
					// range here may be too small/large...
					for (freq = checkFreq - AudioSettings.MAX_FREQ_STEP; 
							freq <= checkFreq + AudioSettings.MAX_FREQ_STEP; 
							freq += AudioSettings.FREQ_STEP_25) {

						goertzel = new Goertzel((float)audioSettings.getSampleRate(), (float)freq, scanArray);
						goertzel.initGoertzel();

						// this can result in a dupe, 
						// we need not just the highest above minMag but a narrow range?
						candidateMag = goertzel.getOptimisedMagnitude();
						if (candidateMag >= minMagnitude) {
							freqCounter.add(freq);
						}
					}
					if (!freqCounter.isEmpty()) {
						mapFrequencyCounts(freqCounter);
					}
				}
			}
			// end bufferStorage loop thru
		}
		activityLogger("finished Buffer Scan loop with " + freqMap.size() + " entries.");
		return true;
	}

/********************************************************************/	
	
	private double[] windowArray(int windowType, double[] dArr) {
		int i;
		// default value set to 2
		switch (windowType) {
			case 1:
				// Hann(ing) Window
				for (i = 0; i < dArr.length; i++) {
                    dArr[i] = dArr[i] * (0.5d - (0.5d * Math.cos((AudioSettings.PI2 * ((double) i)) / dArr.length)));
                }
                break;
			case 2:
				// Blackman Window
				for (i = 0; i < dArr.length; i++) {
                    dArr[i] = dArr[i] * ((0.42659d - (0.49659d * Math.cos((AudioSettings.PI2 * 
                    		((double) i)) / dArr.length))) + (0.076849d * Math.cos((AudioSettings.PI4 * ((double) i)) / dArr.length)));
                }
                break;
			case 3:
				// Hamming Window
				for (i = 0; i < dArr.length; i++) {
                    dArr[i] = dArr[i] * (0.54d - (0.46d * Math.cos((AudioSettings.PI2 * ((double) i)) / dArr.length)));
                }
                break;
			case 4:
				// Nuttall Window
				for (i = 0; i < dArr.length; i++) {
                    dArr[i] = dArr[i] * (((0.355768d - (0.487396d * Math.cos((AudioSettings.PI2 * ((double) i)) / dArr.length))) + 
                    		(0.144232d * Math.cos((AudioSettings.PI4 * ((double) i)) / dArr.length))) - 
                    		(0.012604d * Math.cos((AudioSettings.PI6 * ((double) i)) / dArr.length)));
                }
                break;
		}
		return dArr;
	}
	
	
	private void mapFrequencyCounts(ArrayList<Integer> freqList) {
		// SparseIntArray is suggested...		
		// this only counts, order of occurrence is not preserved.		
		for (int freq : freqList) {
            if (freqMap.containsKey(freq)) {
                freqMap.put(freq, freqMap.get(freq) + 1);
            } 
            else {
                freqMap.put(freq, 1);
            }
        }
	}
	
/********************************************************************/	
	
	private void activityLogger(String message) {
		MainActivity.logger(message);
	}
	
	private void logger(String message) {
		Log.d(TAG, message);
	}		
}

