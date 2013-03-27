/* RefBank, the distributed platform for biliographic references.
 * Copyright (C) 2011-2013 ViBRANT (FP7/2007-2013, GA 261532), by D. King & G. Sautter
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package de.uka.ipd.idaho.onn.stringPool.apps;

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import de.uka.ipd.idaho.easyIO.web.HtmlServlet;
import de.uka.ipd.idaho.easyIO.web.WebAppHost;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient;
import de.uka.ipd.idaho.onn.stringPool.StringPoolConstants;
import de.uka.ipd.idaho.onn.stringPool.StringPoolRestClient;

/**
 * Utility class for servlets building on the string pool, centrally providing
 * initialization facilities and, optionally, periodical updates.
 * 
 * @author sautter
 */
public class StringPoolAppServlet extends HtmlServlet implements StringPoolConstants {
	
	/**
	 * the URL of the string pool node this servlet builds upon (for remote
	 * access, and first local access)
	 */
	protected String stringPoolNodeUrl;

	/**
	 * the servlet name of the string pool node this servlet builds upon (for
	 * local access via the WebAppHost registry and direct method invocation)
	 */
	protected String stringPoolNodeName;
	
	private EventFetcherThread updateFetcherService = null;
	private int lookupInterval = (10 * 60 * 1000);
	private long lastLookup;
	private long lastAttemptedLookup;
	
	private class EventFetcherThread extends Thread {
		private boolean keepRunning = true;
		public void run() {
			
			//	wake up creator thread
			synchronized (this) {
				this.notify();
			}
			
			//	run until shutdown() is called
			while (this.keepRunning) {
				
				//	check if update waiting
				long currentTime = System.currentTimeMillis();
				
				//	updates due, get them
				if (lookupInterval < (currentTime - lastAttemptedLookup)) try {
					lastAttemptedLookup = currentTime;
					fetchUpdates(lastLookup);
					lastLookup = currentTime;
					setSetting("lastLookup", ("" + lastLookup));
				}
				catch (IOException ioe) {
					System.out.println("IO error on getting updates from " + stringPoolNodeUrl + " - " + ioe.getClass().getName() + " (" + ioe.getMessage() + ")");
					ioe.printStackTrace(System.out);
				}
				catch (Exception e) {
					System.out.println("Error on getting updates from " + stringPoolNodeUrl + " - " + e.getClass().getName() + " (" + e.getMessage() + ")");
					e.printStackTrace(System.out);
				}
				
				//	give a little time to the others
				if (this.keepRunning) try {
					Thread.sleep(1000);
				}
				catch (InterruptedException ie) {
					System.out.println("Could not sleep as supposed to");
				}
			}
		}
		
		synchronized void shutdown() {
			this.keepRunning = false;
			try {
				this.join();
			} catch (InterruptedException ie) {}
		}
	}
	
	/**
	 * This implementation loads the URL of the backing String Pool node and
	 * initializes synchronization, so sub classes overwriting this method have
	 * to make the super call.
	 * @see de.uka.ipd.idaho.easyIO.web.HtmlServlet#doInit()
	 */
	protected void doInit() throws ServletException {
		super.doInit();
		
		//	get access to backing string pool node
		this.stringPoolNodeUrl = this.getSetting("stringPoolNodeUrl");
		if (this.stringPoolNodeUrl == null)
			throw new ServletException("Invalid string pool node URL.");
		this.stringPoolNodeName = this.getSetting("stringPoolNodeName");
		
		//	load lookup interval and last lookup
		try {
			this.lookupInterval = Integer.parseInt(this.getSetting("lookupInterval", ("" + this.lookupInterval)));
		} catch (NumberFormatException nfe) {}
		try {
			this.lastLookup = Long.parseLong(this.getSetting("lastLookup", ("" + this.lastLookup)));
		} catch (NumberFormatException nfe) {}
		
		//	start update fetcher
		if (this.fetchUpdates()) {
			this.updateFetcherService = new EventFetcherThread();
			synchronized (this.updateFetcherService) {
				this.updateFetcherService.start();
				try {
					this.updateFetcherService.wait();
				} catch (InterruptedException ie) {}
			}
		}
	}
	
	/**
	 * Retrieve a client object to communicate with the backing string pool
	 * node. This may either be a REST client, or the string pool servlet
	 * itself. The latter happens if (a) that servlet is in the same web
	 * application as the one this method belongs to, and (b) has been created.
	 * @return a client object to communicate with the backing string pool node
	 */
	protected StringPoolClient getStringPoolClient() {
		//	we have the local connection, go with it
		if (this.spc instanceof Servlet)
			return this.spc;
		
		//	try switching to local connection if possible
		if (this.stringPoolNodeName != null) {
			Servlet s = WebAppHost.getInstance(this.getServletContext()).getServlet(this.stringPoolNodeName);
			if (s instanceof StringPoolClient) {
				this.spc = ((StringPoolClient) s);
				return this.spc;
			}
		}
		
		//	instantiate remote connection if not done before
		if (this.spc == null)
			this.spc = new StringPoolRestClient(this.stringPoolNodeUrl);
		
		//	finally ...
		return this.spc;
	}
	private StringPoolClient spc = null;
	
	/**
	 * Indicate whether or not to fetch updates from the connected string pool
	 * node. This default implementation returns false, deactivating periodic
	 * updates. Sub classes wanting to use the update mechanism have to
	 * overwrite this method to return true.
	 * @return true if updates should be fetched, false otherwise
	 */
	protected boolean fetchUpdates() {
		return false;
	}
	
	/**
	 * Fetch updates from the connected string pool node. This method is called
	 * periodically if updates are activated.
	 * @param lastLookup the time of the last lookup
	 * @throws IOException
	 */
	protected void fetchUpdates(long lastLookup) throws IOException {}
	
	/**
	 * This implementation shuts down the update fetcher service, so sub classes
	 * overwriting this method have to make the super call.
	 * @see de.uka.ipd.idaho.easyIO.web.WebServlet#exit()
	 */
	protected void exit() {
		if (this.updateFetcherService != null) {
			this.updateFetcherService.shutdown();
			this.updateFetcherService = null;
		}
	}
}
