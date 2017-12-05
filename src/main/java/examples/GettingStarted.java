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
import java.time.ZonedDateTime;
import java.util.Hashtable;
import java.util.Set;

import cc.kave.commons.model.events.ActivityEvent;
import cc.kave.commons.model.events.CommandEvent;
import cc.kave.commons.model.events.IDEEvent;
import cc.kave.commons.model.events.NavigationEvent;
import cc.kave.commons.model.events.completionevents.CompletionEvent;
import cc.kave.commons.model.events.visualstudio.BuildEvent;
import cc.kave.commons.model.ssts.ISST;
import cc.kave.commons.utils.io.IReadingArchive;
import cc.kave.commons.utils.io.ReadingArchive;

/**
 * Simple example that shows how the interaction dataset can be opened, all
 * users identified, and all contained events deserialized.
 */
public class GettingStarted {

	private String eventsDir;
	private Hashtable<String, Long> cumulativeActivityTime = new Hashtable<String, Long>();

	public GettingStarted(String eventsDir) {
		this.eventsDir = eventsDir;
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
			//System.out.printf("\n#### processing user zip: %s #####\n", userZip);
			processUserZip(userZip);
		}
		System.out.printf("# of users: %d\n", userZips.size());
		System.out.printf("hashtable activity: %s\n", this.cumulativeActivityTime.toString());
		System.out.printf("hashtable activity size: %s\n", this.cumulativeActivityTime.size());
	}

	private void processUserZip(String userZip) {
		int numProcessedEvents = 0;
		// open the .zip file ...
		try (IReadingArchive ra = new ReadingArchive(new File(eventsDir, userZip))) {
			// ... and iterate over content.
			// the iteration will stop after 200 events to speed things up.
			while (ra.hasNext() && (numProcessedEvents++ < 200)) {
				/*
				 * within the userZip, each stored event is contained as a single file that
				 * contains the Json representation of a subclass of IDEEvent.
				 */
				IDEEvent e = ra.getNext(IDEEvent.class);

				// the events can then be processed individually
				processEvent(e);
			}
		}
	}
	
	private void processEvent(IDEEvent e) {

		if (e instanceof ActivityEvent) {
			process((ActivityEvent) e);
		} else {
			processBasic(e);
		}

	}

	private void process(ActivityEvent e) {
		String id = e.IDESessionUUID;
		if (this.cumulativeActivityTime.containsKey(id)){
			this.cumulativeActivityTime.put(id, this.cumulativeActivityTime.get(id) + e.Duration.toMillis());
		} else {
			this.cumulativeActivityTime.put(id, e.Duration.toMillis());
		}
	}

	private void processBasic(IDEEvent e) {
		//TODO
	}
}