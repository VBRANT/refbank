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
package de.uka.ipd.idaho.onn;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TreeMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import de.uka.ipd.idaho.easyIO.settings.Settings;
import de.uka.ipd.idaho.easyIO.web.HtmlServlet;
import de.uka.ipd.idaho.htmlXmlUtil.TokenReceiver;
import de.uka.ipd.idaho.htmlXmlUtil.TreeNodeAttributeSet;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;

/**
 * @author sautter
 */
public class OnnServlet extends HtmlServlet implements OnnConstants {
	
	private static final String NODES_NODE_TYPE = (ONN_XML_NAMESPACE_PREFIX + "nodes");
	private static final String NODE_NODE_TYPE = (ONN_XML_NAMESPACE_PREFIX + "node");
	private static final String NAME_ATTRIBUTE = "name";
	private static final String ACCESS_URL_ATTRIBUTE = "accessUrl";
	
	private static final String DOMAIN_NAME_REGEX = "[A-Za-z0-9][A-Za-z0-9\\-\\_\\.]+[A-Za-z0-9]";
	
	private static final String NODES_ACTION_NAME = "nodes";
	private static final String NAME_ACTION_NAME = "name";
	private static final String INTRODUCE_ACTION_NAME = "introduce";
	private static final String PING_ACTION_NAME = "ping";
	private static final String ADMIN_ACTION_NAME = "admin";
	
	private static final String NAME_PARAMETER = "name";
	private static final String ACCESS_URL_PARAMETER = "accessUrl";
	private static final String REPLICATION_INTERVAL_PARAMETER = "replicationInterval";
	private static final String ACTIVE_PARAMETER = "active";
	private static final String PASSCODE_PARAMETER = "passcode";
	private static final String OPERATION_PARAMETER = "operation";
	
	private static final String ADD_NODE_OPERATION = "addNode";
	private static final String GET_NODES_OPERATION = "getNodes";
	private static final String UPDATE_NODES_OPERATION = "updateNodes";
	private static final String SET_ACCESS_URL_OPERATION = "setAccessUrl";
	private static final String SET_PASSCODE_OPERATION = "setPasscode";
	private static final String PING_OPERATION = "ping";
	private static final String RESET_REPLICATION_OPERATION = "resetReplication";
	
	protected String domainName;
	protected String accessUrl;
	
	private String passcode;
	
	private TreeMap nodes = new TreeMap();
	private EventFetcherThread updateFetcherService = null;
	
	private class EventFetcherThread extends Thread {
		private boolean keepRunning = true;
		public void run() {
			
			//	wake up creator thread
			synchronized (nodes) {
				nodes.notify();
			}
			
			//	delay a bit (60 seconds) on startup, allow request that caused servlet to be loaded to be answered first
			try {
				Thread.sleep(60 * 1000);
			} catch (InterruptedException ie) {}
			System.out.println("OpenNodeNetwork: event fetcher thread entering service");
			
			//	run until shutdown() is called
			while (this.keepRunning) {
				
				//	check if update waiting
				OnnNode updateNode = null;
				long currentTime = System.currentTimeMillis();
				
				//	check if some node to poll
				synchronized (nodes) {
					
					//	first, check when last tried to poll
					for (Iterator rit = nodes.values().iterator(); (updateNode == null) && rit.hasNext();) {
						OnnNode node = ((OnnNode) rit.next());
						if (!node.active)
							continue;
						if ((node.lastAttemptedLookup + (node.updateInterval * 1000)) < currentTime)
							updateNode = node;
					}
				}
				
				//	if so, get updates
				if (updateNode != null) try {
					updateNode.lastAttemptedLookup = currentTime;
					updateFrom(updateNode);
				}
				catch (IOException ioe) {
					System.out.println("IO error on getting updates from " + updateNode.name + " - " + ioe.getClass().getName() + " (" + ioe.getMessage() + ")");
					ioe.printStackTrace(System.out);
				}
				catch (Exception e) {
					System.out.println("Error on getting updates from " + updateNode.name + " - " + e.getClass().getName() + " (" + e.getMessage() + ")");
					e.printStackTrace(System.out);
				}
				
				//	give a little time to the others
				if (this.keepRunning) try {
					Thread.sleep(1000);
				} catch (InterruptedException ie) {}
			}
		}
		
		void shutdown() {
			synchronized (nodes) {
				this.keepRunning = false;
				nodes.notify();
			}
			try {
				this.join();
			} catch (InterruptedException ie) {}
		}
	}
	
	/**
	 * This implementation loads the basic settings for the ONN node, so sub
	 * classes overwriting this method have to make the super call.
	 * @see de.uka.ipd.idaho.easyIO.web.HtmlServlet#doInit()
	 */
	protected void doInit() throws ServletException {
		super.doInit();
		
		//	get local domain properties
		this.domainName = this.getSetting("domainName");
		if ((this.domainName == null) || !this.domainName.matches(DOMAIN_NAME_REGEX))
			throw new ServletException("Invalid domain name.");
		this.accessUrl = this.getSetting("accessUrl");
		
		//	read password
		this.passcode = this.getSetting("adminPasscode");
		
		//	load known nodes
		File[] remoteResFiles = this.dataFolder.listFiles(new FileFilter() {
			public boolean accept(File file) {
				return (file.isFile() && file.getName().endsWith(".node.cnfg"));
			}
		});
		for (int r = 0; r < remoteResFiles.length; r++) {
			Settings nodeData = Settings.loadSettings(remoteResFiles[r]);
			String domainName = nodeData.getSetting(DOMAIN_NAME_SETTING);
			String accessUrl = nodeData.getSetting(ACCESS_URL_SETTING);
			if ((domainName != null) && (accessUrl != null)) {
				OnnNode node = new OnnNode(domainName, accessUrl);
				try {
					node.updateInterval = Integer.parseInt(nodeData.getSetting(UPDATE_INTERVAL_SETTING, "3600"));
				} catch (NumberFormatException nfe) {}
				try {
					node.lastUpdate = Long.parseLong(nodeData.getSetting(LAST_UPDATE_SETTING, "0"));
				} catch (NumberFormatException nfe) {}
				node.active = "true".equals(nodeData.getSetting(ACTIVE_SETTING, "false"));
				this.nodes.put(node.name, node);
			}
		}
		
		//	start update fetcher
		synchronized (this.nodes) {
			this.updateFetcherService = new EventFetcherThread();
			this.updateFetcherService.start();
			try {
				this.nodes.wait();
			} catch (InterruptedException ie) {}
		}
	}
	
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
	
	/**
	 * Retrieve descriptor objects of the nodes known to this one.
	 * @return an array holding descriptor objects of the nodes known to this
	 *         one
	 */
	public OnnNode[] getNodes() {
		return ((OnnNode[]) this.nodes.values().toArray(new OnnNode[this.nodes.size()]));
	}
	
	/**
	 * Retrieve the descriptor object of a remote node with a given name.
	 * @param name the name of the node
	 * @return the descriptor object of the remote node with the specified name
	 */
	public OnnNode getNode(String name) {
		return ((name == null) ? null : ((OnnNode) this.nodes.get(name)));
	}
	
	private void updateFrom(OnnNode node) throws IOException {
		System.out.println("OpenNodeNetwork: updating from " + node.name + " (" + node.accessUrl + ")");
		boolean success = true;
		//	with some 10% probability, fetch infrastructure data TODO assess if frequency makes sense
		if (Math.random() < 0.1) {
			OnnNode[] rNodes = node.getNodes();
			if (rNodes != null) try {
				System.out.println("  - updating infrastructure information");
				this.addNodes(rNodes);
				System.out.println("  - infrastructure information updated");
			}
			catch (IOException ioe) {
				success = false;
				ioe.printStackTrace(System.out);
			}
		}
		try {
			System.out.println("  - updating data");
			this.doUpdateFrom(node);
			System.out.println("  - data updated");
		}
		catch (IOException ioe) {
			success = false;
			ioe.printStackTrace(System.out);
		}
		
		if (success)
			node.lastUpdate = node.lastAttemptedLookup;
	}
	
	/**
	 * Pull updates from another node. This method is called periodically for
	 * all connected nodes with which replication is activated, namely in the
	 * replication interval set for the individual nodes. This default
	 * implementation does nothing, sub classes are welcome to overwrite it as
	 * needed.
	 * @param node the node to pull from
	 * @throws IOException
	 */
	protected void doUpdateFrom(OnnNode node) throws IOException {}
	
	/**
	 * This implementation handles the administrative and infrastructure
	 * replication actions. Sub classes are recommended to overwrite it, filter
	 * out their specific actions, and delegate all other actions to this
	 * implementation.
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//		String action = request.getParameter(ACTION_PARAMETER);
		String action = request.getPathInfo();
		if (action == null)
			action = request.getParameter(ACTION_PARAMETER);
		else {
			while (action.startsWith("/"))
				action = action.substring(1);
			if (action.indexOf('/') != -1)
				action = action.substring(0, action.indexOf('/'));
		}
		
		//	request for known nodes
		if (NODES_ACTION_NAME.equals(action))
			this.doGetNodes(request, response);
		
		//	ping
		else if (PING_ACTION_NAME.equals(action))
			this.doPing(request, response);
		
		//	request for name and access URL
		else if (NAME_ACTION_NAME.equals(action))
			this.doName(request, response);
		
		//	request for admin login page
		else if (ADMIN_ACTION_NAME.equals(action))
			this.sendLoginPage(request, response);
		
		//	unknown action, send error
		else response.sendError(HttpServletResponse.SC_BAD_REQUEST, ("Invalid action: " + action));
	}
	
	/**
	 * This implementation handles the administrative and infrastructure
	 * replication actions. Sub classes are recommended to overwrite it, filter
	 * out their specific actions, and delegate all other actions to this
	 * implementation.
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//		String action = request.getParameter(ACTION_PARAMETER);
		String action = request.getPathInfo();
		if (action == null)
			action = request.getParameter(ACTION_PARAMETER);
		else {
			while (action.startsWith("/"))
				action = action.substring(1);
			if (action.indexOf('/') != -1)
				action = action.substring(0, action.indexOf('/'));
		}
		
		//	some administrative request
		if (ADMIN_ACTION_NAME.equals(action))
			this.doAdmin(request, response);
		
		//	introduction of a new node
		else if (INTRODUCE_ACTION_NAME.equals(action))
			this.doIntroduceNode(request, response);
		
		//	request for known nodes
		else if (NODES_ACTION_NAME.equals(action))
			this.doGetNodes(request, response);
		
		//	ping
		else if (PING_ACTION_NAME.equals(action))
			this.doPing(request, response);
		
		//	request for name and access URL
		else if (NAME_ACTION_NAME.equals(action))
			this.doName(request, response);
		
		//	unknown action, send error
		else response.sendError(HttpServletResponse.SC_BAD_REQUEST, ("Invalid action: " + action));
	}
	
	private void doIntroduceNode(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	get and check parameters
		String name = request.getParameter("name");
		String accessUrl = request.getParameter("accessUrl");
		if (accessUrl != null) try {
			new URL(accessUrl);
		}
		catch (MalformedURLException mue) {
			accessUrl = null;
		}
		if ((name == null) || !name.matches(DOMAIN_NAME_REGEX) || (accessUrl == null)) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid domain name or access URL.");
			return;
		}
		
		//	add introducing node if unknown
		if (!this.nodes.containsKey(name)) {
			OnnNode node = new OnnNode(name, accessUrl);
			this.storeNode(node);
			this.nodes.put(node.name, node);
		}
		
		//	send known nodes
		response.setCharacterEncoding(ENCODING);
		response.setContentType("text/xml");
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
		this.writeNodes(this.getNodes(), bw, true);
		bw.flush();
	}
	
	private void doGetNodes(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	send known nodes
		response.setCharacterEncoding(ENCODING);
		response.setContentType("text/xml");
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
		this.writeNodes(this.getNodes(), bw, true);
		bw.flush();
	}
	
	private void doPing(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	send empty response as acknowledgement
		response.setCharacterEncoding(ENCODING);
		response.setContentType("text/xml");
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
		this.writeNodes(new OnnNode[0], bw, false);
		bw.flush();
	}
	
	private void doName(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	send own data only
		response.setCharacterEncoding(ENCODING);
		response.setContentType("text/xml");
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
		this.writeNodes(new OnnNode[0], bw, true);
		bw.flush();
	}
	
	private void doAdmin(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	authenticate
		HttpSession session = request.getSession(false);
		if (session == null) {
			String passcode = request.getParameter(PASSCODE_PARAMETER);
			if (this.passcode.equals(passcode))
				session = request.getSession(true);
			else {
				this.sendLoginPage(request, response);
				return;
			}
		}
		
		//	get basic parameters
		String operation = request.getParameter(OPERATION_PARAMETER);
		String operationName = null;
		String operationResult = null;
		String operationError = null;
		
		//	add new node
		if (ADD_NODE_OPERATION.equals(operation)) {
			operationName = "Add Node";
			String accessUrl = request.getParameter(ACCESS_URL_PARAMETER);
			if (accessUrl != null) try {
				new URL(accessUrl);
			}
			catch (MalformedURLException mue) {
				accessUrl = null;
			}
			if (accessUrl == null)
				operationError = "Invalid access URL.";
			else try {
				OnnNode nNode = new OnnNode(accessUrl);
				if (this.getNode(nNode.name) != null)
					operationError = "Already known domain name.";
				else {
					OnnNode[] rNodes = nNode.introduceTo(this.domainName, this.accessUrl);
					if (rNodes == null)
						operationError = "Remote domain unreachable.";
					else {
						NAR nar = this.addNodes(rNodes);
						operationResult = ("Domain '" + nNode.name + "' added successfully, imported " + nar.newNodes + " new nodes, updated " + nar.updatedNodes + " ones.");
					}
				}
			}
			catch (IOException ioe) {
				operationError = "Remote domain unreachable.";
			}
		}
		
		//	modify nodes
		else if (UPDATE_NODES_OPERATION.equals(operation)) {
			operationName = "Update Nodes";
			OnnNode[] nodes = this.getNodes();
			int unc = 0;
			for (int n = 0; n < nodes.length; n++) {
				String accessUrl = request.getParameter(nodes[n].name + "." + ACCESS_URL_PARAMETER);
				if (accessUrl != null) try {
					new URL(accessUrl);
				}
				catch (MalformedURLException mue) {
					accessUrl = null;
				}
				String replicationIntervalString = request.getParameter(nodes[n].name + "." + REPLICATION_INTERVAL_PARAMETER);
				int replicationInterval = -1;
				if (replicationIntervalString == null)
					operationError = "Invalid replication interval.";
				else try {
					replicationInterval = Integer.parseInt(replicationIntervalString);
				}
				catch (NumberFormatException nfe) {}
				String activeString = request.getParameter(nodes[n].name + "." + ACTIVE_PARAMETER);
				
				boolean updated = false;
				if ((replicationInterval != -1) && (replicationInterval != nodes[n].updateInterval)) {
					nodes[n].updateInterval = replicationInterval;
					updated = true;
				}
				if ((activeString != null) && ("true".equals(activeString) != nodes[n].active)) {
					nodes[n].active = "true".equals(activeString);
					updated = true;
				}
				if ((accessUrl != null) && !nodes[n].accessUrl.equals(accessUrl)) {
					OnnNode uNode = new OnnNode(nodes[n].name, accessUrl);
					uNode.updateInterval = nodes[n].updateInterval;
					uNode.lastUpdate = nodes[n].lastUpdate;
					uNode.active = nodes[n].active;
					this.nodes.put(uNode.name, uNode);
					nodes[n] = uNode;
					updated = true;
				}
				if (updated) {
					unc++;
					this.storeNode(nodes[n]);
				}
			}
			if (unc == 0)
				operationResult = "No nodes updated.";
			else operationResult = (unc + " nodes updated successfully.");
		}
		
		//	modify access URL
		else if (SET_ACCESS_URL_OPERATION.equals(operation)) {
			operationName = "Access URL Update";
			String accessUrl = request.getParameter(ACCESS_URL_PARAMETER);
			if (accessUrl != null) try {
				new URL(accessUrl);
			}
			catch (MalformedURLException mue) {
				accessUrl = null;
			}
			if (accessUrl == null)
				operationError = "Invalid access URL.";
			else {
				this.setSetting("accessUrl", accessUrl);
				this.accessUrl = accessUrl;
				operationResult = "Access URL set successfully.";
			}
		}
		
		//	modify passcode
		else if (SET_PASSCODE_OPERATION.equals(operation)) {
			operationName = "Passcode Update";
			String passcode = request.getParameter(PASSCODE_PARAMETER);
			String newPasscode1 = request.getParameter("newPasscode1");
			String newPasscode2 = request.getParameter("newPasscode2");
			if (!this.passcode.equals(passcode))
				operationError = "Invalid passcode.";
			else if (newPasscode1 == null)
				operationError = "Invalid new passcode.";
			else if (!newPasscode1.equals(newPasscode2))
				operationError = "New passcode does not match confirmation.";
			else {
				this.setSetting("adminPasscode", newPasscode1);
				this.passcode = newPasscode1;
				operationResult = "Passcode set successfully.";
			}
		}
		
		//	reset replication threshold for some node
		else if (RESET_REPLICATION_OPERATION.equals(operation)) {
			operationName = "Replication Reset";
			OnnNode node = this.getNode(request.getParameter(NAME_PARAMETER));
			if (node == null)
				operationError = "Unknown domain name.";
			else {
				node.lastUpdate = 0;
				operationResult = "Replication timestamp reset successfully.";
			}
		}
		
		//	ping other node
		else if (PING_OPERATION.equals(operation)) {
			operationName = "Ping";
			OnnNode node = this.getNode(request.getParameter(NAME_PARAMETER));
			if (node == null)
				operationError = "Unknown domain name.";
			else if (node.ping())
				operationResult = "Remote domain contacted successfully.";
			else operationError = "Remote domain unreachable.";
		}
		
		//	import infrastructure information from other node
		else if (GET_NODES_OPERATION.equals(operation)) {
			operationName = "Node Import";
			OnnNode node = this.getNode(request.getParameter(NAME_PARAMETER));
			if (node == null)
				operationError = "Unknown domain name.";
			else {
				OnnNode[] rNodes = node.getNodes();
				if (rNodes == null)
					operationError = "Remote domain unreachable.";
				else {
					NAR nar = this.addNodes(rNodes);
					operationResult = ("Imported " + nar.newNodes + " new nodes, updated " + nar.updatedNodes + " ones.");
				}
			}
		}
		
		//	unknown action, send error
		else if (operation != null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, ("Invalid operation: " + operation));
			return;
		}
		
		//	display node list
		this.sendAdminPage(request, response, operationName, operationResult, operationError);
	}
	
	private void sendLoginPage(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setCharacterEncoding(ENCODING);
		response.setContentType("text/html");
		HtmlPageBuilder loginPageBuilder = new HtmlPageBuilder(this, request, response) {
			protected String getPageTitle(String title) {
				return ("Open Node Network - Node Admin Login Page");
			}
			protected void include(String type, String tag) throws IOException {
				if ("includeBody".equals(type))
					this.includeBody();
				else super.include(type, tag);
			}
			
			private void includeBody() throws IOException {
//				this.writeLine("<form action=\"" + this.request.getContextPath() + this.request.getServletPath() + "\" method=\"POST\" id=\"loginForm\">");
				this.writeLine("<form action=\"" + this.request.getContextPath() + this.request.getServletPath() + "/" + ADMIN_ACTION_NAME + "\" method=\"POST\" id=\"loginForm\">");
				this.writeLine("<input type=\"hidden\" name=\"" + ACTION_PARAMETER + "\" value=\"" + ADMIN_ACTION_NAME + "\">");
				
				this.writeLine("<table class=\"loginTable\" id=\"loginTable\">");
				this.writeLine("<tr class=\"loginTableHead\">");
				this.writeLine("<td class=\"loginTableCell\">Enter Passcode</td>");
				this.writeLine("</tr>");
				this.writeLine("<tr class=\"loginTableBody\">");
				this.writeLine("<td class=\"loginTableCell\"><input type=\"password\" name=\"" + PASSCODE_PARAMETER + "\" value=\"\"></td>");
				this.writeLine("</tr>");
				this.writeLine("<tr class=\"loginTableBody\">");
				this.writeLine("<td class=\"loginTableCell\"><input type=\"submit\" class=\"button\" value=\"Login\"></td>");
				this.writeLine("</tr>");
				this.writeLine("</table>");
				
				this.writeLine("</form>");
			}
		};
		this.sendHtmlPage("onnNodeAdminPage.html", loginPageBuilder);
	}
	
	private void sendAdminPage(HttpServletRequest request, HttpServletResponse response, final String opName, final String opResult, final String opError) throws IOException {
		response.setCharacterEncoding(ENCODING);
		response.setContentType("text/html");
		HtmlPageBuilder adminPageBuilder = new HtmlPageBuilder(this, request, response) {
			protected String getPageTitle(String title) {
				return ("Open Node Network - Node Admin Page (" + domainName + ")");
			}
			protected void include(String type, String tag) throws IOException {
				if ("includeBody".equals(type))
					this.includeBody();
				else super.include(type, tag);
			}
			protected void writePageHeadExtensions() throws IOException {
				this.writeLine("<script type=\"text/javascript\">");
				
				this.writeLine("var submitForm;");
				this.writeLine("var operation;");
				
				this.writeLine("function init() {");
				this.writeLine("  submitForm = $('submitForm');");
				this.writeLine("  operation = $('operation');");
				this.writeLine("}");
				
				this.writeLine("function submitAddNode() {");
				this.writeLine("  operation.value = '" + ADD_NODE_OPERATION + "';");
				this.writeLine("  var auf = $('new.accessUrl');");
				this.writeLine("  var au = addInput('" + ACCESS_URL_PARAMETER + "');");
				this.writeLine("  au.value = auf.value;");
				this.writeLine("  submitForm.submit();");
				this.writeLine("}");
				
				this.writeLine("function submitUpdateNodes() {");
				this.writeLine("  operation.value = '" + UPDATE_NODES_OPERATION + "';");
				this.writeLine("  var auf;");
				this.writeLine("  var au;");
				this.writeLine("  var af;");
				this.writeLine("  var a;");
				this.writeLine("  var rif;");
				this.writeLine("  var ri;");
				OnnNode[] nodes = getNodes();
				for (int n = 0; n < nodes.length; n++) {
					this.writeLine("  auf = $('" + nodes[n].name + ".accessUrl');");
					this.writeLine("  au = addInput('" + nodes[n].name + "." + ACCESS_URL_PARAMETER + "');");
					this.writeLine("  au.value = auf.value;");
					this.writeLine("  af = $('" + nodes[n].name + ".active');");
					this.writeLine("  a = addInput('" + nodes[n].name + "." + ACTIVE_PARAMETER + "');");
					this.writeLine("  a.value = (af.checked ? 'true' : 'false');");
					this.writeLine("  rif = $('" + nodes[n].name + ".replicationInterval');");
					this.writeLine("  ri = addInput('" + nodes[n].name + "." + REPLICATION_INTERVAL_PARAMETER + "');");
					this.writeLine("  ri.value = rif.value;");
				}
				this.writeLine("  submitForm.submit();");
				this.writeLine("}");
				
				this.writeLine("function submitSetAccessUrl() {");
				this.writeLine("  operation.value = '" + SET_ACCESS_URL_OPERATION + "';");
				this.writeLine("  var auf = $('local.accessUrl');");
				this.writeLine("  var au = addInput('" + ACCESS_URL_PARAMETER + "');");
				this.writeLine("  au.value = auf.value;");
				this.writeLine("  submitForm.submit();");
				this.writeLine("}");
				
				this.writeLine("function submitSetPasscode() {");
				this.writeLine("  operation.value = '" + SET_PASSCODE_OPERATION + "';");
				this.writeLine("  var pcf = $('local.passcode');");
				this.writeLine("  var pc = addInput('" + PASSCODE_PARAMETER + "');");
				this.writeLine("  pc.value = pcf.value;");
				this.writeLine("  var npc1f = $('local.newPasscode1');");
				this.writeLine("  var npc1 = addInput('newPasscode1');");
				this.writeLine("  npc1.value = npc1f.value;");
				this.writeLine("  var npc2f = $('local.newPasscode2');");
				this.writeLine("  var npc2 = addInput('newPasscode2');");
				this.writeLine("  npc2.value = npc2f.value;");
				this.writeLine("  submitForm.submit();");
				this.writeLine("}");
				
				this.writeLine("function submitResetReplication(nodeName) {");
				this.writeLine("  operation.value = '" + RESET_REPLICATION_OPERATION + "';");
				this.writeLine("  var n = addInput('" + NAME_PARAMETER + "');");
				this.writeLine("  n.value = nodeName;");
				this.writeLine("  submitForm.submit();");
				this.writeLine("}");
				
				this.writeLine("function submitPing(nodeName) {");
				this.writeLine("  operation.value = '" + PING_OPERATION + "';");
				this.writeLine("  var n = addInput('" + NAME_PARAMETER + "');");
				this.writeLine("  n.value = nodeName;");
				this.writeLine("  submitForm.submit();");
				this.writeLine("}");
				
				this.writeLine("function submitGetNodes(nodeName) {");
				this.writeLine("  operation.value = '" + GET_NODES_OPERATION + "';");
				this.writeLine("  var n = addInput('" + NAME_PARAMETER + "');");
				this.writeLine("  n.value = nodeName;");
				this.writeLine("  submitForm.submit();");
				this.writeLine("}");
				
				this.writeLine("function addInput(name) {");
				this.writeLine("  var i = document.createElement('input');");
				this.writeLine("  i.name = name;");
				this.writeLine("  i.type = 'hidden';");
				this.writeLine("  submitForm.appendChild(i);");
				this.writeLine("  return i;");
				this.writeLine("}");
				
				this.writeLine("function $(id) {");
				this.writeLine("  return document.getElementById(id);");
				this.writeLine("}");
				
				this.writeLine("</script>");
			}
			
			protected String[] getOnloadCalls() {
				String[] olc = {"init();"};
				return olc;
			}
			
			private void includeBody() throws IOException {
//				this.writeLine("<form style=\"display: 'none';\" action=\"" + this.request.getContextPath() + this.request.getServletPath() + "\" method=\"POST\" id=\"submitForm\">");
				this.writeLine("<form style=\"display: 'none';\" action=\"" + this.request.getContextPath() + this.request.getServletPath() + "/" + ADMIN_ACTION_NAME + "\" method=\"POST\" id=\"submitForm\">");
				this.writeLine("<input type=\"hidden\" name=\"" + ACTION_PARAMETER + "\" value=\"" + ADMIN_ACTION_NAME + "\">");
				this.writeLine("<input type=\"hidden\" name=\"" + OPERATION_PARAMETER + "\" value=\"\" id=\"operation\">");
				this.writeLine("</form>");
				
				//	open main table
				this.writeLine("<table class=\"mainTable\" id=\"mainTable\">");
				
				//	message table
				if ((opResult != null) || (opError != null)) {
					this.writeLine("<tr class=\"mainTableBody\">");
					this.writeLine("<td class=\"mainTableCell\" colspan=\"2\">");
					
					this.writeLine("<table class=\"messageTable\" id=\"messageTable\">");
					this.writeLine("<tr class=\"messageTableHead\">");
					this.writeLine("<td class=\"messageTableCell\">Result of " + opName + "</td>");
					this.writeLine("</tr>");
					if (opResult != null) {
						this.writeLine("<tr class=\"messageTableBody\">");
						this.writeLine("<td class=\"messageTableCell\">" + opResult + "</td>");
						this.writeLine("</tr>");
					}
					if (opError != null) {
						this.writeLine("<tr class=\"messageTableBody\">");
						this.writeLine("<td class=\"messageTableCell\">" + opError + "</td>");
						this.writeLine("</tr>");
					}
					this.writeLine("</table>");
					
					this.writeLine("</td>");
					this.writeLine("</tr>");
				}
				
				//	local node data table
				this.writeLine("<tr class=\"mainTableBody\">");
				this.writeLine("<td class=\"mainTableCell\">");
				
				this.writeLine("<table class=\"nodesTable\" id=\"localNodeTable\">");
				this.writeLine("<tr class=\"nodesTableHead\">");
				this.writeLine("<td class=\"nodesTableCell\" colspan=\"2\"><b>Local Node Configuration</b></td>");
				this.writeLine("</tr>");
				this.writeLine("<tr class=\"nodesTableHead\">");
				this.writeLine("<td class=\"nodesTableCell\">Domain Name</td>");
				this.writeLine("<td class=\"nodesTableCell\">Access URL</td>");
				this.writeLine("</tr>");
				this.writeLine("<tr class=\"nodesTableBody\">");
				this.writeLine("<td class=\"nodesTableCell\">" + domainName + "</td>");
				this.writeLine("<td class=\"nodesTableCell\"><input type=\"text\" class=\"urlInput\" name=\"" + ACCESS_URL_PARAMETER + "\" id=\"local.accessUrl\" value=\"" + accessUrl + "\"></td>");
				this.writeLine("</tr>");
				this.writeLine("<tr class=\"nodesTableBody\">");
				this.writeLine("<td class=\"nodesTableCell\" colspan=\"2\"><input type=\"button\" class=\"button\" onclick=\"submitSetAccessUrl();return false;\" value=\"Set Preferred Access URL\"></td>");
				this.writeLine("</tr>");
				this.writeLine("</table>");
				
				this.writeLine("</td>");
				this.writeLine("<td class=\"mainTableCell\">");
				
				//	passcode table
				this.writeLine("<table class=\"nodesTable\" id=\"passcodeNodeTable\">");
				this.writeLine("<tr class=\"nodesTableHead\">");
				this.writeLine("<td class=\"nodesTableCell\" colspan=\"3\"><b>Authentication for this Page</b></td>");
				this.writeLine("</tr>");
				this.writeLine("<tr class=\"nodesTableHead\">");
				this.writeLine("<td class=\"nodesTableCell\">Passcode</td>");
				this.writeLine("<td class=\"nodesTableCell\">Enter New Passcode</td>");
				this.writeLine("<td class=\"nodesTableCell\">Confirm New Passcode</td>");
				this.writeLine("</tr>");
				this.writeLine("<tr class=\"nodesTableBody\">");
				this.writeLine("<td class=\"nodesTableCell\"><input type=\"password\" class=\"passcodeInput\" name=\"passcode\" id=\"local.passcode\" value=\"\"></td>");
				this.writeLine("<td class=\"nodesTableCell\"><input type=\"password\" class=\"passcodeInput\" name=\"newPasscode1\" id=\"local.newPasscode1\" value=\"\"></td>");
				this.writeLine("<td class=\"nodesTableCell\"><input type=\"password\" class=\"passcodeInput\" name=\"newPasscode2\" id=\"local.newPasscode2\" value=\"\"></td>");
				this.writeLine("</tr>");
				this.writeLine("<tr class=\"nodesTableBody\">");
				this.writeLine("<td class=\"nodesTableCell\" colspan=\"3\"><input type=\"button\" class=\"button\" onclick=\"submitSetPasscode();return false;\" value=\"Set Passcode\"></td>");
				this.writeLine("</tr>");
				this.writeLine("</table>");
				
				this.writeLine("</td>");
				this.writeLine("</tr>");
				
				//	add node data table
				this.writeLine("<tr class=\"mainTableBody\">");
				this.writeLine("<td class=\"mainTableCell\" colspan=\"2\">");
				
				this.writeLine("<table class=\"nodesTable\" id=\"addNodeTable\">");
				this.writeLine("<tr class=\"nodesTableHead\">");
				this.writeLine("<td class=\"nodesTableCell\"><b>Connect to other Nodes</b></td>");
				this.writeLine("</tr>");
				this.writeLine("<tr class=\"nodesTableHead\">");
				this.writeLine("<td class=\"nodesTableCell\">Access URL</td>");
				this.writeLine("</tr>");
				this.writeLine("<tr class=\"nodesTableBody\">");
				this.writeLine("<td class=\"nodesTableCell\"><input type=\"text\" class=\"urlInput\" name=\"" + ACCESS_URL_PARAMETER + "\" id=\"new.accessUrl\" value=\"\"></td>");
				this.writeLine("</tr>");
				this.writeLine("<tr class=\"nodesTableBody\">");
				this.writeLine("<td class=\"nodesTableCell\"><input type=\"button\" class=\"button\" onclick=\"submitAddNode();return false;\" value=\"Add Node\"></td>");
				this.writeLine("</tr>");
				this.writeLine("</table>");
				
				this.writeLine("</td>");
				this.writeLine("</tr>");
				
				//	remote node list
				this.writeLine("<tr class=\"mainTableBody\">");
				this.writeLine("<td class=\"mainTableCell\" colspan=\"2\">");
				
				this.writeLine("<table class=\"nodesTable\" id=\"remoteNodesTable\">");
				
				this.writeLine("<tr class=\"nodesTableHead\">");
				this.writeLine("<td class=\"nodesTableCell\" colspan=\"8\"><b>Connected Nodes</b></td>");
				this.writeLine("</tr>");
				
				this.writeLine("<tr class=\"nodesTableHead\">");
				this.writeLine("<td class=\"nodesTableCell\">Domain Name</td>");
				this.writeLine("<td class=\"nodesTableCell\">Replicate?</td>");
				this.writeLine("<td class=\"nodesTableCell\">Every ...</td>");
				this.writeLine("<td class=\"nodesTableCell\">Last</td>");
				this.writeLine("<td class=\"nodesTableCell\">Reset Replication</td>");
				this.writeLine("<td class=\"nodesTableCell\">Access URL</td>");
				this.writeLine("<td class=\"nodesTableCell\">Ping</td>");
				this.writeLine("<td class=\"nodesTableCell\">Import Infrastructure</td>");
				this.writeLine("</tr>");
				
				OnnNode[] nodes = getNodes();
				for (int n = 0; n < nodes.length; n++) {
					this.writeLine("<tr class=\"nodesTableBody\">");
					this.writeLine("<td class=\"nodesTableCell\">" + nodes[n].name + "</td>");
					this.writeLine("<td class=\"nodesTableCell\"><input type=\"checkbox\" name=\"" + nodes[n].name + ".active\" id=\"" + nodes[n].name + ".active\" value=\"true\"" + (nodes[n].active ? " checked" : "") + "></td>");
					this.writeLine("<td class=\"nodesTableCell\"><input type=\"text\" size=\"5\" name=\"" + nodes[n].name + ".replicationInterval\" id=\"" + nodes[n].name + ".replicationInterval\" value=\"" + nodes[n].updateInterval + "\"> sec</td>");
					this.writeLine("<td class=\"nodesTableCell\">" + df.format(new Date(nodes[n].lastUpdate)) + "</td>");
					this.writeLine("<td class=\"nodesTableCell\"><input type=\"button\" onclick=\"submitResetReplication('" + nodes[n].name + "');return false;\" value=\"Reset\"></td>");
					this.writeLine("<td class=\"nodesTableCell\"><input type=\"text\" class=\"urlInput\" name=\"" + nodes[n].name + ".accessUrl\" id=\"" + nodes[n].name + ".accessUrl\" value=\"" + nodes[n].accessUrl + "\"></td>");
					this.writeLine("<td class=\"nodesTableCell\"><input type=\"button\" onclick=\"submitPing('" + nodes[n].name + "');return false;\" value=\"Ping\"></td>");
					this.writeLine("<td class=\"nodesTableCell\"><input type=\"button\" onclick=\"submitGetNodes('" + nodes[n].name + "');return false;\" value=\"Import\"></td>");
					this.writeLine("</tr>");
				}
				
				this.writeLine("<tr class=\"nodesTableBody\">");
				this.writeLine("<td class=\"nodesTableCell\" colspan=\"8\"><input type=\"button\" class=\"button\" onclick=\"submitUpdateNodes();return false;\" value=\"Update Nodes\"></td>");
				this.writeLine("</tr>");
				
				this.writeLine("</table>");
				
				this.writeLine("</td>");
				this.writeLine("</tr>");
				
				this.writeLine("</table>");
			}
		};
		this.sendHtmlPage("onnNodeAdminPage.html", adminPageBuilder);
	}
	private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'GMT'Z", Locale.US);
	
	private void writeNodes(OnnNode[] nodes, BufferedWriter bw, boolean includeSelf) throws IOException {
		if ((nodes.length == 0) && !includeSelf) {
			bw.write("<" + NODES_NODE_TYPE);
			bw.write(ONN_XML_NAMESPACE_ATTRIBUTE);
			bw.write("/>");
			return;
		}
		
		bw.write("<" + NODES_NODE_TYPE);
		bw.write(ONN_XML_NAMESPACE_ATTRIBUTE);
		bw.write(">");
		bw.newLine();
		if (includeSelf) {
			bw.write("<" + NODE_NODE_TYPE);
			bw.write(" " + NAME_ATTRIBUTE + "=\"" + this.domainName + "\"");
			bw.write(" " + ACCESS_URL_ATTRIBUTE + "=\"" + this.accessUrl + "\"");
			bw.write("/>");
		}
		for (int n = 0; n < nodes.length; n++) {
			if (this.domainName.equals(nodes[n].name) && includeSelf)
				continue;
			bw.write("<" + NODE_NODE_TYPE);
			bw.write(" " + NAME_ATTRIBUTE + "=\"" + nodes[n].name + "\"");
			bw.write(" " + ACCESS_URL_ATTRIBUTE + "=\"" + nodes[n].accessUrl + "\"");
			bw.write("/>");
			bw.newLine();
		}
		bw.write("</" + NODES_NODE_TYPE + ">");
		bw.newLine();
	}
	
	/**
	 * Object representing a connected node.
	 * 
	 * @author sautter
	 */
	public static class OnnNode {
		/** the domain name of the node */
		public final String name;
		/** the access URL of the node */
		public final String accessUrl;
		private boolean active = false;
		private long lastUpdate = 0;
		private long lastAttemptedLookup;
		private int updateInterval = 3600;
		public OnnNode(String name, String accessUrl) {
			this.accessUrl = accessUrl;
			this.name = name;
		}
		OnnNode(String accessUrl) throws IOException {
			this.accessUrl = accessUrl;
			this.name = this.getName();
		}
		boolean ping() {
			try {
//				URL pingUrl = new URL(this.accessUrl + "?" + ACTION_PARAMETER + "=" + PING_ACTION_NAME);
				URL pingUrl = new URL(this.accessUrl + "/" + PING_ACTION_NAME + "?" + ACTION_PARAMETER + "=" + PING_ACTION_NAME);
				BufferedReader br = new BufferedReader(new InputStreamReader(pingUrl.openStream(), ENCODING));
				String pong = br.readLine();
				br.close();
				return ((pong != null) && pong.startsWith("<" + NODES_NODE_TYPE));
			}
			catch (IOException ioe) {
				return false;
			}
		}
		OnnNode[] getNodes() {
			try {
//				URL nodesUrl = new URL(this.accessUrl + "?" + ACTION_PARAMETER + "=" + NODES_ACTION_NAME);
				URL nodesUrl = new URL(this.accessUrl + "/" + NODES_ACTION_NAME + "?" + ACTION_PARAMETER + "=" + NODES_ACTION_NAME);
				BufferedReader br = new BufferedReader(new InputStreamReader(nodesUrl.openStream(), ENCODING));
				return readNodes(br);
			}
			catch (IOException ioe) {
				return null;
			}
		}
		OnnNode[] introduceTo(String domainName, String accessUrl) {
			try {
//				URL introduceUrl = new URL(this.accessUrl);
				URL introduceUrl = new URL(this.accessUrl + "/" + INTRODUCE_ACTION_NAME);
				HttpURLConnection con = ((HttpURLConnection) introduceUrl.openConnection());
				con.setRequestMethod("POST");
				con.setDoOutput(true);
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(con.getOutputStream(), ENCODING));
				bw.write(ACTION_PARAMETER + "=" + INTRODUCE_ACTION_NAME);
				bw.write("&" + NAME_PARAMETER + "=" + URLEncoder.encode(domainName, ENCODING));
				bw.write("&" + ACCESS_URL_PARAMETER + "=" + URLEncoder.encode(accessUrl, ENCODING));
				bw.flush();
				BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), ENCODING));
				return readNodes(br);
			}
			catch (IOException ioe) {
				return null;
			}
		}
		private String getName() throws IOException {
//			URL nodesUrl = new URL(this.accessUrl + "?" + ACTION_PARAMETER + "=" + NAME_ACTION_NAME);
			URL nodesUrl = new URL(this.accessUrl + "/" + NAME_ACTION_NAME + "?" + ACTION_PARAMETER + "=" + NAME_ACTION_NAME);
			BufferedReader br = new BufferedReader(new InputStreamReader(nodesUrl.openStream(), ENCODING));
			OnnNode[] nodes = readNodes(br);
			if (nodes.length == 1)
				return nodes[0].name;
			else throw new IOException("Could not retrieve remote domain name.");
		}
		private static final OnnNode[] readNodes(BufferedReader br) {
			try {
				final ArrayList nodes = new ArrayList();
				xmlParser.stream(br, new TokenReceiver() {
					public void close() throws IOException {}
					public void storeToken(String token, int treeDepth) throws IOException {
						if (xmlGrammar.isTag(token)) {
							String type = xmlGrammar.getType(token);
							type = type.substring(type.indexOf(':') + 1);
							if (!NODE_NODE_TYPE.equals(type))
								return;
							TreeNodeAttributeSet nodeData = TreeNodeAttributeSet.getTagAttributes(token, xmlGrammar);
							String name = nodeData.getAttribute(NAME_ATTRIBUTE);
							String accessUrl = nodeData.getAttribute(ACCESS_URL_ATTRIBUTE);
							if ((name != null) && (accessUrl != null))
								nodes.add(new OnnNode(name, accessUrl));
						}
					}
				});
				br.close();
				return ((OnnNode[]) nodes.toArray(new OnnNode[nodes.size()]));
			}
			catch (IOException ioe) {
				return null;
			}
		}
	}
	
	private NAR addNodes(OnnNode[] nodes) throws IOException {
		int nnc = 0;
		int unc = 0;
		for (int n = 0; n < nodes.length; n++) {
			if (this.domainName.equals(nodes[n].name))
				continue;
			OnnNode eNode = this.getNode(nodes[n].name);
			if ((eNode != null) && (eNode.accessUrl.equals(nodes[n].accessUrl)))
				continue;
			else if (eNode == null) {
				this.nodes.put(nodes[n].name, nodes[n]);
				this.storeNode(nodes[n]);
				nnc++;
			}
			else {
				OnnNode uNode = new OnnNode(nodes[n].name, nodes[n].accessUrl);
				uNode.updateInterval = eNode.updateInterval;
				uNode.lastUpdate = eNode.lastUpdate;
				uNode.active = eNode.active;
				this.nodes.put(uNode.name, uNode);
				this.storeNode(uNode);
				unc++;
			}
		}
		return new NAR(nnc, unc);
	}
	private static class NAR {
		final int newNodes;
		final int updatedNodes;
		NAR(int newNodes, int updatedNodes) {
			this.newNodes = newNodes;
			this.updatedNodes = updatedNodes;
		}
	}
	
	private static final String DOMAIN_NAME_SETTING = "domainName";
	private static final String ACCESS_URL_SETTING = "accessUrl";
	private static final String UPDATE_INTERVAL_SETTING = "updateInterval";
	private static final String LAST_UPDATE_SETTING = "lastUpdate";
	private static final String ACTIVE_SETTING = "active";
	private void storeNode(OnnNode node) throws IOException {
		Settings resData = new Settings();
		resData.setSetting(DOMAIN_NAME_SETTING, node.name);
		resData.setSetting(ACCESS_URL_SETTING, node.accessUrl);
		resData.setSetting(UPDATE_INTERVAL_SETTING, ("" + node.updateInterval));
		resData.setSetting(LAST_UPDATE_SETTING, ("" + node.lastUpdate));
		resData.setSetting(ACTIVE_SETTING, (node.active ? "true" : "false"));
		resData.storeAsText(this.getResFile(node));
	}
	private File getResFile(OnnNode node) {
		return new File(this.dataFolder, (node.name.replaceAll("[^a-zA-Z]", "_") + ".node.cnfg"));
	}
}
