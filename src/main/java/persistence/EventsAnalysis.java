/**
 * Copyright 2016 University of Zurich
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package persistence;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import cc.kave.commons.model.events.ActivityEvent;
import cc.kave.commons.model.events.IDEEvent;
import cc.kave.commons.model.events.NavigationEvent;
import cc.kave.commons.model.events.SystemEvent;
import cc.kave.commons.model.events.visualstudio.BuildEvent;
import cc.kave.commons.model.events.visualstudio.DocumentEvent;
import cc.kave.commons.model.events.visualstudio.FindEvent;
import cc.kave.commons.model.events.visualstudio.IDEStateEvent;
import cc.kave.commons.model.events.visualstudio.SolutionEvent;
import cc.kave.commons.model.events.visualstudio.WindowEvent;
import cc.kave.commons.utils.io.IReadingArchive;
import cc.kave.commons.utils.io.ReadingArchive;

import org.supercsv.io.CsvListWriter;
// CSV writing
import org.supercsv.io.ICsvListWriter;
import org.supercsv.prefs.CsvPreference;


public class EventsAnalysis {

	private String eventsDir;
	
	//Keeps track of the temporary activity of a given user until a non-activity event is found (nav or non-nav)
	private Hashtable<String, Long> userTempActivityTable;
	
	//Keeps track of the total navigation and non-navigation time of a user
	private Hashtable<String, ArrayList<Long>> userTotalActivityTable;
	private Hashtable<String, Float> userActivityRatioTable;
	
	private Boolean wasNavigationEvent;
	private Boolean isNavigationPeriod;
	private String currentUUID;
	
	public EventsAnalysis(String eventsDir) {
		this.eventsDir = eventsDir;
		this.userTempActivityTable = new Hashtable<String, Long>();
		this.userTotalActivityTable = new Hashtable<String, ArrayList<Long>>();
		this.userActivityRatioTable = new Hashtable<String, Float>();
		this.wasNavigationEvent = false;
		this.isNavigationPeriod = false;
		this.currentUUID="";
	}
	
	private void writeRatiosToCsv() throws Exception {

	    StringWriter output = new StringWriter();
	    try (ICsvListWriter listWriter = new CsvListWriter(output, 
	         CsvPreference.STANDARD_PREFERENCE)){
	        for (Entry<String, Float> entry : userActivityRatioTable.entrySet()){
	            listWriter.write(entry.getKey(), entry.getValue());
	        }
	    }
	    
	    PrintWriter out = new PrintWriter("ratios.csv");
	    out.println(output);
	    out.close();
	    
	}

	public void run() {
		System.out.printf("looking (recursively) for events in folder %s\n", new File(eventsDir).getAbsolutePath());

		Set<String> userZips = IoHelper.findAllZips(eventsDir);

		for (String userZip : userZips) {
			System.out.printf("\n#### processing user zip: %s #####\n", userZip);
			processUserZip(userZip);
		}
		System.out.printf("# of users: %d\n", userZips.size());;
		System.out.printf("hashtable total: %s\n", this.userTotalActivityTable.toString());
		
		// Process user statistics convert the totalUserActivity tables into ratio numbers


		//Get min, max for range and compute mean and standard deviation
		float minRatio = 1;
		float maxRatio = 0; 
		float sum = 0;
		
	    for(String UUID: userTotalActivityTable.keySet()) {
	    		Long navigationTime = userTotalActivityTable.get(UUID).get(0);
	    		Long nonNavigationTime = userTotalActivityTable.get(UUID).get(1);
	    		Float ratio = (float) navigationTime / (navigationTime + nonNavigationTime);
	    		
	    		//Eliminate outliers
	    		if(ratio > 0.05 && ratio < 0.95) {
	    			userActivityRatioTable.put(UUID, ratio);
	    		
	    			if (ratio < minRatio)
	    				minRatio = ratio;
	    			if (maxRatio < ratio)
	    				maxRatio = ratio;
	    		
	    			sum += ratio;
	    		}
	    }
	    
	    float mean = sum / userActivityRatioTable.size();
	    
	    //Compute Variance
	    double variance = 0;
	    for(String UUID: userActivityRatioTable.keySet()) {
	    		double mean_dist_squared = Math.pow(userActivityRatioTable.get(UUID) - mean, 2);
	    		variance += mean_dist_squared;
	    }
	    variance /= userActivityRatioTable.size();
	    
	    double sd = Math.sqrt(variance);
	    
	    System.out.println("Relevant ratios count: " + userActivityRatioTable.size());
	    System.out.println("Range: (" + minRatio + " to " + maxRatio + ") = " + (maxRatio-minRatio));
	    System.out.println("Mean: " + mean);
	    System.out.println("Standard Deviation: " + sd);
	    		
	    
	    //Write data to CSV
	    
	    try {
			writeRatiosToCsv();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	    
	    
	    System.out.println("Done");
		
	}

	private void processUserZip(String userZip) {
		
		try (IReadingArchive ra = new ReadingArchive(new File(eventsDir, userZip))) {
			
			while (ra.hasNext()) {
				
				IDEEvent e = ra.getNext(IDEEvent.class);
				
				
				//We just encountered another user-day combination given a new UUID record
				if (!this.currentUUID.equals(e.IDESessionUUID)){
					if (!this.currentUUID.equals("")){
						//if last event for a UUID is an activity, there will be still time in the temp table
						//that must be emptied into the total table
						ArrayList<Long>  updatedValues = new ArrayList<Long>();
						//the ternary operator handles whether this time goes only in the total time
						//or also in the navigation time, based on the last non-activity event
						updatedValues.add(this.userTotalActivityTable.get(this.currentUUID).get(0) + (this.isNavigationPeriod ? this.userTempActivityTable.get(this.currentUUID) : new Long(0)));
						updatedValues.add(this.userTotalActivityTable.get(this.currentUUID).get(1) + this.userTempActivityTable.get(this.currentUUID));
						
						this.userTotalActivityTable.put(this.currentUUID, updatedValues);
						this.userTempActivityTable.put(this.currentUUID, new Long(0));
						
					}
					
					//update UUID
					this.currentUUID = e.IDESessionUUID;
					
					//add new entry in the tables
					this.userTempActivityTable.put(this.currentUUID, (long) 0);
					ArrayList<Long> values = new ArrayList<>(Arrays.asList(new Long(0), new Long(0)));
					this.userTotalActivityTable.put(this.currentUUID, values);
					this.wasNavigationEvent = false;
					this.isNavigationPeriod = false;
				}
				
				processEvent(e);
				
				//when there is a switch from a navigation to a non-navigation period
				//the temp table is emptied in the total table, in both values of the list
				if (this.isNavigationPeriod != this.wasNavigationEvent){
					ArrayList<Long>  updatedValues = new ArrayList<Long>();
					for (int i = 0; i <=1; i++){
						updatedValues.add(this.userTotalActivityTable.get(this.currentUUID).get(i) + this.userTempActivityTable.get(this.currentUUID));
					}
						
					this.userTotalActivityTable.put(this.currentUUID, updatedValues);
					this.userTempActivityTable.put(this.currentUUID, new Long(0));
					this.isNavigationPeriod = !this.isNavigationPeriod;
				}
				
				
				if (e instanceof ActivityEvent){
					//in the case of an activity event, we add it to the temp table
					this.userTempActivityTable.put(this.currentUUID, this.userTempActivityTable.get(this.currentUUID) + e.Duration.toMillis());
				} else if (e.Duration != null){
					//otherwise, we add it directly to the total table 
					ArrayList<Long>  updatedValues = new ArrayList<Long>();
					updatedValues.add(this.userTotalActivityTable.get(this.currentUUID).get(0) + (this.wasNavigationEvent ? e.Duration.toMillis() : new Long(0)));
					updatedValues.add(this.userTotalActivityTable.get(this.currentUUID).get(1) + e.Duration.toMillis() + (this.wasNavigationEvent ? this.userTempActivityTable.get(this.currentUUID) : new Long(0)));
					
					this.userTotalActivityTable.put(this.currentUUID, updatedValues);
					this.userTempActivityTable.put(this.currentUUID, new Long(0));
				}
			}
		}
	}
	
	public Hashtable<String, ArrayList<Long>> getUserTotalActivityTable() {
		return userTotalActivityTable;
	}

	private void processEvent(IDEEvent e) {
		
		if (e instanceof DocumentEvent) {
			process((DocumentEvent) e);
		} else if (e instanceof FindEvent) {
			process((FindEvent) e);
		} else if (e instanceof IDEStateEvent) {
			process((IDEStateEvent) e);
		} else if (e instanceof SolutionEvent) {
			process((SolutionEvent) e);
		} else if (e instanceof WindowEvent) {
			process((WindowEvent) e);
		} else if (e instanceof NavigationEvent) {
			process((NavigationEvent) e);
		} else {
			processBasic(e);
		}

	}

	private void process(DocumentEvent de) {
		if (de.Action.name().equals("Saved")) {
			this.wasNavigationEvent = false;
		} else {
			this.wasNavigationEvent = true;
		}
	}
	
	private void process(IDEStateEvent ie) {
		if(ie.IDELifecyclePhase.name().equals("Runtime")) {
			this.wasNavigationEvent = true;
		} else {
			this.wasNavigationEvent = false;
		}
	}
	
	private void process(SolutionEvent se) {
		if(se.Action.name().equals("OpenSolution") && se.Action.name().equals("CloseSolution")) {
			this.wasNavigationEvent = true;
		} else {
			this.wasNavigationEvent = false;
		}
		
	}
	
	private void process(FindEvent fe) {
		this.wasNavigationEvent = true;
	}
	
	private void process(WindowEvent we) {
		this.wasNavigationEvent = true;
	}
	
	private void process(NavigationEvent ne) {
		this.wasNavigationEvent = true;
	}
	
	private void processBasic(IDEEvent e) {
		this.wasNavigationEvent = false;
	}
}