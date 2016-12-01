package com.cityfreqs.pilfershush;

import com.cityfreqs.pilfershush.scanners.AudioScanner;
import android.content.Context;

public class PilferShushScanner {
	private Context context;
	private BackgroundChecker backgroundChecker;
	private AudioChecker audioChecker;	
	private AudioScanner audioScanner;
	private int scanBufferSize;
	private String bufferScanReport;
	
	protected void onDestroy() {
		audioChecker.destroy();
		backgroundChecker.destroy();
	}
	
/********************************************************************/
/*
* 	
*/		
	protected boolean initScanner(Context context) {
		this.context = context;
		scanBufferSize = 0;
		audioChecker = new AudioChecker();
		
		if (audioChecker.determineInternalAudioType()) {
			entryLogger(audioChecker.getAudioSettings().toString(), false);
			audioScanner = new AudioScanner(audioChecker.getAudioSettings());
			// get internalAudio settings here.
			initBackgroundChecks();
			return true;
		}
		return false;
	}
	
	protected boolean checkScanner() {
		return audioChecker.checkAudioRecord();
	}
	
	protected void setPollingSpeed(int delayMS) {
		audioChecker.setPollingSpeed(delayMS);
		entryLogger("Polling speed set to " + delayMS + " ms.", false);
	}
	
	protected void micChecking(boolean checking) {
		if (checking) {
			audioChecker.checkAudioBufferState();
			backgroundChecker.auditLogAsync();
		}
		else {
			audioChecker.stopAllAudio();
		}
	}
	
	protected void pollingCheck(boolean polling) {
		if (polling) {
			if (audioChecker.pollAudioCheckerInit()) {
				audioChecker.pollAudioCheckerStart();
			}
		}
		else {
			audioChecker.finishPollChecker();
		}
	}
	
	protected void setFrequencyStep(int step) {
		audioScanner.setFreqStep(step);
		entryLogger("FreqStep changed to: " + step, false);
	}
	
	protected void setMinMagnitude(double magnitude) {
		audioScanner.setMinMagnitude(magnitude);
		entryLogger("Magnitude level set: " + magnitude, false);
	}
	
	protected void runAudioScanner() {
		entryLogger("AudioScanning start...", false);
		scanBufferSize = 0;
		audioScanner.runAudioScanner();
	}
	
	protected void stopAudioScanner() {
		if (audioScanner != null) {
			entryLogger("AudioScanning stop.", false);
			audioScanner.stopAudioScanner();
			
			if (audioScanner.canProcessBufferStorage()) {
				scanBufferSize = audioScanner.getSizeBufferStorage();
				entryLogger("BufferStorage size: " + scanBufferSize, false);
			}
			else {
				entryLogger("BufferStorage: FALSE", false);
			}
		}
	}
	
	protected boolean hasBufferStorage() {
		return scanBufferSize > 0;
	}
	
	protected int getBufferStorageSize() {
		return scanBufferSize;
	}
	
	protected boolean runBufferScanner() {
		if (audioScanner.runBufferScanner()) {
			entryLogger("Buffer Scan found data.", true);
			bufferScanReport = "";
			audioScanner.storeBufferScanMap();
			if (audioScanner.processBufferScanMap()) {
				bufferScanReport = "Buffer Scan data: \n" + audioScanner.getLogicEntries();
				entryLogger(bufferScanReport, true);
				return true;
			}
		}
		else {
			entryLogger("BufferScan nothing found.", false);
		}
		return false;
	}
	
	protected String getBufferScanReport() {
		return bufferScanReport;
	}
	
	protected void stopBufferScanner() {
		audioScanner.stopBufferScanner();
	}

/********************************************************************/	
	
	protected int getAudioRecordAppsNumber() {
		return backgroundChecker.getUserRecordNumApps();
	}
	
	protected boolean hasAudioBeaconApps() {
		return backgroundChecker.checkAudioBeaconApps();
	}
	
	protected int getAudioBeaconAppNumber() {
		return backgroundChecker.getAudioBeaconAppNames().length;
	}
	
	protected String[] getAudioBeaconAppList() {		
		return backgroundChecker.getAudioBeaconAppNames();
	}
	
	protected String[] getScanAppList() {
		return backgroundChecker.getOverrideScanAppNames();
	}
	
	protected void listBeaconDetails(int appNumber) {
		listAppAudioBeaconDetails(appNumber);
	}
	
	protected void listScanDetails(int appNumber) {
		listAppOverrideScanDetails(appNumber);
	}
	
	protected boolean mainPollingCheck() {
		boolean detected = false;
		setPollingSpeed(100);
		if (audioChecker.pollAudioCheckerInit()) {
			audioChecker.pollAudioCheckerStart();
			detected = audioChecker.getDetected();
		}
		return detected;
	}
	
	protected void mainPollingStop() {
		audioChecker.finishPollChecker();
	}
	
	protected boolean hasAudioScanSequence() {
		return audioScanner.hasFrequencySequence();
	}
	
	protected String getModFrequencyLogic() {
		return audioScanner.getFrequencySequenceLogic();
	}
	
/********************************************************************/	
	// MainActivity.stopScanner() debug type outputs
	// currently rem'd out
	protected String getFrequencySequence() {
		// get original sequence as transmitted...
		String sequence = "";
		for (Integer freq : audioScanner.getFreqSequence()) {
			sequence += freq.toString();
			// add a space
			sequence += " ";
		}		
		return sequence;
	}
	
	protected String getFreqSeqLogicEntries() {
		return audioScanner.getFreqSeqLogicEntries();
	}
	
/********************************************************************/	
	
	private void initBackgroundChecks() {
		backgroundChecker = new BackgroundChecker();
		if (backgroundChecker.initChecker(context.getPackageManager())) {
			// is good
			auditBackgroundChecks();
		}
		else {
			// is bad
			MainActivity.logger("Cannot run user app checker.");
		}
	}	
	
/********************************************************************/	
/*
* 	CHECKS	
*/	
	private void auditBackgroundChecks() {
		// is good
		MainActivity.logger("run background checks...\n");
		backgroundChecker.runChecker();
		
		MainActivity.logger("User apps with RECORD AUDIO ability: " 
				+ backgroundChecker.getUserRecordNumApps() + "\n");

		backgroundChecker.audioAppEntryLog();		
	}
	
	private void listAppAudioBeaconDetails(int selectedIndex) {
		if (backgroundChecker.getAudioBeaconAppEntry(selectedIndex).checkBeaconServiceNames()) {
			entryLogger("Found audio beacon services for " 
					+ backgroundChecker.getAudioBeaconAppEntry(selectedIndex).getActivityName() 
					+ ": " + backgroundChecker.getAudioBeaconAppEntry(selectedIndex).getBeaconServiceNamesNum(), true);
			
			logAppEntryInfo(backgroundChecker.getAudioBeaconAppEntry(selectedIndex).getBeaconServiceNames());
		}
		//TODO
		// add a call for any receiver names too
		if (backgroundChecker.getAudioBeaconAppEntry(selectedIndex).checkBeaconReceiverNames()) {
			entryLogger("Found audio beacon receivers for " 
					+ backgroundChecker.getAudioBeaconAppEntry(selectedIndex).getActivityName() 
					+ ": " + backgroundChecker.getAudioBeaconAppEntry(selectedIndex).getBeaconReceiverNamesNum(), true);
			
			logAppEntryInfo(backgroundChecker.getAudioBeaconAppEntry(selectedIndex).getBeaconReceiverNames());
		}
	}
	
	private void listAppOverrideScanDetails(int selectedIndex) {
		// check for receivers too?
		entryLogger("Found User App services for " 
				+ backgroundChecker.getOverrideScanAppEntry(selectedIndex).getActivityName() 
				+ ": " + backgroundChecker.getOverrideScanAppEntry(selectedIndex).getServicesNum(), true);
		
		if (backgroundChecker.getOverrideScanAppEntry(selectedIndex).getServicesNum() > 0) {
			logAppEntryInfo(backgroundChecker.getOverrideScanAppEntry(selectedIndex).getServiceNames());
		}
		
		entryLogger("Found User App receivers for " 
				+ backgroundChecker.getOverrideScanAppEntry(selectedIndex).getActivityName() 
				+ ": " + backgroundChecker.getOverrideScanAppEntry(selectedIndex).getReceiversNum(), true);
		
		if (backgroundChecker.getOverrideScanAppEntry(selectedIndex).getReceiversNum() > 0) {
			logAppEntryInfo(backgroundChecker.getOverrideScanAppEntry(selectedIndex).getReceiverNames());
		}
	}
	
	private void logAppEntryInfo(String[] appEntryInfoList) {
		entryLogger("\nAppEntry list: \n", false);
		for (int i = 0; i < appEntryInfoList.length; i++) {
			entryLogger(appEntryInfoList[i] + "\n", false);
		}
	}
	
	private static void entryLogger(String entry, boolean caution) {
        MainActivity.entryLogger(entry, caution);
	}
}
