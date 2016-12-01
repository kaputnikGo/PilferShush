package com.cityfreqs.pilfershush;


import com.cityfreqs.pilfershush.assist.AudioSettings;

import android.media.AudioFormat;
//import android.media.AudioManager;
import android.media.AudioRecord;
//import android.media.AudioTrack;
import android.media.MediaRecorder.AudioSource;

public class AudioChecker {	
	private int sampleRate;
	private int bufferSize;
	private int encoding;
	private int channel;
	private int audioSource = AudioSource.DEFAULT;
	
	private AudioRecord audioRecord;
	private PollAudioChecker pollAudioChecker;
	private int userPollSpeed;
	
	private AudioSettings audioSettings;
	
	
	public AudioChecker() {
		//
		userPollSpeed = AudioSettings.LONG_DELAY;
		audioSettings = new AudioSettings();
	}
	
	protected void destroy() {
		stopAllAudio();
		if (audioRecord != null) {
			audioRecord = null;
		}
		if (pollAudioChecker != null) {
			pollAudioChecker.destroy();
		}
	}
	
/********************************************************************/
/*
 * 
 */	
	protected boolean determineInternalAudioType() {
		// guaranteed default for Android is 44.1kHz, PCM_16BIT, CHANNEL_IN_MONO
		int minBuffSize = 0;
		int buffSize = 0;
		for (int rate : AudioSettings.SAMPLE_RATES) {
	        for (short audioFormat : new short[] { 
	        		AudioFormat.ENCODING_PCM_16BIT,
	        		AudioFormat.ENCODING_PCM_8BIT }) {
	        	
	            for (short channelConfig : new short[] { 
	            		AudioFormat.CHANNEL_IN_DEFAULT,
	            		AudioFormat.CHANNEL_IN_MONO, 
	            		AudioFormat.CHANNEL_IN_STEREO }) {
	                try {
	                    MainActivity.logger("Try rate " + rate + "Hz, enc: " + audioFormat + ", channel: "+ channelConfig);
	                    
	                    // get a better sized buffer for recording?: powersTwo > minBuff
	                    minBuffSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);
	                    buffSize = AudioSettings.getClosestPowersHigh(minBuffSize);
	                    
	                    if (buffSize != AudioRecord.ERROR_BAD_VALUE) {
	                        // check if we can instantiate and have a success
	                        AudioRecord recorder = new AudioRecord(
	                        		AudioSource.DEFAULT, 
	                        		rate, 
	                        		channelConfig, 
	                        		audioFormat, 
	                        		buffSize);

	                        if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
	                        	MainActivity.logger("found, rate: " + rate + ", minBuff: " 
	                        			+ minBuffSize + ", used buffer: " + buffSize);
	                        	
	                        	// set our values
	                        	sampleRate = rate;
	                        	channel = channelConfig;
	                        	encoding = audioFormat;
	                        	bufferSize = buffSize;
	                        	audioSettings.setBasicAudioSettings(sampleRate, bufferSize, encoding, channel);
	                        	recorder.release();
	                        	recorder = null;	                        	
	                            return true;
	                        }
	                    }
	                } 
	                catch (Exception e) {
	                	MainActivity.logger("Rate: " + rate + "Exception, keep trying, e:" + e.toString());
	                }
	            }
	        }
	    }
		MainActivity.logger("determine internal audio failure.");
	    return false;
	}
	
	protected AudioSettings getAudioSettings() {
		return audioSettings;
	}	
	
	protected boolean checkAudioRecord() {
		// return if can start new audioRecord object		
		boolean recordable = false;
		if (audioRecord == null) {
			try {
				audioRecord = new AudioRecord(audioSource, sampleRate, channel, encoding, bufferSize);				
				//audioSessionId = audioRecord.getAudioSessionId();				
				MainActivity.logger("Can start Microphone Check.");
			}
			catch (Exception ex) {
				ex.printStackTrace();
				MainActivity.logger("Microphone in use...");
				recordable = false;
			}
			finally {
		    	try {
		    		audioRecord.release();
		    		recordable = true;
		        }
		        catch(Exception e){
		        	recordable = false;
		        }
			}
		}
		else {
			recordable = true;
		}
		return recordable;
	}
	
	// TODO
	// can check for AUDIOFOCUS ?
	
/********************************************************************/	
/*
 * 	
 */
	protected void checkAudioBufferState() {
	    try {
	        audioRecord = new AudioRecord(AudioSource.DEFAULT, sampleRate, channel, encoding, bufferSize );
	        // need to start reading buffer to trigger an exception
	        audioRecord.startRecording();
	        short buffer[] = new short[bufferSize];
	        int audioStatus = audioRecord.read(buffer, 0, bufferSize);

	        // check for error on pre 6.x and 6.x API
	        if(audioStatus == AudioRecord.ERROR_INVALID_OPERATION 
	        		|| audioStatus == AudioRecord.STATE_UNINITIALIZED) {
	        	MainActivity.logger("checkAudioBufferState error status: " + audioStatus);
	        }
	    }
	    catch(Exception e) {
	    	MainActivity.logger("checkAudioBufferState exception on start.");
	    }
	    finally {
	    	try {
	    		MainActivity.logger("checkAudioBufferState no error.");
	        }
	        catch(Exception e){
	        	MainActivity.logger("checkAudioBufferState exception on close.");
	        }
	    }
	}
	
	// currently this will start and then destroy after single use...
	protected boolean pollAudioCheckerInit() {
		//set for default
		pollAudioChecker = new PollAudioChecker(audioSource, sampleRate, channel, encoding, bufferSize);
		return pollAudioChecker.setupPollAudio();
	}
	
	protected void pollAudioCheckerStart() {
		if (pollAudioChecker != null) {
			pollAudioChecker.togglePolling(userPollSpeed);
		}
	}
	
	protected void finishPollChecker() {
		if (pollAudioChecker != null) {
			pollAudioChecker.togglePolling(userPollSpeed);
			pollAudioChecker = null;
		}
	}
	
	protected void setPollingSpeed(int userSpeed) {
		userPollSpeed = userSpeed;
	}
	
	protected boolean getDetected() {
		if (pollAudioChecker != null) {
			return pollAudioChecker.getDetected();
		}
		return false;
	}
	
/********************************************************************/
/*
 * 
 */
	protected void stopAllAudio() {
		// ensure we don't keep resources
		MainActivity.logger("stopAllAudio called.");	
		if (audioRecord != null) {
			if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
				audioRecord.stop();
			}
			audioRecord.release();
			MainActivity.logger("audioRecord stop and release.");
		}
		else {
			MainActivity.logger("audioRecord is null.");
		}
	}		
}
