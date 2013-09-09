/* RefBank, the distributed platform for bibliographic references.
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
package de.uka.ipd.idaho.refBank.apps;

import javax.servlet.Servlet;

import de.uka.ipd.idaho.easyIO.web.WebAppHost;
import de.uka.ipd.idaho.onn.stringPool.apps.StringPoolAppServlet;
import de.uka.ipd.idaho.refBank.RefBankClient;
import de.uka.ipd.idaho.refBank.RefBankRestClient;

/**
 * Utility class for servlets building on RefBank, centrally providing
 * initialization facilities and, optionally, periodical updates.
 * 
 * @author sautter
 */
public class RefBankAppServlet extends StringPoolAppServlet {
	
	/**
	 * Retrieve a client object to communicate with the backing Refbank node.
	 * This may either be a REST client, or the RefBank servlet itself. The
	 * latter happens if (a) that servlet is in the same web application as the
	 * one this method belongs to, and (b) has been created.
	 * @return a client object to communicate with the backing RefBank node
	 */
	protected RefBankClient getRefBankClient() {
		
		//	we have the local connection, go with it
		if (this.rbc instanceof Servlet)
			return this.rbc;
		
		//	try switching to local connection if possible
		if (this.stringPoolNodeName != null) {
			Servlet s = WebAppHost.getInstance(this.getServletContext()).getServlet(this.stringPoolNodeName);
			if (s instanceof RefBankClient) {
				System.out.println("RefBankAppServlet: found local RefBank node");
				this.rbc = ((RefBankClient) s);
				return this.rbc;
			}
		}
		
		//	instantiate remote connection if not done before
		if (this.rbc == null) {
			System.out.println("RefBankAppServlet: connecting to RefBank node via REST");
			this.rbc = new RefBankRestClient(this.stringPoolNodeUrl);
		}
		
		//	finally ...
		return this.rbc;
	}
	private RefBankClient rbc = null;
}
