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
package examples;

import java.io.File;
import java.util.ArrayList;
import java.util.Hashtable;
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

/**
 * Simple example that shows how the interaction dataset can be opened, all
 * users identified, and all contained events deserialized.
 */
public class GettingStarted {

	private String eventsDir;
	private Hashtable<String, Long> userTempActivityTable;
	private Hashtable<String, ArrayList<Long>> userTotalActivityTable;
	private Boolean wasNavigationEvent;
	private Boolean isNavigationPeriod;
	private String currentUser;
	
	public GettingStarted(String eventsDir) {
		this.eventsDir = eventsDir;
		this.userTempActivityTable = new Hashtable<String, Long>();
		this.userTotalActivityTable = new Hashtable<String, ArrayList<Long>>();
		this.wasNavigationEvent = false;
		this.isNavigationPeriod = false;
	}

	public void run() {
		System.out.printf("looking (recursively) for events in folder %s\n", new File(eventsDir).getAbsolutePath());

		/*
		 * Each .zip that is contained in the eventsDir represents all events that we
		 * have collected for a specific user, the folder represents the first day when
		 * the user uploaded data.
		 */
		Set<String> userZips = IoHelper.findAllZips(eventsDir);

		for (String userZip : userZips) {
			System.out.printf("\n#### processing user zip: %s #####\n", userZip);
			processUserZip(userZip);
		}
		System.out.printf("# of users: %d\n", userZips.size());
		System.out.printf("hashtable activity: %s\n", this.userTempActivityTable.toString());
		System.out.printf("hashtable total: %s\n", this.userTotalActivityTable.toString());
	}

	private void processUserZip(String userZip) {
		System.out.println(userZip);
		this.currentUser = userZip;
		this.userTempActivityTable.put(this.currentUser, (long) 0);
		ArrayList<Long> values = new ArrayList<Long>();
		for (int i = 0; i <=1; i++)
			values.add(new Long(0));
		this.userTotalActivityTable.put(this.currentUser, values);
		
		this.wasNavigationEvent = false;
		this.isNavigationPeriod = false;
		// open the .zip file ...
		try (IReadingArchive ra = new ReadingArchive(new File(eventsDir, userZip))) {
			// ... and iterate over content.
			while (ra.hasNext()) {
				/*
				 * within the userZip, each stored event is contained as a single file that
				 * contains the Json representation of a subclass of IDEEvent.
				 */
				IDEEvent e = ra.getNext(IDEEvent.class);

				// the events can then be processed individually
				processEvent(e);
				
				if (this.isNavigationPeriod != this.wasNavigationEvent){
					ArrayList<Long>  updatedValues = new ArrayList<Long>();
					for (int i = 0; i <=1; i++){
						updatedValues.add(this.userTotalActivityTable.get(this.currentUser).get(i) + this.userTempActivityTable.get(this.currentUser));
					}
						
					this.userTotalActivityTable.put(this.currentUser, updatedValues);
					this.userTempActivityTable.put(this.currentUser, new Long(0));
					this.isNavigationPeriod = !this.isNavigationPeriod;
				}
				
				if (e instanceof ActivityEvent){
					this.userTempActivityTable.put(this.currentUser, this.userTempActivityTable.get(this.currentUser) + e.Duration.toMillis());
				} else if (e.Duration != null){
					ArrayList<Long>  updatedValues = new ArrayList<Long>();
					updatedValues.add(this.userTotalActivityTable.get(this.currentUser).get(0) + (this.wasNavigationEvent ? e.Duration.toMillis() : new Long(0)));
					updatedValues.add(this.userTotalActivityTable.get(this.currentUser).get(1) + e.Duration.toMillis() + (this.wasNavigationEvent ? this.userTempActivityTable.get(this.currentUser) : new Long(0)));
					
					this.userTotalActivityTable.put(this.currentUser, updatedValues);
					this.userTempActivityTable.put(this.currentUser, new Long(0));
				}
			}
			ArrayList<Long>  updatedValues = new ArrayList<Long>();
			updatedValues.add(this.userTotalActivityTable.get(this.currentUser).get(0) + (this.isNavigationPeriod ? this.userTempActivityTable.get(this.currentUser) : new Long(0)));
			updatedValues.add(this.userTotalActivityTable.get(this.currentUser).get(1) + this.userTempActivityTable.get(this.currentUser));
			
			this.userTotalActivityTable.put(this.currentUser, updatedValues);
			this.userTempActivityTable.put(this.currentUser, new Long(0));
			double ratioU = ((double)this.userTotalActivityTable.get(this.currentUser).get(0))/((double)(this.userTotalActivityTable.get(this.currentUser).get(1)) + 1);
			System.out.println("ratio: " + ratioU);
		}
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