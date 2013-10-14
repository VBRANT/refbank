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
package de.uka.ipd.idaho.refBank.apps.webInterface;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.TreeMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import de.uka.ipd.idaho.easyIO.settings.Settings;
import de.uka.ipd.idaho.gamta.QueriableAnnotation;
import de.uka.ipd.idaho.gamta.util.SgmlDocumentReader;
import de.uka.ipd.idaho.htmlXmlUtil.TreeNodeAttributeSet;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.XsltUtils;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.XsltUtils.IsolatorWriter;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient.PooledString;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient.PooledStringIterator;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefTypeSystem;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefTypeSystem.BibRefType;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils.RefData;
import de.uka.ipd.idaho.refBank.RefBankClient;

/**
 * Search facility for RefBank.
 * 
 * @author sautter
 */
public class RefBankSearchServlet extends RefBankWiServlet {
	
	private static final String FRAME_PAGE_PATH = "fr";
	
	private static final String YEAR_PARAMETER = "year";
	
	private TreeMap formats = new TreeMap(String.CASE_INSENSITIVE_ORDER);
	private Properties formatExportMimeTypes = new Properties();
	private Properties formatExportFileExtensions = new Properties();
	
	private static final String PARSE_REF_FORMAT = "PaRsEtHeReF";
	private static final String EDIT_REF_FORMAT = "EdItReFsTrInG";
	
	private static final String SEARCH_RESULT_PATH = "result.js";
	
	private static final String MINOR_UPDATE_PATH = "mu";
	
	private static final String MINOR_UPDATE_FRAME_ID = "minorUpdateFrame";
	private static final String MINOR_UPDATE_FORM_ID = "minorUpdateForm";
	private static final String MINOR_UPDATE_RESULT_ATTRIBUTE = "minorUpdateResult";
	
	private static final String DELETED_PARAMETER = "deleted";
	
	private static final String EXPORT_BASKET_UPDATE_PATH = "ebu";
	private static final String EXPORT_BASKET_SHOW_PATH = "ebs";
	private static final String EXPORT_BASKET_DOWNLOAD_PATH = "ebd";
	
	private static final String EXPORT_BASKET_ATTRIBUTE = "exportBasket";
	
	private static final String EXPORT_BASKET_UPDATE_FRAME_ID = "exportBasketUpdateFrame";
	private static final String EXPORT_BASKET_UPDATE_FORM_ID = "exportBasketUpdateForm";
	
	private static final String EXPORT_BASKET_UPDATE_RESULT_ATTRIBUTE = "exportBasketAddResult";
	
	private String refParserUrl = null;
	private String refEditorUrl = null;
	
	private String styledRefLayout = "font-face: Times; font-size: 10pt;";
	
	private BibRefTypeSystem refTypeSystem = null;
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolAppServlet#doInit()
	 */
	protected void doInit() throws ServletException {
		super.doInit();
		
		//	get type system
		String refTypeSystemPath = this.getSetting("refTypeSystemPath");
		this.refTypeSystem = ((refTypeSystemPath == null) ? BibRefTypeSystem.getDefaultInstance() : BibRefTypeSystem.getInstance(new File(this.webInfFolder, refTypeSystemPath), true));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.apps.RefBankAppServlet#reInit()
	 */
	protected void reInit() throws ServletException {
		super.reInit();
		
		//	read available data formats and respective XSL transformers
		this.formats.clear();
		Settings formats = this.config.getSubset("format");
		String[] formatNames = formats.getKeys();
		for (int f = 0; f < formatNames.length; f++) {
			String xsltName = formats.getSetting(formatNames[f]);
			try {
				Transformer xslt = XsltUtils.getTransformer(new File(this.dataFolder, xsltName), false);
				this.formats.put(formatNames[f], xslt);
			} catch (IOException ioe) {}
		}
		
		//	read additional settings for export basket download
		this.formatExportMimeTypes.clear();
		this.formatExportFileExtensions.clear();
		Settings export = this.config.getSubset("export");
		for (int f = 0; f < formatNames.length; f++) {
			String mimeType = export.getSetting(formatNames[f] + ".mimeType");
			if (mimeType != null)
				this.formatExportMimeTypes.setProperty(formatNames[f], mimeType);
			String fileExtension = export.getSetting(formatNames[f] + ".fileExtension");
			if (fileExtension != null)
				this.formatExportFileExtensions.setProperty(formatNames[f], fileExtension);
		}
		
		//	load reference string styles
		Settings styles = this.config.getSubset("style");
		String[] styleNames = styles.getKeys();
		for (int s = 0; s < styleNames.length; s++) {
			String xsltName = styles.getSetting(styleNames[s]);
			try {
				Transformer xslt = XsltUtils.getTransformer(new File(this.dataFolder, xsltName), false);
				BibRefUtils.addRefStringStyle(styleNames[s], xslt);
			} catch (IOException ioe) {}
		}
		
		//	get layout for styled references
		this.styledRefLayout = this.getSetting("styledRefLayout", this.styledRefLayout);
		
		//	get link to reference parser and reference string editor
		this.refParserUrl = this.getSetting("refParserUrl");
		this.refEditorUrl = this.getSetting("refEditorUrl");
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	determine action
		String pathInfo = request.getPathInfo();
		if (pathInfo != null) {
			while (pathInfo.startsWith("/"))
				pathInfo = pathInfo.substring(1);
		}
		
		//	minor update
		if (MINOR_UPDATE_PATH.equals(pathInfo)) {
			String id = request.getParameter(STRING_ID_ATTRIBUTE);
			String canonicalId = request.getParameter(CANONICAL_STRING_ID_ATTRIBUTE);
			String deleted = request.getParameter(DELETED_PARAMETER);
			String user = request.getParameter(USER_PARAMETER);
			if (user == null)
				user = "Anonymous";
			String result = "OK";
			
			try {
				
				//	make reference cluster representative
				if ((canonicalId != null) && (canonicalId.length() != 0)) {
					RefBankClient rbc = this.getRefBankClient();
					
					//	update representative of cluster
					if ((id == null) || (id.length() == 0)) {
						PooledString rps = rbc.getString(canonicalId);
						if (rps == null)
							throw new IOException();
						System.out.println("Making reference " + canonicalId + " representative of cluster " + rps.getCanonicalStringID());
						PooledStringIterator psi = rbc.getLinkedStrings(rps.getCanonicalStringID());
						if (psi.getException() != null)
							throw psi.getException();
						ArrayList refIDs = new ArrayList();
						while (psi.hasNextString())
							refIDs.add(psi.getNextString().id);
						for (Iterator idit = refIDs.iterator(); idit.hasNext();)
							rbc.setCanonicalStringId(((String) idit.next()), canonicalId, user);
					}
					
					//	removal from cluster
					else if (id.equals(canonicalId)) {
						System.out.println("Making reference " + id + " self-representative");
						rbc.setCanonicalStringId(id, canonicalId, user);
					}
					
					//	addition to cluster
					else {
						System.out.println("Adding reference " + id + " to cluster " + canonicalId);
						PooledStringIterator psi = rbc.getLinkedStrings(id);
						if (psi.getException() != null)
							throw psi.getException();
						ArrayList refIDs = new ArrayList();
						while (psi.hasNextString())
							refIDs.add(psi.getNextString().id);
						for (Iterator idit = refIDs.iterator(); idit.hasNext();)
							rbc.setCanonicalStringId(((String) idit.next()), canonicalId, user);
					}
				}
				
				//	delete or undelete reference
				else if ((id != null) && (deleted != null)) {
					System.out.println("Setting deletion status of reference " + id + " to " + deleted);
					RefBankClient rbc = this.getRefBankClient();
					rbc.setDeleted(id, "true".equals(deleted), user);
				}
			}
			
			//	catch exceptions and indicate failure to client
			catch (IOException ioe) {
				result = "FAIL";
			}
			
			//	send back form for next minor update
			this.sendMinorUpdateForm(request, response, result);
			return;
		}
		
		//	export basket update
		if (EXPORT_BASKET_UPDATE_PATH.equals(pathInfo)) {
			String id = request.getParameter(STRING_ID_ATTRIBUTE);
			String result;
			if (id != null) {
				
				//	removal
				if (id.startsWith("--")) {
					LinkedHashSet exportBasket = this.getExportBasket(request, false);
					if (exportBasket != null) {
						exportBasket.remove(id.substring("--".length()));
						result = ("" + exportBasket.size());
					}
					else result = "0";
				}
				
				//	addition
				else {
					LinkedHashSet exportBasket = this.getExportBasket(request, true);
					exportBasket.add(id);
					result = ("" + exportBasket.size());
				}
			}
			else result = "0";
			
			//	send back form for next addition
			this.sendExportBasketUpdateForm(request, response, result);
			return;
		}
	}
	
	private LinkedHashSet getExportBasket(HttpServletRequest request, boolean create) {
		HttpSession session = request.getSession(create);
		if (session == null)
			return null;
		LinkedHashSet exportBasket = ((LinkedHashSet) session.getAttribute(EXPORT_BASKET_ATTRIBUTE));
		if (exportBasket == null) {
			exportBasket = new LinkedHashSet();
			session.setAttribute(EXPORT_BASKET_ATTRIBUTE, exportBasket);
		}
		return exportBasket;
	}
	
	private void sendExportBasketUpdateForm(HttpServletRequest request, HttpServletResponse response, String result) throws IOException {
		response.setContentType("text/html");
		response.setCharacterEncoding(ENCODING);
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
		bw.write("<html><head>");bw.newLine();
		HtmlPageBuilder.writeJavaScriptDomHelpers(bw);
		if (result != null) {
			bw.write("<script type=\"text/javascript\">");bw.newLine();
			bw.write("function exportBasketUpdateResultRead() {");bw.newLine();
			bw.write("  var aebr = getById('" + EXPORT_BASKET_UPDATE_RESULT_ATTRIBUTE + "');");bw.newLine();
			bw.write("  if (aebr != null)");bw.newLine();
			bw.write("    removeElement(aebr);");bw.newLine();
			bw.write("  var ebuf = getById('" + EXPORT_BASKET_UPDATE_FORM_ID + "');");bw.newLine();
			bw.write("  ebuf.appendChild(createExportBasketUpdateFormField('" + STRING_ID_ATTRIBUTE + "'));");bw.newLine();
			bw.write("}");bw.newLine();
			bw.write("function createExportBasketUpdateFormField(nameAndId) {");bw.newLine();
			bw.write("  var input = newElement('input', null, null, null);");bw.newLine();
			bw.write("  setAttribute(input, 'type', 'hidden');");bw.newLine();
			bw.write("  setAttribute(input, 'name', nameAndId);");bw.newLine();
			bw.write("  setAttribute(input, 'id', nameAndId);");bw.newLine();
			bw.write("  return input;");bw.newLine();
			bw.write("}");bw.newLine();
			bw.write("</script>");bw.newLine();
		}
		bw.write("</head><body>");bw.newLine();
		bw.write("<form id=\"" + EXPORT_BASKET_UPDATE_FORM_ID + "\" method=\"POST\" action=\"" + request.getContextPath() + request.getServletPath() + "/" + EXPORT_BASKET_UPDATE_PATH + "\">");
		if (result == null)
			bw.write("<input type=\"hidden\" name=\"" + STRING_ID_ATTRIBUTE + "\" value=\"\" id=\"" + STRING_ID_ATTRIBUTE + "\">");
		else bw.write("<input type=\"hidden\" name=\"" + EXPORT_BASKET_UPDATE_RESULT_ATTRIBUTE + "\" value=\"" + result + "\" id=\"" + EXPORT_BASKET_UPDATE_RESULT_ATTRIBUTE + "\">");
		bw.write("</form>");bw.newLine();
		bw.write("</body></html>");bw.newLine();
		bw.flush();
	}
	
	private void sendMinorUpdateForm(HttpServletRequest request, HttpServletResponse response, String result) throws IOException {
		response.setContentType("text/html");
		response.setCharacterEncoding(ENCODING);
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
		bw.write("<html><head>");bw.newLine();
		HtmlPageBuilder.writeJavaScriptDomHelpers(bw);
		if (result != null) {
			bw.write("<script type=\"text/javascript\">");bw.newLine();
			bw.write("function minorUpdateResultRead() {");bw.newLine();
			bw.write("  var mur = getById('" + MINOR_UPDATE_RESULT_ATTRIBUTE + "');");bw.newLine();
			bw.write("  if (mur != null)");bw.newLine();
			bw.write("    removeElement(mur);");bw.newLine();
			bw.write("  var muf = getById('" + MINOR_UPDATE_FORM_ID + "');");bw.newLine();
			bw.write("  muf.appendChild(createMinorUpdateFormField('" + CANONICAL_STRING_ID_ATTRIBUTE + "'));");bw.newLine();
			bw.write("  muf.appendChild(createMinorUpdateFormField('" + STRING_ID_ATTRIBUTE + "'));");bw.newLine();
			bw.write("  muf.appendChild(createMinorUpdateFormField('" + DELETED_PARAMETER + "'));");bw.newLine();
			bw.write("  muf.appendChild(createMinorUpdateFormField('" + USER_PARAMETER + "'));");bw.newLine();
			bw.write("}");bw.newLine();
			bw.write("function createMinorUpdateFormField(nameAndId) {");bw.newLine();
			bw.write("  var input = newElement('input', null, null, null);");bw.newLine();
			bw.write("  setAttribute(input, 'type', 'hidden');");bw.newLine();
			bw.write("  setAttribute(input, 'name', nameAndId);");bw.newLine();
			bw.write("  setAttribute(input, 'id', nameAndId);");bw.newLine();
			bw.write("  return input;");bw.newLine();
			bw.write("}");bw.newLine();
			bw.write("</script>");bw.newLine();
		}
		bw.write("</head><body>");bw.newLine();
		bw.write("<form id=\"" + MINOR_UPDATE_FORM_ID + "\" method=\"POST\" action=\"" + request.getContextPath() + request.getServletPath() + "/" + MINOR_UPDATE_PATH + "\">");
		if (result == null) {
			bw.write("<input type=\"hidden\" name=\"" + CANONICAL_STRING_ID_ATTRIBUTE + "\" value=\"\" id=\"" + CANONICAL_STRING_ID_ATTRIBUTE + "\">");
			bw.write("<input type=\"hidden\" name=\"" + STRING_ID_ATTRIBUTE + "\" value=\"\" id=\"" + STRING_ID_ATTRIBUTE + "\">");
			bw.write("<input type=\"hidden\" name=\"" + DELETED_PARAMETER + "\" value=\"\" id=\"" + DELETED_PARAMETER + "\">");
			bw.write("<input type=\"hidden\" name=\"" + USER_PARAMETER + "\" value=\"\" id=\"" + USER_PARAMETER + "\">");
		}
		else bw.write("<input type=\"hidden\" name=\"" + MINOR_UPDATE_RESULT_ATTRIBUTE + "\" value=\"" + result + "\" id=\"" + MINOR_UPDATE_RESULT_ATTRIBUTE + "\">");
		bw.write("</form>");bw.newLine();
		bw.write("</body></html>");bw.newLine();
		bw.flush();
	}
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	determine action
		String pathInfo = request.getPathInfo();
		if (pathInfo != null) {
			while (pathInfo.startsWith("/"))
				pathInfo = pathInfo.substring(1);
		}
		
		//	request for minor update form
		if (MINOR_UPDATE_PATH.equals(pathInfo)) {
			this.sendMinorUpdateForm(request, response, null);
			return;
		}
		
		//	request for export basket update form
		if (EXPORT_BASKET_UPDATE_PATH.equals(pathInfo)) {
			this.sendExportBasketUpdateForm(request, response, null);
			return;
		}
		
		//	request for export basket content
		if (EXPORT_BASKET_SHOW_PATH.equals(pathInfo)) {
			this.sendPopupHtmlPage(this.getExportBasketPageBuilder(request, response));
			return;
		}
		
		//	request for export basket content download
		if (EXPORT_BASKET_DOWNLOAD_PATH.equals(pathInfo)) {
			String format = request.getParameter(FORMAT_PARAMETER);
			String style = request.getParameter(STYLE_PARAMETER);
			if (style != null)
				this.sendStyledExportBasket(request, style, response);
			else this.sendFormattedExportBasket(request, ((format == null) ? "MODS" : format), response);
			return;
		}
		
		//	request for formatted reference frame
		if (FRAME_PAGE_PATH.equals(pathInfo)) {
			String id = request.getParameter(STRING_ID_ATTRIBUTE);
			String format = request.getParameter(FORMAT_PARAMETER);
			if ((format != null) && (format.trim().length() == 0))
				format = null;
			String style = request.getParameter(STYLE_PARAMETER);
			if ((style != null) && (style.trim().length() == 0))
				style = null;
			this.sendFormattedReferenceFrame(request, id, format, style, response);
			return;
		}
		
		// reference id plus format or style ==> reference in special format
		String id = request.getParameter(STRING_ID_ATTRIBUTE);
		String format = request.getParameter(FORMAT_PARAMETER);
		if ((format != null) && (format.trim().length() == 0))
			format = null;
		String style = request.getParameter(STYLE_PARAMETER);
		if ((style != null) && (style.trim().length() == 0))
			style = null;
		if (id != null) {
			if ((format != null) || (style != null)) {
				if (style != null)
					this.sendStyledReference(request, id, style, response);
				else this.sendFormattedReference(request, id, ((format == null) ? "MODS" : format), response);
				return;
			}
		}
		
		//	get search parameters
		String query = request.getParameter(QUERY_PARAMETER);
		String type = request.getParameter(TYPE_PARAMETER);
		String user = request.getParameter(USER_PARAMETER);
		String author = request.getParameter(AUTHOR_PARAMETER);
		String title = request.getParameter(TITLE_PARAMETER);
		String year = request.getParameter(DATE_PARAMETER);
		if (year == null) year = request.getParameter(YEAR_PARAMETER);
		String origin = request.getParameter(ORIGIN_PARAMETER);
		String idType = request.getParameter(ID_TYPE_PARAMETER);
		String idValue = request.getParameter(ID_VALUE_PARAMETER);
		
		//	request for search result rendering calls
		if (SEARCH_RESULT_PATH.equals(pathInfo)) {
			
			//	query parameters and perform search if given
			PooledStringIterator psi = null;
			RefBankClient rbc = null;
			
			//	perform search if query given;
			if ((query != null) || (author != null) || (title != null) || (year != null) || (origin != null) || ((idType != null) && (idValue != null))) {
				int yearInt = -1;
				if (year != null) try {
					yearInt = Integer.parseInt(year);
				} catch (NumberFormatException nfe) {}
				Properties ids = null;
				if ((idType != null) && (idValue != null)) {
					ids = new Properties();
					ids.setProperty(idType, idValue);
				}
				String[] textPredicates = {};
				if (query != null) {
					textPredicates = new String[1];
					textPredicates[0] = query;
				}
				rbc = this.getRefBankClient(); //	retrieve RefBank client on the fly to use local bridge if possible
				psi = rbc.findReferences(textPredicates, false, type, user, author, title, yearInt, origin, ids, true, 0, false);
//				if (psi.getException() != null)
//					throw psi.getException();
			}
			
			//	send result
			response.setContentType("text/plain");
			response.setCharacterEncoding(ENCODING);
			BufferedWriter srw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
			
			//	no query at all
			if (psi == null) {
				srw.write("alert('Please specify search parameters!');");
				srw.newLine();
			}
			
			//	search error
			else if (psi.getException() != null) {
				srw.write("alert('An error occurred while ecexuting your request, sorry:\\n  " + psi.getException().getMessage() + "\\nPlease try again later or modify your search parameters!');");
				srw.newLine();
			}
			
			//	send references found (self-canonical ones first)
			else {
				HashSet canonicalRefIDs = new HashSet();
				ArrayList duplicateRefs = new ArrayList();
				while (psi.hasNextString()) {
					PooledString ps = psi.getNextString();
					if (ps.id.equals(ps.getCanonicalStringID())) {
						srw.write("displayResultRef('" + ps.id + "', '" + this.escapeForJavaScript(ps.getStringPlain()) + "', '" + ps.getCreateUser() + "', '" + ps.getCreateDomain() + "', '" + ps.getUpdateUser() + "', '" + ps.getUpdateDomain() + "', " + (ps.getParseChecksum() != null) + ", " + ps.isDeleted() + ", '" + ps.getCanonicalStringID() + "');");
						srw.newLine();
						canonicalRefIDs.add(ps.id);
					}
					else duplicateRefs.add(ps);
				}
				for (int d = 0; d < duplicateRefs.size(); d++) {
					PooledString ps = ((PooledString) duplicateRefs.get(d));
					if (!canonicalRefIDs.contains(ps.getCanonicalStringID())) {
						PooledString rps = rbc.getString(ps.getCanonicalStringID());
						if (rps != null) {
							srw.write("displayResultRef('" + rps.id + "', '" + this.escapeForJavaScript(rps.getStringPlain()) + "', '" + rps.getCreateUser() + "', '" + rps.getCreateDomain() + "', '" + rps.getUpdateUser() + "', '" + rps.getUpdateDomain() + "', " + (rps.getParseChecksum() != null) + ", " + rps.isDeleted() + ", '" + rps.getCanonicalStringID() + "');");
							srw.newLine();
							canonicalRefIDs.add(rps.id);
						}
					}
					srw.write("displayResultRef('" + ps.id + "', '" + this.escapeForJavaScript(ps.getStringPlain()) + "', '" + ps.getCreateUser() + "', '" + ps.getCreateDomain() + "', '" + ps.getUpdateUser() + "', '" + ps.getUpdateDomain() + "', " + (ps.getParseChecksum() != null) + ", " + ps.isDeleted() + ", '" + ps.getCanonicalStringID() + "');");
					srw.newLine();
				}
				srw.write("updateSearchResultStats('" + rbc.getStringCount(-1) + "');");
				srw.newLine();
			}
			
			srw.flush();
		}
		
		//	request for search page proper
		else {
			HtmlPageBuilder pageBuilder = this.getSearchPageBuilder(request, /*psi,*/ query, type, author, title, year, origin, response);
			this.sendHtmlPage(pageBuilder);
		}
	}
	
	private String escapeForJavaScript(String str) {
		StringBuffer escaped = new StringBuffer();
		char ch;
		for (int c = 0; c < str.length(); c++) {
			ch = str.charAt(c);
			if ((ch == '\\') || (ch == '\''))
				escaped.append('\\');
			if (ch < 32)
				escaped.append(' ');
			else escaped.append(ch);
		}
		return escaped.toString();
	}
	
	private HtmlPageBuilder getExportBasketPageBuilder(HttpServletRequest request, HttpServletResponse response) throws IOException {
		final LinkedHashSet exportBasket = this.getExportBasket(request, false);
		RefBankClient rbk = this.getRefBankClient();
		final PooledStringIterator psi = (((exportBasket == null) || exportBasket.isEmpty()) ? null : rbk.getStrings((String[]) exportBasket.toArray(new String[exportBasket.size()])));
		response.setContentType("text/html");
		response.setCharacterEncoding(ENCODING);
		return new HtmlPageBuilder(this, request, response) {
			protected void include(String type, String tag) throws IOException {
				if ("includeBody".equals(type))
					this.includeExportBasket();
				else super.include(type, tag);
			}
			private void includeExportBasket() throws IOException {
				this.writeLine("<table class=\"resultTable\" id=\"resultTable\">");
				
				//	indicate empty basket
				if ((psi == null) || !psi.hasNextString()) {
					this.writeLine("<tr class=\"resultTableRow\">");
					this.writeLine("<td class=\"resultTableCell\">");
					this.writeLine("There are currently no references in your Export Basket.");
					this.writeLine("</td>");
					this.writeLine("</tr>");
				}
				
				//	list basket contents
				else {
					
					//	add exporter buttons
					this.writeLine("<tr class=\"resultTableRow\" id=\"topFunctionRow\">");
					this.writeLine("<td class=\"resultTableCell\" colspan=\"2\">");
					this.writeLine("<span class=\"referenceFormatLinkLabel\">Export References as</span>");
					for (Iterator fit = formats.keySet().iterator(); fit.hasNext();) {
						String format = ((String) fit.next());
						this.writeLine("<a" + 
								" class=\"referenceFormatLink\"" + 
//								" target=\"_blank\"" + 
								" title=\"Download these references formatted as " + format + "\"" + 
								" href=\"" + this.request.getContextPath() + this.request.getServletPath() + "/" + EXPORT_BASKET_DOWNLOAD_PATH + "?" + FORMAT_PARAMETER + "=" + format + "\"" + 
								">" + format + "</a>");
					}
					this.writeLine("&nbsp;&nbsp;");
					String[] styles = BibRefUtils.getRefStringStyles();
					for (int s = 0; s < styles.length; s++) {
						this.writeLine("<a" + 
								" class=\"referenceFormatLink\"" + 
//								" target=\"_blank\"" + 
								" title=\"Download these references styled as " + styles[s] + " style\"" + 
								" href=\"" + this.request.getContextPath() + this.request.getServletPath() + "/" + EXPORT_BASKET_DOWNLOAD_PATH + "?" + STYLE_PARAMETER + "=" + styles[s] + "\"" + 
								">" + styles[s] + "</a>");
					}
					this.writeLine("</td>");
					this.writeLine("</tr>");
					
					//	display references
					while (psi.hasNextString()) {
						PooledString ps = psi.getNextString();
						this.writeLine("<tr class=\"resultTableRow\" id=\"ref" + ps.id + "\">");
						this.writeLine("<td class=\"resultTableCell\">");
						this.writeLine("<div class=\"referenceString\">" + xmlGrammar.escape(ps.getStringPlain()) + "</div>");
						this.writeLine("</td>");
						this.writeLine("<td class=\"resultTableCell\">");
						this.writeLine("<input" + 
								" class=\"referenceFormatLink\"" + 
								" type=\"button\"" + 
								" value=\"Remove\"" + 
								" title=\"Remove this reference from your Export Basket\"" + 
								" onclick=\"return updateExportBasket('" + ps.id + "', true);\"" + 
								">");
						this.writeLine("</td>");
						this.writeLine("</tr>");
					}
					
					//	add exporter buttons once again
					this.writeLine("<tr class=\"resultTableRow\" id=\"bottomFunctionRow\">");
					this.writeLine("<td class=\"resultTableCell\" colspan=\"2\">");
					this.writeLine("<span class=\"referenceFormatLinkLabel\">Export References as</span>");
					for (Iterator fit = formats.keySet().iterator(); fit.hasNext();) {
						String format = ((String) fit.next());
						this.writeLine("<a" + 
								" class=\"referenceFormatLink\"" + 
//								" target=\"_blank\"" + 
								" title=\"Download these references formatted as " + format + "\"" + 
								" href=\"" + this.request.getContextPath() + this.request.getServletPath() + "/" + EXPORT_BASKET_DOWNLOAD_PATH + "?" + FORMAT_PARAMETER + "=" + format + "\"" + 
								">" + format + "</a>");
					}
					this.writeLine("&nbsp;&nbsp;");
					for (int s = 0; s < styles.length; s++) {
						this.writeLine("<a" + 
								" class=\"referenceFormatLink\"" + 
//								" target=\"_blank\"" + 
								" title=\"Download these references styled as " + styles[s] + " style\"" + 
								" href=\"" + this.request.getContextPath() + this.request.getServletPath() + "/" + EXPORT_BASKET_DOWNLOAD_PATH + "?" + STYLE_PARAMETER + "=" + styles[s] + "\"" + 
								">" + styles[s] + "</a>");
					}
					this.writeLine("</td>");
					this.writeLine("</tr>");
				}
				this.writeLine("</table>");
				
				this.write("<iframe id=\"" + EXPORT_BASKET_UPDATE_FRAME_ID + "\" height=\"0px\" style=\"border-width: 0px;\" src=\"" + this.request.getContextPath() + this.request.getServletPath() + "/" + EXPORT_BASKET_UPDATE_PATH + "\">");
				this.writeLine("</iframe>");
			}
			protected void writePageHeadExtensions() throws IOException {
				this.writeLine("<script type=\"text/javascript\">");
				writeExportBasketFunctions(this, true, false);
				this.writeLine("</script>");
			}
		};
	}
	
	private void writeExportBasketFunctions(HtmlPageBuilder hpb, boolean inBasketPage, boolean inFormattedRefPage) throws IOException {
		LinkedHashSet exportBasket = this.getExportBasket(hpb.request, false);
		
		hpb.writeLine("var exportBasketUpdateOverlay = null;");
		hpb.writeLine("var exportBasketUpdateRefId = null;");
		hpb.writeLine("var exportBasketUpdateRefRemoved = false;");
		hpb.writeLine("function updateExportBasket(refId, remove) {");
		hpb.writeLine("  var exportBasketUpdateFrame = getById('" + EXPORT_BASKET_UPDATE_FRAME_ID + "');");
		hpb.writeLine("  if (exportBasketUpdateFrame == null)");
		hpb.writeLine("    return false;");
		hpb.writeLine("  var exportBasketUpdateForm = exportBasketUpdateFrame.contentWindow.getById('" + EXPORT_BASKET_UPDATE_FORM_ID + "');");
		hpb.writeLine("  if (exportBasketUpdateForm == null)");
		hpb.writeLine("    return false;");
		hpb.writeLine("  if (refId != null) {");
		hpb.writeLine("    var refIdField = exportBasketUpdateFrame.contentWindow.getById('" + STRING_ID_ATTRIBUTE + "');");
		hpb.writeLine("    if (refIdField == null)");
		hpb.writeLine("      return false;");
		hpb.writeLine("    refIdField.value = ((remove ? '--' : '') + refId);");
		hpb.writeLine("  }");
		hpb.writeLine("  exportBasketUpdateOverlay = getOverlay(null, 'exportBasketUpdateOverlay', true);");
		hpb.writeLine("  exportBasketUpdateRefId = refId;");
		hpb.writeLine("  exportBasketUpdateRefRemoved = remove;");
		hpb.writeLine("  exportBasketUpdateForm.submit();");
		hpb.writeLine("  window.setTimeout('waitUpdateExportBasket(0)', 250);");
		hpb.writeLine("  return false;");
		hpb.writeLine("}");
		
		hpb.writeLine("function waitUpdateExportBasket(round) {");
		hpb.writeLine("  if (round > 20) {");
		hpb.writeLine("    alert('The server did not reply in time, please try again later.');");
		hpb.writeLine("    removeElement(exportBasketUpdateOverlay);");
		hpb.writeLine("    exportBasketUpdateOverlay = null;");
		hpb.writeLine("    exportBasketUpdateRefId = null;");
		hpb.writeLine("    exportBasketUpdateRefRemoved = false;");
		hpb.writeLine("    return;");
		hpb.writeLine("  }");
		hpb.writeLine("  var exportBasketUpdateFrame = getById('" + EXPORT_BASKET_UPDATE_FRAME_ID + "');");
		hpb.writeLine("  if (!exportBasketUpdateFrame.contentWindow.getById) {");
		hpb.writeLine("    window.setTimeout(('waitUpdateExportBasket(' + (round+1) + ')'), 250);");
		hpb.writeLine("    return;");
		hpb.writeLine("  }");
		hpb.writeLine("  var exportBasketUpdateForm = exportBasketUpdateFrame.contentWindow.getById('" + EXPORT_BASKET_UPDATE_FORM_ID + "');");
		hpb.writeLine("  if (exportBasketUpdateForm == null) {");
		hpb.writeLine("    window.setTimeout(('waitUpdateExportBasket(' + (round+1) + ')'), 250);");
		hpb.writeLine("    return;");
		hpb.writeLine("  }");
		hpb.writeLine("  var resultField = exportBasketUpdateFrame.contentWindow.getById('" + EXPORT_BASKET_UPDATE_RESULT_ATTRIBUTE + "');");
		hpb.writeLine("  if (resultField == null) {");
		hpb.writeLine("    window.setTimeout(('waitUpdateExportBasket(' + (round+1) + ')'), 250);");
		hpb.writeLine("    return;");
		hpb.writeLine("  }");
		hpb.writeLine("  var ebSize = resultField.value;");
		if (!inBasketPage) {
			if (inFormattedRefPage)
				hpb.writeLine("  var ebButton = getById('ebButton');");
			else hpb.writeLine("  var ebButton = getById('ebButton' + exportBasketUpdateRefId);");
			hpb.writeLine("  var refId = exportBasketUpdateRefId;");
			hpb.writeLine("  if (exportBasketUpdateRefRemoved) {");
			hpb.writeLine("    ebButton.value = 'Export';");
			hpb.writeLine("    ebButton.title = 'Add this reference to your Export Basket';");
			hpb.writeLine("    ebButton.onclick = function() {");
			hpb.writeLine("      updateExportBasket(refId, false);");
			hpb.writeLine("      return false;");
			hpb.writeLine("    };");
			hpb.writeLine("  }");
			hpb.writeLine("  else {");
			hpb.writeLine("    ebButton.value = 'Do Not Export';");
			hpb.writeLine("    ebButton.title = 'Remove this reference from your Export Basket';");
			hpb.writeLine("    ebButton.onclick = function() {");
			hpb.writeLine("      updateExportBasket(refId, true);");
			hpb.writeLine("      return false;");
			hpb.writeLine("    };");
			hpb.writeLine("  }");
		}
		if (inBasketPage || inFormattedRefPage) {
			hpb.writeLine("  if (window.notifyExportBucketUpdated)");
			hpb.writeLine("    notifyExportBucketUpdated(exportBasketUpdateRefId, exportBasketUpdateRefRemoved, ebSize);");
		}
		else {
			hpb.writeLine("  var sEbButton = getById('showExportBasketButton');");
			hpb.writeLine("  if (sEbButton != null) {");
			hpb.writeLine("    if (sEbButton.firstChild)");
			hpb.writeLine("      sEbButton.firstChild.nodeValue = ('My Export Refs (' + ebSize + ')');");
			hpb.writeLine("    else sEbButton.appendChild(document.createTextNode('My Export Refs (' + ebSize + ')'));");
			hpb.writeLine("  }");
		}
		hpb.writeLine("  exportBasketUpdateFrame.contentWindow.exportBasketUpdateResultRead();");
		if (inBasketPage)
			hpb.writeLine("  removeElement(getById('ref' + exportBasketUpdateRefId));");
		hpb.writeLine("  exportBasketUpdateRefId = null;");
		hpb.writeLine("  exportBasketUpdateRefRemoved = false;");
		if (inBasketPage) {
			hpb.writeLine("  if (ebSize == '0') {");
			hpb.writeLine("    var tfr = getById('topFunctionRow');");
			hpb.writeLine("    if (tfr != null)");
			hpb.writeLine("      removeElement(tfr);");
			hpb.writeLine("    var bfr = getById('bottomFunctionRow');");
			hpb.writeLine("    if (bfr != null)");
			hpb.writeLine("      removeElement(bfr);");
			hpb.writeLine("    var rt = getById('resultTable');");
			hpb.writeLine("    if (rt != null) {");
			hpb.writeLine("      var ebtr = newElement('tr', null, 'resultTableRow');");
			hpb.writeLine("      rt.appendChild(ebtr);");
			hpb.writeLine("      var ebtc = newElement('td', null, 'resultTableCell', 'There are currently no references in your Export Basket.');");
			hpb.writeLine("      ebtr.appendChild(ebtc);");
			hpb.writeLine("    }");
			hpb.writeLine("  }");
		}
		hpb.writeLine("  removeElement(exportBasketUpdateOverlay);");
		hpb.writeLine("  exportBasketUpdateOverlay = null;");
		hpb.writeLine("}");
		
		if (!inBasketPage && !inFormattedRefPage) {
			hpb.writeLine("function addShowExportBasketButton() {");
			hpb.writeLine("  if (getById('showExportBasketButton') != null)");
			hpb.writeLine("    return;");
			hpb.writeLine("  var ptm = getPageTopMenu();");
			hpb.writeLine("  if (ptm == null)");
			hpb.writeLine("    return;");
			hpb.writeLine("  var sEbButton = newElement('a', 'showExportBasketButton', 'footerNavigationLink', 'My Export Refs (" + ((exportBasket == null) ? 0 : exportBasket.size()) + ")');");
			hpb.writeLine("  setAttribute(sEbButton, 'href', '#');");
			hpb.writeLine("  setAttribute(sEbButton, 'onclick', 'return openExportBasket();');");
			hpb.writeLine("  setAttribute(sEbButton, 'title', 'Review and export contents of Export Basket');");
			hpb.writeLine("  setAttribute(sEbButton, 'style', 'margin-left: 10px;');");
			hpb.writeLine("  ptm.appendChild(sEbButton);");
			hpb.writeLine("}");
			
			hpb.writeLine("function openExportBasket() {");
			hpb.writeLine("  var w = window.open('" + hpb.request.getContextPath() + hpb.request.getServletPath() + "/" + EXPORT_BASKET_SHOW_PATH + "', 'Your Export Basket', 'width=500,height=400,top=100,left=100,resizable=yes,scrollbar=yes,scrollbars=yes');");
			hpb.writeLine("  w.notifyExportBucketUpdated = window.notifyExportBucketUpdated;");
			hpb.writeLine("  return false;");
			hpb.writeLine("};");
			
			hpb.writeLine("function notifyExportBucketUpdated(refId, removed, ebSize) {");
			hpb.writeLine("  exportBasketRefIDs[refId] = (removed ? null : 'E');");
			hpb.writeLine("  var ebButton = getById('ebButton' + refId);");
			hpb.writeLine("  if (ebButton != null) {");
			hpb.writeLine("    if (removed) {");
			hpb.writeLine("      ebButton.value = 'Export';");
			hpb.writeLine("      ebButton.onclick = function() {");
			hpb.writeLine("        updateExportBasket(refId, false);");
			hpb.writeLine("        return false;");
			hpb.writeLine("      };");
			hpb.writeLine("    }");
			hpb.writeLine("    else {");
			hpb.writeLine("      ebButton.value = 'Do Not Export';");
			hpb.writeLine("      ebButton.onclick = function() {");
			hpb.writeLine("        updateExportBasket(refId, true);");
			hpb.writeLine("        return false;");
			hpb.writeLine("      };");
			hpb.writeLine("    }");
			hpb.writeLine("  }");
			hpb.writeLine("  var sEbButton = getById('showExportBasketButton');");
			hpb.writeLine("  if (sEbButton != null) {");
			hpb.writeLine("    if (sEbButton.firstChild)");
			hpb.writeLine("      sEbButton.firstChild.nodeValue = ('My Export Refs (' + ebSize + ')');");
			hpb.writeLine("    else sEbButton.appendChild(document.createTextNode('My Export Refs (' + ebSize + ')'));");
			hpb.writeLine("  }");
			hpb.writeLine("}");
		}
	}
	
	private void sendFormattedExportBasket(HttpServletRequest request, String format, HttpServletResponse response) throws IOException {
		
		//	get export basket
		LinkedHashSet exportBasket = this.getExportBasket(request, false);
		if ((exportBasket == null) || exportBasket.isEmpty()) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, "No references to export");
			return;
		}
		
		//	resolve reference IDs
		RefBankClient rbk = this.getRefBankClient();
		final PooledStringIterator psi = rbk.getStrings((String[]) exportBasket.toArray(new String[exportBasket.size()]));
		
		//	get format transformer
		final Transformer xslt = ((Transformer) this.formats.get(format));
		
		//	send error message
		if (xslt == null) {
			final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
			bw.write("Unknown reference format: " + format);
			if (this.formats.size() != 0) {
				bw.newLine();
				bw.write("Use the links above or below to get to the valid formats.");
			}
			bw.flush();
			return;
		}
		
		//	format response
		response.setCharacterEncoding(ENCODING);
		response.setContentType(this.formatExportMimeTypes.getProperty(format, "text/plain"));
		response.setHeader("Cache-Control", "no-cache");
		response.setHeader("Content-Disposition", ("attachment; filename=MyRefs." + format + "." + this.formatExportFileExtensions.getProperty(format, "txt")));
		
		//	send formatted references
		final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
		StringBuffer refsParsed = new StringBuffer("<" + STRING_SET_NODE_TYPE + SP_XML_NAMESPACE_ATTRIBUTE + ">\n");
		while (psi.hasNextString()) {
			PooledString ps = psi.getNextString();
			refsParsed.append("<" + STRING_NODE_TYPE + SP_XML_NAMESPACE_ATTRIBUTE + "><" + STRING_PARSED_NODE_TYPE + ">\n");
			String refParsedString = ps.getStringParsed();
			int fc = 0;
			if (refParsedString.startsWith("<mods:mods>")) {
				refsParsed.append("<mods:mods xmlns:mods=\"http://www.loc.gov/mods/v3\">");
				fc = "<mods:mods>".length();
			}
			for (int c = fc; c < refParsedString.length(); c++) {
				char ch = refParsedString.charAt(c);
				if ((ch == '<') && (c != 0) && (refParsedString.charAt(c - 1) == '>'))
					refsParsed.append('\n');
				refsParsed.append(ch);
			}
			refsParsed.append("\n</" + STRING_PARSED_NODE_TYPE + "></" + STRING_NODE_TYPE + ">");
		}
		refsParsed.append("\n</" + STRING_SET_NODE_TYPE + ">");
		final CharSequenceReader csr = new CharSequenceReader(refsParsed);
		final IOException[] ioe = {null};
		Thread tt = new Thread() {
			public void run() {
				synchronized (csr) {
					csr.notify();
				}
				try {
					xslt.transform(new StreamSource(csr), new StreamResult(new IsolatorWriter(bw)));
				}
				catch (TransformerException te) {
					ioe[0] = new IOException(te.getMessage());
				}
			}
		};
		synchronized (csr) {
			tt.start();
			try {
				csr.wait();
			} catch (InterruptedException ie) {}
		}
		while (tt.isAlive()) {
			try {
				tt.join(250);
			} catch (InterruptedException ie) {}
			if (ioe[0] != null)
				throw ioe[0];
			if ((csr.lastRead + 2500) < System.currentTimeMillis())
				break;
		}
		bw.flush();
	}
	
	private void sendStyledExportBasket(HttpServletRequest request, String style, HttpServletResponse response) throws IOException {
		LinkedHashSet exportBasket = this.getExportBasket(request, false);
		if ((exportBasket == null) || exportBasket.isEmpty()) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, "No references to export");
			return;
		}
		
		//	resolve reference IDs
		RefBankClient rbk = this.getRefBankClient();
		PooledStringIterator psi = rbk.getStrings((String[]) exportBasket.toArray(new String[exportBasket.size()]));
		
		//	anything to send?
		if (!psi.hasNextString()) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, "No references to export");
			return;
		}
		
		//	test reference style
		PooledString ps = psi.getNextString(); 
		QueriableAnnotation modsRef = SgmlDocumentReader.readDocument(new StringReader(ps.getStringParsed()));
		RefData rd = BibRefUtils.modsXmlToRefData(modsRef);
		String sr = BibRefUtils.toRefString(rd, style);
		if (sr == null) {
			response.setContentType("text/plain");
			response.setCharacterEncoding(ENCODING);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
			bw.write("Unknown reference style: " + style);
			if (BibRefUtils.getRefStringStyles().length != 0) {
				bw.newLine();
				bw.write("Use the links below to get to the valid styles.");
			}
			bw.flush();
			return;
		}
		
		//	format response
		response.setContentType("text/html");
		response.setCharacterEncoding(ENCODING);
		response.setHeader("Cache-Control", "no-cache");
		response.setHeader("Content-Disposition", ("attachment; filename=MyRefs." + style + ".html"));
		
		//	send list head
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
		bw.write("<html><head></head><body>"); bw.newLine();
		
		//	send references
		while (true) {
			
			//	cut whitespace and HTML tags
			sr = sr.trim();
			if ("<html>".equals(sr.substring(0, 6).toLowerCase()))
				sr = sr.substring(6);
			if ("</html>".equals(sr.substring(sr.length()-7).toLowerCase()))
				sr = sr.substring(0, (sr.length()-7));
			sr = sr.trim();
			
			//	send reference
			bw.write("<p style=\"" + this.styledRefLayout + "\">" + sr.trim() + "</p>"); bw.newLine();
			
			//	switch to next reference
			if (psi.hasNextString()) {
				ps = psi.getNextString(); 
				modsRef = SgmlDocumentReader.readDocument(new StringReader(ps.getStringParsed()));
				rd = BibRefUtils.modsXmlToRefData(modsRef);
				sr = BibRefUtils.toRefString(rd, style);
			}
			else break;
		}
		
		//	close reference list
		bw.write("</body></html>"); bw.newLine();
		bw.flush();
	}
	
	private void sendFormattedReference(HttpServletRequest request, String id, String format, HttpServletResponse response) throws IOException {
		
		//	retrieve RefBank client on the fly to use local bridge if possible
		RefBankClient rbc = this.getRefBankClient();
		
		// get parsed string
		final PooledString ps = rbc.getString(id);
		
		//	check error
		if (this.sendFormattedReferenceError(request, id, ps, response))
			return;
		
		//	get format transformer
		final Transformer xslt = ((Transformer) this.formats.get(format));
		if (xslt == null) {
			response.setContentType("text/plain");
			response.setCharacterEncoding(ENCODING);
			final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
			bw.write("Unknown reference format: " + format);
			if (this.formats.size() != 0) {
				bw.newLine();
				bw.write("Use the links below to get to the valid formats.");
			}
			bw.flush();
			return;
		}
		
		//	prepare parsed reference for XSLT
		String refParsedString = ps.getStringParsed();
		StringBuffer refParsed = new StringBuffer("<" + STRING_NODE_TYPE + SP_XML_NAMESPACE_ATTRIBUTE + "><" + STRING_PARSED_NODE_TYPE + ">\n");
		int fc = 0;
		if (refParsedString.startsWith("<mods:mods>")) {
			refParsed.append("<mods:mods xmlns:mods=\"http://www.loc.gov/mods/v3\">");
			fc = "<mods:mods>".length();
		}
		for (int c = fc; c < refParsedString.length(); c++) {
			char ch = refParsedString.charAt(c);
			if ((ch == '<') && (c != 0) && (refParsedString.charAt(c - 1) == '>'))
				refParsed.append('\n');
			refParsed.append(ch);
		}
		refParsed.append("\n</" + STRING_PARSED_NODE_TYPE + "></" + STRING_NODE_TYPE + ">");
		
		//	send formatted reference
		response.setContentType("text/plain");
		response.setCharacterEncoding(ENCODING);
		final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
		final CharSequenceReader csr = new CharSequenceReader(refParsed);
		final IOException[] ioe = {null};
		Thread tt = new Thread() {
			public void run() {
				synchronized (csr) {
					csr.notify();
				}
				try {
					xslt.transform(new StreamSource(csr), new StreamResult(new IsolatorWriter(bw)));
				}
				catch (TransformerException te) {
					ioe[0] = new IOException(te.getMessage());
				}
			}
		};
		synchronized (csr) {
			tt.start();
			try {
				csr.wait();
			} catch (InterruptedException ie) {}
		}
		while (tt.isAlive()) {
			try {
				tt.join(250);
			} catch (InterruptedException ie) {}
			if (ioe[0] != null)
				throw ioe[0];
			if ((csr.lastRead + 2500) < System.currentTimeMillis())
				break;
		}
		bw.flush();
	}
	
	private void sendStyledReference(HttpServletRequest request, String id, String style, HttpServletResponse response) throws IOException {
		
		//	retrieve RefBank client on the fly to use local bridge if possible
		RefBankClient rbc = this.getRefBankClient();
		
		// get parsed string
		final PooledString ps = rbc.getString(id);
		
		//	check error
		if (this.sendFormattedReferenceError(request, id, ps, response))
			return;
		
		//	get styled reference
		QueriableAnnotation modsRef = SgmlDocumentReader.readDocument(new StringReader(ps.getStringParsed()));
		RefData rd = BibRefUtils.modsXmlToRefData(modsRef);
		String sr = BibRefUtils.toRefString(rd, style);
		
		//	check error
		if (sr == null) {
			response.setContentType("text/plain");
			response.setCharacterEncoding(ENCODING);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
			bw.write("Unknown reference style: " + style);
			if (BibRefUtils.getRefStringStyles().length != 0) {
				bw.newLine();
				bw.write("Use the links below to get to the valid styles.");
			}
			bw.flush();
			return;
		}
		
		//	cut HTML tags
		if ("<html>".equals(sr.substring(0, 6).toLowerCase()))
			sr = sr.substring(6);
		if ("</html>".equals(sr.substring(sr.length()-7).toLowerCase()))
			sr = sr.substring(0, (sr.length()-7));
		
		//	prepare sending reference
		response.setContentType("text/html");
		response.setCharacterEncoding(ENCODING);
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
		bw.write("<html><head></head><body>");
		bw.newLine();
		bw.write("<p style=\"" + this.styledRefLayout + "\">" + sr.trim() + "</p>");
		bw.newLine();
		bw.write("</body></html>");
		bw.newLine();
		bw.flush();
	}
	
	private boolean sendFormattedReferenceError(HttpServletRequest request, String id, PooledString ps, HttpServletResponse response) throws IOException {
		if (ps == null) {
			response.setContentType("text/plain");
			response.setCharacterEncoding(ENCODING);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
			bw.write("Invalid reference ID: " + id);
			bw.flush();
			return true;
		}
		else if (ps.getStringParsed() == null) {
			response.setContentType("text/plain");
			response.setCharacterEncoding(ENCODING);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
			bw.write("A parsed version is not yet available for this reference.");
			if (this.refParserUrl != null) {
				bw.newLine();
				bw.write("Use the 'Parse Reference' link below to create a parsed version.");
				bw.newLine();
				bw.newLine();
				bw.write("If you just parsed the reference string, there might be a problem in the parse.");
				bw.newLine();
				bw.write("In that case, you can use the 'Parse Reference' link below to re-open the parser.");
			}
			bw.flush();
			return true;
		}
		else return false;
	}
	
	private void sendFormattedReferenceFrame(HttpServletRequest request, final String id, final String format, final String style, HttpServletResponse response) throws IOException {
		
		//	retrieve RefBank client on the fly to use local bridge if possible
		RefBankClient rbc = this.getRefBankClient();
		
		//	get parsed string
		final PooledString ps = rbc.getString(id);
		
		//	check null
		if (ps == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, ("Invalid reference ID: " + id));
			return;
		}
		
		//	send code frame
		response.setContentType("text/html");
		response.setCharacterEncoding(ENCODING);
		HtmlPageBuilder pageBuilder = new HtmlPageBuilder(this, request, response) {
			protected void include(String type, String tag) throws IOException {
				if ("includeBody".equals(type))
					this.includeParsedReference();
				else super.include(type, tag);
			}
			protected String[] getOnloadCalls() {
				String[] olcs = new String[1];
				if ((refParserUrl != null) && PARSE_REF_FORMAT.equals(format))
					olcs[0] = "parseRef();";
				else if ((refEditorUrl != null) && EDIT_REF_FORMAT.equals(format))
					olcs[0] = "editRef();";
				else if (style != null)
					olcs[0] = "setStyle('" + style + "');";
				else olcs[0] = "setFormat('" + ((format == null) ? "MODS" : format) + "');";
				return olcs;
			}
			private void includeParsedReference() throws IOException {
				this.writeLine("<table class=\"resultTable\">");
				this.writeLine("<tr class=\"resultTableRow\">");
				this.writeLine("<td class=\"resultTableCell\">");
				this.write("<p class=\"referenceString\">" + xmlGrammar.escape(ps.getStringPlain()) + "</p>");
				this.writeLine("</td>");
				this.writeLine("</tr>");
				this.writeLine("<tr class=\"resultTableRow\">");
				this.writeLine("<td class=\"resultTableCell\">");
				this.writeLine("<iframe src=\"about:blank\" id=\"refCodeFrame\">");
				this.writeLine("</iframe>");
				this.writeLine("</td>");
				this.writeLine("</tr>");
				this.writeLine("<tr class=\"resultTableRow\">");
				this.writeLine("<td class=\"resultTableCell\">");
				this.writeLine("<span class=\"referenceFormatLinkLabel\">");
				this.writeLine("Contributed by <b>" + ps.getCreateUser() + "</b> (at <b>" + ps.getCreateDomain() + "</b>)");
				this.writeLine("</span>");
				if (ps.getParseChecksum() != null) {
					this.writeLine("&nbsp;&nbsp;");
					this.writeLine("<span class=\"referenceFormatLinkLabel\">");
					this.writeLine("Parsed by <b>" + ps.getUpdateUser() + "</b> (at <b>" + ps.getUpdateDomain() + "</b>)");
					this.writeLine("</span>");
				}
				this.writeLine("</td>");
				this.writeLine("</tr>");
				String[] styles = BibRefUtils.getRefStringStyles();
				if (ps.getStringParsed() != null) {
					if (formats.size() != 0) {
						this.writeLine("<tr class=\"resultTableRow\">");
						this.writeLine("<td class=\"resultTableCell\">");
						this.writeLine("<span class=\"referenceFormatLinkLabel\">Other Formats:</span>");
						for (Iterator fit = formats.keySet().iterator(); fit.hasNext();) {
							String format = ((String) fit.next());
							this.writeLine("<input" + 
									" class=\"referenceFormatLink\"" + 
									" type=\"button\"" + 
									" value=\"" + format + "\"" + 
									" title=\"Get this reference formatted as " + format + "\"" + 
									" onclick=\"return setFormat('" + format + "');\"" + 
									">");
						}
						this.writeLine("</td>");
						this.writeLine("</tr>");
					}
					if (styles.length != 0) {
						this.writeLine("<tr class=\"resultTableRow\">");
						this.writeLine("<td class=\"resultTableCell\">");
						this.writeLine("<span class=\"referenceFormatLinkLabel\">Reference Styles:</span>");
						for (int s = 0; s < styles.length; s++) {
							this.writeLine("<input" + 
									" class=\"referenceFormatLink\"" + 
									" type=\"button\"" + 
									" value=\"" + styles[s] + "\"" + 
									" title=\"Get this reference formatted in " + styles[s] + " style\"" + 
									" onclick=\"return setStyle('" + styles[s] + "');\"" + 
									">");
						}
						this.writeLine("</td>");
						this.writeLine("</tr>");
					}
					this.writeLine("<tr class=\"resultTableRow\">");
					this.writeLine("<td class=\"resultTableCell\">");
					this.writeLine("<span class=\"referenceFormatLinkLabel\">Export Reference:</span>");
					LinkedHashSet exportBasket = getExportBasket(this.request, false);
					this.writeLine("<input" + 
							" class=\"referenceFormatLink\"" + 
							" type=\"button\"" + 
							" id=\"ebButton\"" + 
							" value=\"" + (((exportBasket != null) && exportBasket.contains(id)) ? "Do Not Export" : "Export") + "\"" + 
							" title=\"" + (((exportBasket != null) && exportBasket.contains(id)) ? "Remove this reference from your Export Basket" : "Add this reference to your Export Basket") + "\"" + 
							" onclick=\"return updateExportBasket('" + id + "', " + (((exportBasket != null) && exportBasket.contains(id)) ? "true" : "false") + ");\"" +
							">");
					this.writeLine("</td>");
					this.writeLine("</tr>");
				}
				this.writeLine("<tr class=\"resultTableRow\">");
				this.writeLine("<td class=\"resultTableCell\">");
				this.writeLine("<span class=\"referenceFormatLinkLabel\">Contribute to Bibliography:</span>");
				if (refParserUrl != null) {
					this.writeLine("<input" + 
							" class=\"referenceFormatLink\"" +
							" type=\"button\"" +
							" value=\"" + ((ps.getStringParsed() == null) ? "Parse Reference" : "Refine Parsed Reference") + "\"" +
							" title=\"" + ((ps.getStringParsed() == null) ? "Parse this bibliographic reference so formatted versions become available" : "Refine or correct the parsed version of this bibliographic reference") + "\"" +
							" onclick=\"return parseRef();\"" + 
							">");
				}
				if (refEditorUrl != null) {
					this.writeLine("<input" + 
							" class=\"referenceFormatLink\"" +
							" type=\"button\"" +
							" value=\"Edit Reference\"" +
							" title=\"" + "Correct this bibliographic reference string, e.g. to eliminate typos or punctuation errors" + "\"" +
							" onclick=\"return editRef();\"" + 
							">");
				}
				this.writeLine("<input type=\"button\" id=\"delete" + ps.id + "\" class=\"referenceFormatLink\"" + (ps.isDeleted() ? " style=\"display: none;\"" : "") + " onclick=\"return setDeleted('" + ps.id + "', true);\" value=\"Delete\">");
				this.writeLine("<input type=\"button\" id=\"unDelete" + ps.id + "\" class=\"referenceFormatLink\"" + (ps.isDeleted() ? "" : " style=\"display: none;\"") + " onclick=\"return setDeleted('" + ps.id + "', false);\" value=\"Un-Delete\">");
				this.writeLine("</td>");
				this.writeLine("</tr>");
				
				this.writeLine("</table>");
				
				this.write("<iframe id=\"" + MINOR_UPDATE_FRAME_ID + "\" height=\"0px\" style=\"border-width: 0px;\" src=\"" + this.request.getContextPath() + this.request.getServletPath() + "/" + MINOR_UPDATE_PATH + "\">");
				this.writeLine("</iframe>");
				
				this.write("<iframe id=\"" + EXPORT_BASKET_UPDATE_FRAME_ID + "\" height=\"0px\" style=\"border-width: 0px;\" src=\"" + this.request.getContextPath() + this.request.getServletPath() + "/" + EXPORT_BASKET_UPDATE_PATH + "\">");
				this.writeLine("</iframe>");
			}
			
			protected String getPageTitle(String title) {
				if (PARSE_REF_FORMAT.equals(format))
					return "Parse Reference";
				else if (EDIT_REF_FORMAT.equals(format))
					return "Edit Reference";
				else return ("Parsed Reference as " + format);
			}
			
			protected void writePageHeadExtensions() throws IOException {
				this.writeLine("<script type=\"text/javascript\">");
				
				this.writeLine("function setDeleted(refId, deleted) {");
				this.writeLine("  if (!getUser())");
				this.writeLine("    return false;");
				this.writeLine("  var minorUpdateFrame = document.getElementById('" + MINOR_UPDATE_FRAME_ID + "');");
				this.writeLine("  if (minorUpdateFrame == null)");
				this.writeLine("    return false;");
				this.writeLine("  var minorUpdateForm = minorUpdateFrame.contentWindow.document.getElementById('" + MINOR_UPDATE_FORM_ID + "');");
				this.writeLine("  if (minorUpdateForm == null)");
				this.writeLine("    return false;");
				this.writeLine("  var refIdField = minorUpdateFrame.contentWindow.document.getElementById('" + STRING_ID_ATTRIBUTE + "');");
				this.writeLine("  if (refIdField == null)");
				this.writeLine("    return false;");
				this.writeLine("  refIdField.value = refId;");
				this.writeLine("  var deletedField = minorUpdateFrame.contentWindow.document.getElementById('" + DELETED_PARAMETER + "');");
				this.writeLine("  if (deletedField == null)");
				this.writeLine("    return false;");
				this.writeLine("  deletedField.value = deleted;");
				this.writeLine("  var userField = minorUpdateFrame.contentWindow.document.getElementById('" + USER_PARAMETER + "');");
				this.writeLine("  if (userField == null)");
				this.writeLine("    return false;");
				this.writeLine("  userField.value = user;");
				this.writeLine("  minorUpdateForm.submit();");
				this.writeLine("  document.getElementById('delete' + refId).style.display = (deleted ? 'none' : '');");
				this.writeLine("  document.getElementById('unDelete' + refId).style.display = (deleted ? '' : 'none');");
				this.writeLine("  return false;");
				this.writeLine("}");
				
				this.writeLine("var currentFormat = '" + (PARSE_REF_FORMAT.equals(format) ? "MODS" : format) + "';");
				this.writeLine("function setFormat(format) {");
				this.writeLine("  document.getElementById('refCodeFrame').src = ('" + this.request.getContextPath() + this.request.getServletPath() + "?" + STRING_ID_ATTRIBUTE + "=" + id + "&" + FORMAT_PARAMETER + "=' + format);");
				this.writeLine("  document.title = ('Parsed Reference as ' + format);");
				this.writeLine("  currentFormat = format;");
				this.writeLine("  currentStyle = null;");
				this.writeLine("  return false;");
				this.writeLine("}");
				
				this.writeLine("var currentStyle = " + ((style == null) ? "null" : ("'" + style + "'")) + ";");
				this.writeLine("function setStyle(style) {");
				this.writeLine("  document.getElementById('refCodeFrame').src = ('" + this.request.getContextPath() + this.request.getServletPath() + "?" + STRING_ID_ATTRIBUTE + "=" + id + "&" + STYLE_PARAMETER + "=' + style);");
				this.writeLine("  document.title = ('Reference in ' + style + ' Style');");
				this.writeLine("  currentFormat = null;");
				this.writeLine("  currentStyle = style;");
				this.writeLine("  return false;");
				this.writeLine("}");
				
				if (refParserUrl != null) {
					String parsedRefBaseLinkFormatted = (this.request.getContextPath() + this.request.getServletPath() + 
							"?" + STRING_ID_ATTRIBUTE + "=" + id + 
							"&" + FORMAT_PARAMETER + "="
						);
					String parserLinkFormatted = (refParserUrl + 
							"?" + STRING_ID_ATTRIBUTE + "=" + URLEncoder.encode(ps.id, ENCODING) + "" +
							"&resultUrl=" + URLEncoder.encode((parsedRefBaseLinkFormatted), ENCODING)
						);
					String parsedRefBaseLinkStyled = (this.request.getContextPath() + this.request.getServletPath() + 
							"?" + STRING_ID_ATTRIBUTE + "=" + id + 
							"&" + STYLE_PARAMETER + "="
						);
					String parserLinkStyled = (refParserUrl + 
							"?" + STRING_ID_ATTRIBUTE + "=" + URLEncoder.encode(ps.id, ENCODING) + "" +
							"&resultUrl=" + URLEncoder.encode((parsedRefBaseLinkStyled), ENCODING)
						);
					this.writeLine("function parseRef() {");
					this.writeLine("  if (!getUser())");
					this.writeLine("    return false;");
					this.writeLine("  if (currentStyle == null)");
					this.writeLine("    document.getElementById('refCodeFrame').src = ('" + parserLinkFormatted + "' + currentFormat + '&" + USER_PARAMETER + "=' + encodeURIComponent(user));");
					this.writeLine("  else document.getElementById('refCodeFrame').src = ('" + parserLinkStyled + "' + currentStyle + '&" + USER_PARAMETER + "=' + encodeURIComponent(user));");
					this.writeLine("  return false;");
					this.writeLine("}");
				}
				if (refEditorUrl != null) {
					String editedRefBaseLink = (this.request.getContextPath() + this.request.getServletPath() + "/" + FRAME_PAGE_PATH +
							"?" + FORMAT_PARAMETER + "=MODS" + 
							"&" + STRING_ID_ATTRIBUTE + "="
						);
					String editorLink = (refEditorUrl + 
							"?" + STRING_ID_ATTRIBUTE + "=" + URLEncoder.encode(ps.id, ENCODING) + "" +
							"&resultUrlPrefix=" + URLEncoder.encode((editedRefBaseLink), ENCODING)
						);
					this.writeLine("function editRef() {");
					this.writeLine("  if (!getUser())");
					this.writeLine("    return false;");
					this.writeLine("  window.location.href = ('" + editorLink + "&" + USER_PARAMETER + "=' + encodeURIComponent(user));");
					this.writeLine("  return false;");
					this.writeLine("}");
				}
				
				writeExportBasketFunctions(this, false, true);
				
				this.writeLine("</script>");
			}
		};
		this.sendPopupHtmlPage(pageBuilder);
	}
	
	private static class CharSequenceReader extends Reader {
		private CharSequence chars;
		private int length;
		private int offset = 0;
		private int mark = 0;
		long lastRead = System.currentTimeMillis();
		CharSequenceReader(CharSequence chars) {
			this.chars = chars;
			this.length = this.chars.length();
		}
		private void ensureOpen() throws IOException {
			if (this.chars == null) throw new IOException("Stream closed");
		}
		public int read() throws IOException {
			synchronized (this.lock) {
				ensureOpen();
				this.lastRead = System.currentTimeMillis();
				if (this.offset >= this.length)
					return -1;
				else return this.chars.charAt(this.offset++);
			}
		}
		public int read(char cbuf[], int off, int len) throws IOException {
			synchronized (this.lock) {
				ensureOpen();
				if ((off < 0) || (off > cbuf.length) || (len < 0) || ((off + len) > cbuf.length) || ((off + len) < 0))
					throw new IndexOutOfBoundsException();
				else if (len == 0)
					return 0;
				else if (this.offset >= this.length)
					return -1;
				int readable = Math.min(this.length - this.offset, len);
				for (int r = 0; r < readable; r++)
					cbuf[off + r] = this.chars.charAt(this.offset++);
				this.lastRead = System.currentTimeMillis();
				return readable;
			}
		}
		public long skip(long ns) throws IOException {
			synchronized (this.lock) {
				ensureOpen();
				if (this.offset >= this.length)
					return 0;
				long skippable = Math.min(this.length - this.offset, ns);
				skippable = Math.max(-this.offset, skippable);
				this.offset += skippable;
				this.lastRead = System.currentTimeMillis();
				return skippable;
			}
		}
		public boolean ready() throws IOException {
			synchronized (this.lock) {
				ensureOpen();
				return true;
			}
		}
		public boolean markSupported() {
			return true;
		}
		public void mark(int readAheadLimit) throws IOException {
			if (readAheadLimit < 0)
				throw new IllegalArgumentException("Read-ahead limit < 0");
			synchronized (this.lock) {
				ensureOpen();
				this.mark = this.offset;
			}
		}
		public void reset() throws IOException {
			synchronized (this.lock) {
				ensureOpen();
				this.offset = this.mark;
			}
		}
		public void close() {
			this.chars = null;
		}
	}
	
	private HtmlPageBuilder getSearchPageBuilder(HttpServletRequest request, /*final PooledStringIterator psi,*/ final String query, final String refType, final String author, final String title, final String year, final String origin, HttpServletResponse response) throws IOException {
		response.setContentType("text/html");
		response.setCharacterEncoding(ENCODING);
		return new HtmlPageBuilder(this, request, response) {
			protected void include(String type, String tag) throws IOException {
				if ("includeForm".equals(type))
					this.includeSearchForm();
				else if ("includeResult".equals(type)) {
//					if (psi != null)
						this.includeSearchResult();
				}
				else if ("includeRefTypeOptions".equals(type))
					this.includeRefTypeOptions();
				else super.include(type, tag);
			}
			private boolean inSearchForm = false;
			private Properties searchFieldIDs = new Properties();
			public void storeToken(String token, int treeDepth) throws IOException {
				if (this.inSearchForm && html.isTag(token)) {
					String type = html.getType(token);
					if ("input".equals(type)) {
						TreeNodeAttributeSet tnas = TreeNodeAttributeSet.getTagAttributes(token, html);
						String name = tnas.getAttribute("name");
						if (name != null) {
							if (!tnas.containsAttribute("value")) {
								String value = null;
								if (QUERY_PARAMETER.equals(name))
									value = query;
								else if (AUTHOR_PARAMETER.equals(name))
									value = author;
								else if (TITLE_PARAMETER.equals(name))
									value = title;
								else if (DATE_PARAMETER.equals(name))
									value = year;
								else if (YEAR_PARAMETER.equals(name))
									value = year;
								else if (ORIGIN_PARAMETER.equals(name))
									value = origin;
								if (value != null)
									token = (token.substring(0, (token.length() - 1)) + ((token.indexOf("id=\"") == -1) ? (" id=\"" + name + "SearchField\"") : "") + " value=\"" + xmlGrammar.escape(value) + "\">");
							}
							if (tnas.containsAttribute("id"))
								this.searchFieldIDs.setProperty(name, tnas.getAttribute("id"));
							else {
								token = (token.substring(0, (token.length() - 1)) + " id=\"" + name + "SearchField\">");
								this.searchFieldIDs.setProperty(name, (name + "SearchField"));
							}
						}
					}
					else if ((refType != null) && "option".equals(type) && !html.isEndTag(token)) {
						TreeNodeAttributeSet tnas = TreeNodeAttributeSet.getTagAttributes(token, html);
						String value = tnas.getAttribute("value");
						if (refType.equals(value))
							token = (token.substring(0, (token.length() - 1)) + " selected>");
					}
					else if ("select".equals(type) && !html.isEndTag(token)) {
						TreeNodeAttributeSet tnas = TreeNodeAttributeSet.getTagAttributes(token, html);
						String name = tnas.getAttribute("name");
						if (name != null) {
							if (tnas.containsAttribute("id"))
								this.searchFieldIDs.setProperty(name, tnas.getAttribute("id"));
							else {
								token = (token.substring(0, (token.length() - 1)) + " id=\"" + name + "SearchField\">");
								this.searchFieldIDs.setProperty(name, (name + "SearchField"));
							}
						}
					}
				}
				super.storeToken(token, treeDepth);
			}
			
			private void includeSearchForm() throws IOException {
				this.writeLine("<form" +
						" method=\"GET\"" +
						" action=\"" + this.request.getContextPath() + this.request.getServletPath() + "\"" +
						" onsubmit=\"return doDynamicSearch();\"" +
						">");
				this.inSearchForm = true;
				this.includeFile("searchFields.html");
				this.inSearchForm = false;
				this.writeLine("</form>");
				
				this.writeLine("<script type=\"text/javascript\">");
				
				this.writeLine("function doDynamicSearch() {");
				this.writeLine("  var ds = getById('dynamicSearchScript');");
				this.writeLine("  var dsp = ds.parentNode;");
				this.writeLine("  removeElement(ds);");
//				this.writeLine("  var dsSrc = (window.location.protocol + '//' + window.location.host + window.location.pathname.substring(0, (window.location.pathname.lastIndexOf('/')+1)) + '" + SEARCH_RESULT_PATH + "?');");
				this.writeLine("  var dsSrc = ('" + this.request.getContextPath() + this.request.getServletPath() + "/" + SEARCH_RESULT_PATH + "?');");
				this.writeLine("  dsSrc = (dsSrc + 'time=' + (new Date).getTime());");
				this.writeLine("  dsSrc = (dsSrc + getSearchParameter('" + QUERY_PARAMETER + "', '" + this.searchFieldIDs.get(QUERY_PARAMETER) + "'));");
				this.writeLine("  dsSrc = (dsSrc + getSearchParameter('" + TYPE_PARAMETER + "', '" + this.searchFieldIDs.get(TYPE_PARAMETER) + "'));");
				this.writeLine("  dsSrc = (dsSrc + getSearchParameter('" + AUTHOR_PARAMETER + "', '" + this.searchFieldIDs.get(AUTHOR_PARAMETER) + "'));");
				this.writeLine("  dsSrc = (dsSrc + getSearchParameter('" + TITLE_PARAMETER + "', '" + this.searchFieldIDs.get(TITLE_PARAMETER) + "'));");
				this.writeLine("  dsSrc = (dsSrc + getSearchParameter('" + DATE_PARAMETER + "', '" + this.searchFieldIDs.get(DATE_PARAMETER) + "'));");
				this.writeLine("  dsSrc = (dsSrc + getSearchParameter('" + YEAR_PARAMETER + "', '" + this.searchFieldIDs.get(YEAR_PARAMETER) + "'));");
				this.writeLine("  dsSrc = (dsSrc + getSearchParameter('" + ORIGIN_PARAMETER + "', '" + this.searchFieldIDs.get(ORIGIN_PARAMETER) + "'));");
//				this.writeLine("  alert('Switching to ' + dsSrc);");
				
				this.writeLine("  activeReferenceId = null;");
				this.writeLine("  showingOptionsFor = null;");
				this.writeLine("  grabbedId = null;");
				this.writeLine("  draggingId = null;");
				this.writeLine("  dragging = null;");
				this.writeLine("  dragPosX = 0;");
				this.writeLine("  dragPosY = 0;");
				this.writeLine("  deletedRefIDs = new Array();");
				this.writeLine("  deletedRefIdSet = new Object();");
				this.writeLine("  toggleDeleted(false);");
				this.writeLine("  if (resultTableHeader == null) {");
				this.writeLine("    resultTableHeader = getById('resultTableHeader');");
				this.writeLine("    removeElement(resultTableHeader);");
				this.writeLine("  }");
				this.writeLine("  var rt = getById('resultTable');");
				this.writeLine("  while (rt.firstChild != null)");
				this.writeLine("    removeElement(rt.firstChild);");
				
				this.writeLine("  ds = newElement('script', 'dynamicSearchScript');");
				this.writeLine("  ds.type = 'text/javascript';");
				this.writeLine("  ds.src = dsSrc;");
//				this.writeLine("  ds.src = ('./dynamicSearchResult.js?' + 'time=' + (new Date).getTime());");
				this.writeLine("  dsp.appendChild(ds);");
				this.writeLine("  return false;");
				this.writeLine("}");
				
				this.writeLine("function getSearchParameter(fieldName, fieldId) {");
				this.writeLine("  var field = getById(fieldId);");
				this.writeLine("  if ((field == null) || (field.value == ''))");
				this.writeLine("    return '';");
				this.writeLine("  else return ('&' + fieldName + '=' + escape(field.value));");
				this.writeLine("}");
				
				this.writeLine("</script>");
			}
			
			private void includeRefTypeOptions() throws IOException {
				BibRefType[] refTypes = refTypeSystem.getBibRefTypes();
				for (int t = 0; t < refTypes.length; t++)
					this.storeToken(("<option value=\"" + xmlGrammar.escape(refTypes[t].name) + "\">" + refTypes[t].getLabel() + "</option>"), 0);
			}
			
			private void includeSearchResult() throws IOException {
				
				this.writeLine("<table class=\"resultTable\" id=\"resultTable\" style=\"display: none;\">");
//				
//				StringVector deletedRefIDs = new StringVector();
//				HashMap refClusters = new HashMap();
//				int refCount = 0;
//				if (!psi.hasNextString()) {
//					this.writeLine("<tr class=\"resultTableRow\">");
//					this.writeLine("<td class=\"resultTableCell\">");
//					this.writeLine("Your search did not return any results, sorry.");
//					this.writeLine("</td>");
//					this.writeLine("</tr>");
//				}
//				else {
					this.writeLine("<tr class=\"resultTableRow\" id=\"resultTableHeader\">");
					this.writeLine("<td class=\"resultTableCell\" width=\"50%\">");
					this.writeLine("<span class=\"referenceString\" style=\"font-size: 60%;\">Hover&nbsp;references&nbsp;for&nbsp;further&nbsp;options</span>" +
							"&nbsp;&nbsp;" +
							"<span class=\"referenceString\" style=\"font-size: 60%;\"><span id=\"resultRefCount\"></span>&nbsp;matching&nbsp;references&nbsp;(including&nbsp;<span id=\"resultRefCountDeleted\"></span>&nbsp;deleted&nbsp;and&nbsp;<span id=\"resultRefCountDuplicate\"></span>&nbsp;duplicates)&nbsp;out&nbsp;of&nbsp;" + getRefBankClient().getStringCount(0) + "</span>" +
							"&nbsp;&nbsp;" +
							"<span class=\"referenceString\" style=\"font-size: 60%;\"><a href=\"#\" onclick=\"window.open('" + this.request.getContextPath() + "/staticPopup/help.html', 'RefBank Help', 'width=500,height=400,top=100,left=100,resizable=yes,scrollbar=yes,scrollbars=yes'); return false;\">Help</a></span>" +
							"");
					this.writeLine("</td>");
					this.writeLine("<td class=\"resultTableCell\" style=\"text-align: right;\">");
					this.writeLine("<input type=\"button\" id=\"showDeleted\" class=\"referenceFormatLink\" onclick=\"return toggleDeleted(true);\" value=\"Show references flagged as deleted\">");
					this.writeLine("<input type=\"button\" id=\"hideDeleted\" class=\"referenceFormatLink\" style=\"display: none;\" onclick=\"return toggleDeleted(false);\" value=\"Hide references flagged as deleted\">");
					this.writeLine("</td>");
					this.writeLine("</tr>");
//					
//					//	retrieve and group result references
//					while (psi.hasNextString()) {
//						PooledString ps = psi.getNextString();
//						SearchResultRefCluster refCluster = ((SearchResultRefCluster) refClusters.get(ps.getCanonicalStringID()));
//						if (refCluster == null) {
//							refCluster = new SearchResultRefCluster(ps.getCanonicalStringID());
//							refClusters.put(ps.getCanonicalStringID(), refCluster);
//						}
//						refCluster.add(ps);
//					}
//					
//					//	generate search result display
//					for (Iterator canIdIt = refClusters.keySet().iterator(); canIdIt.hasNext();) {
//						SearchResultRefCluster refCluster = ((SearchResultRefCluster) refClusters.get(canIdIt.next()));
//						
//						if (refCluster.getRepresentative() == null) {
//							RefBankClient rbc = getRefBankClient();
//							PooledString rps = rbc.getString(refCluster.id);
//							if (rps != null)
//								refCluster.add(rps);
//						}
//						
//						this.writeLine("<tr class=\"resultTableRow\" id=\"row" + refCluster.id + "\"" + (refCluster.isClusterDeleted() ? " style=\"display: none;\"" : "") + "><td class=\"resultTableCell\" colspan=\"2\">");
//						this.writeSearchResultRef(refCluster.getRepresentative(), false);
//						refCount++;
//						if (refCluster.getRepresentative().isDeleted())
//							deletedRefIDs.addElement(refCluster.id);
//						this.writeLine("<div id=\"duplicatesOf" + refCluster.id + "\" class=\"resultDuplicates\" style=\"display: none;\">");
//						for (PooledStringIterator dpsi = refCluster.getDuplicateIterator(); dpsi.hasNextString();) {
//							PooledString dps = dpsi.getNextString();
//							this.writeSearchResultRef(dps, true);
//							refCount++;
//							if (dps.isDeleted())
//								deletedRefIDs.addElement(dps.id);
//						}
//						this.writeLine("</div>");
//						this.writeLine("</td></tr>");
//					}
//				}
				this.writeLine("</table>");
				
//				this.writeLine("<script type=\"text/javascript\">");
//				this.writeLine("function buildDeletedArray() {");
//				this.writeLine("  deletedRefIDs = new Array(" + deletedRefIDs.size() + ");");
//				for (int d = 0; d < deletedRefIDs.size(); d++) {
//					this.writeLine("  deletedRefIDs[" + d + "] = '" + deletedRefIDs.get(d) + "';");
//					this.writeLine("  deletedRefIdSet['" + deletedRefIDs.get(d) + "'] = 'D';");
//				}
//				this.writeLine("}");
//				this.writeLine("var rcSpan = getById('resultRefCount');");
//				this.writeLine("if (rcSpan != null)");
//				this.writeLine("  rcSpan.appendChild(document.createTextNode('" + refCount + "'));");
//				this.writeLine("var rcDelSpan = getById('resultRefCountDeleted');");
//				this.writeLine("if (rcDelSpan != null)");
//				this.writeLine("  rcDelSpan.appendChild(document.createTextNode('" + deletedRefIDs.size() + "'));");
//				this.writeLine("var rcDupSpan = getById('resultRefCountDuplicate');");
//				this.writeLine("if (rcDupSpan != null)");
//				this.writeLine("  rcDupSpan.appendChild(document.createTextNode('" + (refCount - refClusters.size()) + "'));");
//				this.writeLine("</script>");
				
				this.write("<iframe id=\"" + MINOR_UPDATE_FRAME_ID + "\" height=\"0px\" style=\"border-width: 0px;\" src=\"" + this.request.getContextPath() + this.request.getServletPath() + "/" + MINOR_UPDATE_PATH + "\">");
				this.writeLine("</iframe>");
				
				this.write("<iframe id=\"" + EXPORT_BASKET_UPDATE_FRAME_ID + "\" height=\"0px\" style=\"border-width: 0px;\" src=\"" + this.request.getContextPath() + this.request.getServletPath() + "/" + EXPORT_BASKET_UPDATE_PATH + "\">");
				this.writeLine("</iframe>");
				
				this.write("<script id=\"dynamicSearchScript\" type=\"text/javascript\" src=\"isSetDynamically\">");
				this.writeLine("</script>");
			}
			
//			private void writeSearchResultRef(PooledString ps, boolean isDuplicate) throws IOException {
//				this.writeLine("<div class=\"referenceStringContainer\" id=\"" + ps.id + "\"" + (ps.isDeleted() ? " style=\"display: none;\"" : "") + ">");
//				this.writeLine("<div class=\"referenceString\" id=\"refString" + ps.id + "\" onmousedown=\"return grabReference('" + ps.id + "');\" ondblclick=\"selectRefString('" + ps.id + "');\" onmouseover=\"" + (isDuplicate ? "showOptionsFor" : "setActiveReference") + "('" + ps.id + "');\" onmouseout=\"setActiveReference(null); dragGrabbedReference();\">" + xmlGrammar.escape(ps.getStringPlain()) + "</div>");
//				this.writeLine("<div class=\"referenceStringCredits\" id=\"credits" + ps.id + "\">");
//				this.writeLine("<span class=\"referenceFormatLinkLabel\">Contributed by <b>" + ps.getCreateUser() + "</b> (at <b>" + ps.getCreateDomain() + "</b>)</span>&nbsp;&nbsp;<span class=\"referenceFormatLinkLabel\">Last Updated by <b>" + ps.getUpdateUser() + "</b> (at <b>" + ps.getUpdateDomain() + "</b>)</span>");
//				this.writeLine("</div>");
//				this.writeLine("<div id=\"optionsFor" + ps.id + "\" class=\"resultOptions" + ((ps.getParseChecksum() == null) ? "" : " parsed") + (isDuplicate ? " duplicate" : "") + "\" style=\"display: none;\"></div>");
//				this.writeLine("</div>");
//			}
//			
			protected String[] getOnloadCalls() {
				if ((query != null) || (author != null) || (title != null) || (year != null) || (origin != null)) {
					String[] olcs = {"initDragReference();", "addShowExportBasketButton();", "doDynamicSearch();"};
					return olcs;
				}
				else {
					String[] olcs = {"initDragReference();", "addShowExportBasketButton();"};
					return olcs;
				}
			}
			
			protected void writePageHeadExtensions() throws IOException {
				this.writeLine("<script type=\"text/javascript\">");
				
				//	for testing statically
				this.writeLine("var debugMessage = null;");
				this.writeLine("function showDebugMessage(message) {");
				this.writeLine("  if (debugMessage != null)");
				this.writeLine("    debugMessage.value = message;");
				this.writeLine("}");
				
				//	add object storing export basket ref IDs
				this.writeLine("var exportBasketRefIDs = new Object();");
				LinkedHashSet exportBasket = getExportBasket(this.request, false);
				if (exportBasket != null) {
					for (Iterator eridit = exportBasket.iterator(); eridit.hasNext();)
						this.writeLine("exportBasketRefIDs['" + ((String) eridit.next()) + "'] = 'E';");
				}
				
				//	reacting to mouse movements
				this.writeLine("var activeReferenceId = null;");
				this.writeLine("var showingOptionsFor = null;");
				this.writeLine("function setActiveReference(refId) {");
				this.writeLine("  activeReferenceId = refId;");
				this.writeLine("  showDebugMessage('active reference set to ' + activeReferenceId + ((activeReferenceId == null) ? '' : (', class is ' + getById('optionsFor' + activeReferenceId).className)));");
				this.writeLine("  if (activeReferenceId != null)");
				this.writeLine("    showOptionsFor(activeReferenceId, false);");
				this.writeLine("}");
				
				this.writeLine("function showOptionsFor(refId, isDup) {");
				this.writeLine("  getById('optionsFor' + refId).style.display = '';");
				this.writeLine("  generateOptions(refId);");
				this.writeLine("  if ((showingOptionsFor != null) && (showingOptionsFor != refId))");
				this.writeLine("    getById('optionsFor' + showingOptionsFor).style.display = 'none';");
				this.writeLine("  showingOptionsFor = refId;");
				this.writeLine("}");
				
				//	showing and hiding deleted and duplicate references
				this.writeLine("var deletedRefIDs = new Array();");
				this.writeLine("var deletedRefIdSet = new Object();");
				this.writeLine("var showingDeleted = false;");
				this.writeLine("function toggleDeleted(showDeleted) {");
//				this.writeLine("  if (deletedRefIDs == null)");
//				this.writeLine("    buildDeletedArray();");
				this.writeLine("  showingDeleted = showDeleted;");
				this.writeLine("  for (var d = 0; d < deletedRefIDs.length; d++) {");
				this.writeLine("    var deletedRefRow = getById('row' + deletedRefIDs[d]);");
				this.writeLine("    if (deletedRefRow != null)");
				this.writeLine("      deletedRefRow.style.display = (showDeleted ? '' : 'none');");
				this.writeLine("    var deletedRef = getById(deletedRefIDs[d]);");
				this.writeLine("    if (deletedRef != null)");
				this.writeLine("      deletedRef.style.display = (showDeleted ? '' : 'none');");
				this.writeLine("  }");
				this.writeLine("  var sd = getById('showDeleted');");
				this.writeLine("  if (sd != null)");
				this.writeLine("    sd.style.display = (showDeleted ? 'none' : '');");
				this.writeLine("  var hd = getById('hideDeleted');");
				this.writeLine("  if (hd != null)");
				this.writeLine("    hd.style.display = (showDeleted ? '' : 'none');");
//				this.writeLine("  getById('showDeleted').style.display = (showDeleted ? 'none' : '');");
//				this.writeLine("  getById('hideDeleted').style.display = (showDeleted ? '' : 'none');");
				this.writeLine("  return false;");
				this.writeLine("}");
				
				this.writeLine("function toggleDuplicates(refId) {");
				this.writeLine("  var duplicates = getById('duplicatesOf' + refId);");
				this.writeLine("  var dButton = document.getElementById('dButton' + refId);");
				this.writeLine("  if (dButton.value == 'Show Duplicates') {");
				this.writeLine("    duplicates.style.display = '';");
				this.writeLine("    dButton.value = 'Hide Duplicates';");
				this.writeLine("  }");
				this.writeLine("  else {");
				this.writeLine("    duplicates.style.display = 'none';");
				this.writeLine("    dButton.value = 'Show Duplicates';");
				this.writeLine("  }");
				this.writeLine("}");
				
				//	server callbacks
				this.writeLine("var minorUpdateOverlay = null;");
				this.writeLine("function doMinorUpdateServer(refId, canRefId, deleted) {");
				this.writeLine("  if (debugMessage != null) {");
				this.writeLine("    window.doMinorUpdateClient();");
				this.writeLine("    window.doMinorUpdateClient = null;");
				this.writeLine("    return;");
				this.writeLine("  }");
				this.writeLine("  if (!sendMinorUpdateServer(refId, canRefId, deleted))");
				this.writeLine("    window.doMinorUpdateClient = null;");
				this.writeLine("}");
				
				this.writeLine("function sendMinorUpdateServer(refId, canRefId, deleted) {");
				this.writeLine("  if (!getUser())");
				this.writeLine("    return false;");
				this.writeLine("  var minorUpdateFrame = getById('" + MINOR_UPDATE_FRAME_ID + "');");
				this.writeLine("  if (minorUpdateFrame == null)");
				this.writeLine("    return false;");
				this.writeLine("  var minorUpdateForm = minorUpdateFrame.contentWindow.getById('" + MINOR_UPDATE_FORM_ID + "');");
				this.writeLine("  if (minorUpdateForm == null)");
				this.writeLine("    return false;");
				this.writeLine("  if (refId != null) {");
				this.writeLine("    var refIdField = minorUpdateFrame.contentWindow.getById('" + STRING_ID_ATTRIBUTE + "');");
				this.writeLine("    if (refIdField == null)");
				this.writeLine("      return false;");
				this.writeLine("    refIdField.value = refId;");
				this.writeLine("  }");
				this.writeLine("  var deletedField = minorUpdateFrame.contentWindow.getById('" + DELETED_ATTRIBUTE + "');");
				this.writeLine("  if (deletedField == null)");
				this.writeLine("    return false;");
				this.writeLine("  deletedField.value = deleted;");
				this.writeLine("  if (canRefId != null) {");
				this.writeLine("    var canRefIdField = minorUpdateFrame.contentWindow.getById('" + CANONICAL_STRING_ID_ATTRIBUTE + "');");
				this.writeLine("    if (canRefIdField == null)");
				this.writeLine("      return false;");
				this.writeLine("    canRefIdField.value = canRefId;");
				this.writeLine("  }");
				this.writeLine("  var userField = minorUpdateFrame.contentWindow.getById('" + USER_PARAMETER + "');");
				this.writeLine("  if (userField == null)");
				this.writeLine("    return false;");
				this.writeLine("  userField.value = user;");
				this.writeLine("  minorUpdateOverlay = getOverlay(null, 'minorUpdateOverlay', true);");
				this.writeLine("  minorUpdateForm.submit();");
				this.writeLine("  window.setTimeout('waitMinorUpdateServer(0)', 250);");
				this.writeLine("  return true;");
				this.writeLine("}");
				
				this.writeLine("function waitMinorUpdateServer(round) {");
				this.writeLine("  if (round > 20) {");
				this.writeLine("    alert('The server did not reply in time, please try again later.');");
				this.writeLine("    window.doMinorUpdateClient = null;");
				this.writeLine("    removeElement(minorUpdateOverlay);");
				this.writeLine("    minorUpdateOverlay = null;");
				this.writeLine("    return;");
				this.writeLine("  }");
				this.writeLine("  var minorUpdateFrame = getById('" + MINOR_UPDATE_FRAME_ID + "');");
				this.writeLine("  if (!minorUpdateFrame.contentWindow.getById) {");
				this.writeLine("    window.setTimeout(('waitMinorUpdateServer(' + (round+1) + ')'), 250);");
				this.writeLine("    return;");
				this.writeLine("  }");
				this.writeLine("  var minorUpdateForm = minorUpdateFrame.contentWindow.getById('" + MINOR_UPDATE_FORM_ID + "');");
				this.writeLine("  if (minorUpdateForm == null) {");
				this.writeLine("    window.setTimeout(('waitMinorUpdateServer(' + (round+1) + ')'), 250);");
				this.writeLine("    return;");
				this.writeLine("  }");
				this.writeLine("  var resultField = minorUpdateFrame.contentWindow.getById('" + MINOR_UPDATE_RESULT_ATTRIBUTE + "');");
				this.writeLine("  if (resultField == null) {");
				this.writeLine("    window.setTimeout(('waitMinorUpdateServer(' + (round+1) + ')'), 250);");
				this.writeLine("    return;");
				this.writeLine("  }");
				this.writeLine("  if (resultField.value == 'OK')");
				this.writeLine("    doMinorUpdateClient();");
				this.writeLine("  else alert('An error occurred on the server, please try again later.');");
				this.writeLine("  minorUpdateFrame.contentWindow.minorUpdateResultRead();");
				this.writeLine("  window.doMinorUpdateClient = null;");
				this.writeLine("  removeElement(minorUpdateOverlay);");
				this.writeLine("  minorUpdateOverlay = null;");
				this.writeLine("}");
				
				//	deleting and un-deleting
				this.writeLine("function setDeleted(refId, deleted) {");
				this.writeLine("  showDebugMessage('deleted ' + refId + ' set to ' + deleted);");
//				this.writeLine("  if (deletedRefIDs == null)");
//				this.writeLine("    buildDeletedArray();");
				this.writeLine("  window.doMinorUpdateClient = function() {setDeletedBrowser(refId, deleted);}");
				this.writeLine("  doMinorUpdateServer(refId, null, deleted);");
				this.writeLine("}");
				
				this.writeLine("function setDeletedBrowser(refId, deleted) {");
				this.writeLine("  getById('row' + refId).style.display = ((!showingDeleted && deleted) ? 'none' : '');");
				this.writeLine("  var dudButton = getById('dudButton' + refId);");
				this.writeLine("  if (deleted) {");
				this.writeLine("    deletedRefIDs[deletedRefIDs.length] = refId;");
				this.writeLine("    deletedRefIdSet[refId] = 'D';");
				this.writeLine("    dudButton.value = 'Un-Delete';");
				this.writeLine("    dudButton.onclick = function() {setDeleted(refId, false); return false;};");
				this.writeLine("  }");
				this.writeLine("  else {");
				this.writeLine("    for (var d = 0; d < deletedRefIDs.length; d++) {");
				this.writeLine("      if (deletedRefIDs[d] == refId) {");
				this.writeLine("        deletedRefIDs[d] = '';");
				this.writeLine("        d = deletedRefIDs.length;");
				this.writeLine("      }");
				this.writeLine("    }");
				this.writeLine("    deletedRefIdSet[refId] = null;");
				this.writeLine("    dudButton.value = 'Delete';");
				this.writeLine("    dudButton.onclick = function() {setDeleted(refId, true); return false;};");
				this.writeLine("  }");
				this.writeLine("  return false;");
				this.writeLine("}");
				
				//	shoving around duplicates
				this.writeLine("function setCanonicalId(refId, canRefId, mode) {");
				this.writeLine("  window.doMinorUpdateClient = function() {setCanonicalIdBrowser(refId, canRefId, mode);}");
				this.writeLine("  doMinorUpdateServer(((mode == 'MR') ? null : refId), canRefId, false);");
				this.writeLine("}");
				
				this.writeLine("function setCanonicalIdBrowser(refId, canRefId, mode) {");
				this.writeLine("  var resultTable = getById('resultTable');");
				// move former duplicate to top level
				this.writeLine("  if ((mode == 'ND') || (mode == 'MR')) {");
				this.writeLine("    var ref = getById(canRefId);");
				this.writeLine("    var canRefRow = ref;");
				this.writeLine("    while ((canRefRow != null) && (canRefRow.className != 'resultTableRow'))");
				this.writeLine("      canRefRow = canRefRow.parentNode;");
				this.writeLine("    ref.parentNode.removeChild(ref);");
				this.writeLine("    var refDupContainer = newElement('div', ('duplicatesOf' + canRefId), 'resultDuplicates', null);");
				this.writeLine("    setAttribute(refDupContainer, 'style', 'display: none;');");
				this.writeLine("    var refRow = newElement('tr', ('row' + canRefId), 'resultTableRow', null);");
				this.writeLine("    var refCell = newElement('td', null, 'resultTableCell', null);");
				this.writeLine("    setAttribute(refCell, 'colspan', '2');");
				this.writeLine("    refCell.appendChild(ref);");
				this.writeLine("    refCell.appendChild(refDupContainer);");
				this.writeLine("    refRow.appendChild(refCell);");
				this.writeLine("    if ((canRefRow == null) || (canRefRow.nextSibling == null))");
				this.writeLine("      getById('resultTable').appendChild(refRow);");
				this.writeLine("    else canRefRow.parentNode.insertBefore(refRow, canRefRow.nextSibling);");
				this.writeLine("    clearOptions(canRefId);");
				this.writeLine("    var refString = getById('refString' + canRefId);");
				this.writeLine("    refString.onmouseover = function() {setActiveReference(canRefId);};");
				this.writeLine("    var refOptions = getById('optionsFor' + canRefId);");
				this.writeLine("    refOptions.className = 'resultOptions' + ((refOptions.className.indexOf('parsed') == -1) ? '' : ' parsed');");
				this.writeLine("  }");
				// coming from 'not a duplicate', we're done
				this.writeLine("  if (mode == 'ND')");
				this.writeLine("    return; ");
				this.writeLine("  var dRefRow = getById('row' + refId);");
				// moving duplicate around
				this.writeLine("  if ((mode == 'MD') && (dRefRow == null)) {");
				this.writeLine("    var dRef = getById(refId);");
				this.writeLine("    dRef.parentNode.removeChild(dRef);");
				this.writeLine("    var cRefDupContainer = getById('duplicatesOf' + canRefId);");
				this.writeLine("    cRefDupContainer.appendChild(dRef);");
				this.writeLine("    clearOptions(refId);");
				this.writeLine("    return;");
				this.writeLine("  }");
				// marking duplicate
				this.writeLine("  if ((mode == 'MD') || (mode == 'MR')) {");
				// move over duplicates
				this.writeLine("    var cRefDupContainer = getById('duplicatesOf' + canRefId);");
				this.writeLine("    var dRefDupContainer = getById('duplicatesOf' + refId);");
				this.writeLine("    while (dRefDupContainer.firstChild != null) {");
				this.writeLine("      var dRefDup = dRefDupContainer.firstChild;");
				this.writeLine("      if (dRefDup.id != null)");
				this.writeLine("        clearOptions(dRefDup.id);");
				this.writeLine("      dRefDupContainer.removeChild(dRefDup);");
				this.writeLine("      cRefDupContainer.appendChild(dRefDup);");
				this.writeLine("    }");
				// disable dropping on reference
				this.writeLine("    var dRefString = getById('refString' + refId);");
				this.writeLine("    dRefString.onmouseover = function() {showOptionsFor(refId, true);};");
				// adjust CSS class of options
				this.writeLine("    var dRefOptions = getById('optionsFor' + refId);");
				this.writeLine("    dRefOptions.className = 'resultOptions' + ((dRefOptions.className.indexOf('parsed') == -1) ? '' : ' parsed') + ' duplicate';");
				// move reference to duplicate container
				this.writeLine("    var dRef = getById(refId);");
				this.writeLine("    dRef.parentNode.removeChild(dRef);");
				this.writeLine("    cRefDupContainer.appendChild(dRef);");
				this.writeLine("    clearOptions(refId);");
				// remove table row
				this.writeLine("    dRefRow.parentNode.removeChild(dRefRow);");
				this.writeLine("  }");
				this.writeLine("}");
				
				//	handling of export basket
				writeExportBasketFunctions(this, false, false);
				
				//	generators for style and format buttons and contribution buttons
				this.write("var formats = new Array(");
				for (Iterator fit = formats.keySet().iterator(); fit.hasNext();) {
					this.write("'" + ((String) fit.next()) + "'");
					if (fit.hasNext())
						this.write(", ");
				}
				this.writeLine(");");
				String[] styles = BibRefUtils.getRefStringStyles();
				this.write("var styles = new Array(");
				for (int s = 0; s < styles.length; s++) {
					if (s != 0)
						this.write(", ");
					this.write("'" + styles[s] + "'");
				}
				this.writeLine(");");
				this.writeLine("var subWindowBaseUrl = '" + this.request.getContextPath() + this.request.getServletPath() + "';");
				this.writeLine("function clearOptions(id) {");
				this.writeLine("  var options = getById('optionsFor' + id);");
				this.writeLine("  while (options.firstChild != null)");
				this.writeLine("    options.removeChild(options.firstChild);");
				this.writeLine("}");
				
				this.writeLine("function generateOptions(refId) {");
				this.writeLine("  var options = getById('optionsFor' + refId);");
				this.writeLine("  if ((options.firstChild != null) && (options.firstChild.nodeName.toLowerCase() == 'span'))");
				this.writeLine("    return;");
//				this.writeLine("  if (deletedRefIDs == null)");
//				this.writeLine("    buildDeletedArray();");
				this.writeLine("  showDebugMessage('generating options for ' + refId);");
				this.writeLine("  clearOptions(refId);");
				this.writeLine("  if (options.className.indexOf('duplicate') != -1) {");
				this.writeLine("    var cRefDupContainer = options;");
				this.writeLine("    while ((cRefDupContainer != null) && (cRefDupContainer.className != 'resultDuplicates'))");
				this.writeLine("      cRefDupContainer = cRefDupContainer.parentNode;");
				this.writeLine("    if (cRefDupContainer != null) {");
				this.writeLine("      options.appendChild(newElement('span', null, 'referenceFormatLinkLabel', 'Contribute to Bibliography:'));");
				this.writeLine("      var rButton = addFunctionButton(options, 'Make Representative', 'Make this reference the representative of this duplicate cluster', function() {setCanonicalId(cRefDupContainer.id.substring(12), refId, 'MR'); return false;});");
				this.writeLine("      setAttribute(rButton, 'id', ('rButton' + refId));");
				this.writeLine("      var ndButton = addFunctionButton(options, 'Not a Duplicate', 'Remove this reference from this cluster and make it a top level reference', function() {setCanonicalId(refId, refId, 'ND'); return false;});");
				this.writeLine("      setAttribute(ndButton, 'id', ('ndButton' + refId));");
				this.writeLine("    }");
				this.writeLine("    return;");
				this.writeLine("  }");
				this.writeLine("  if (options.className.indexOf('parsed') != -1) {");
				this.writeLine("    options.appendChild(newElement('span', null, 'referenceFormatLinkLabel', 'Additional Formats & Styles:'));");
				this.writeLine("    for (var f = 0; f < formats.length; f++)");
				this.writeLine("      addOpenWindowButton(options, formats[f], ('Get this reference formatted as ' + formats[f]), (subWindowBaseUrl + '/" + FRAME_PAGE_PATH + "?" + STRING_ID_ATTRIBUTE + "=' + refId + '&" + FORMAT_PARAMETER + "=' + formats[f]), 'Formatted Reference');");
				this.writeLine("    options.appendChild(document.createTextNode('  '));");
				this.writeLine("    for (var s = 0; s < styles.length; s++)");
				this.writeLine("      addOpenWindowButton(options, styles[s], ('Get this reference formatted in ' + styles[s] + ' style'), (subWindowBaseUrl + '/" + FRAME_PAGE_PATH + "?" + STRING_ID_ATTRIBUTE + "=' + refId + '&" + STYLE_PARAMETER + "=' + styles[s]), 'Styled Reference');");
				this.writeLine("    options.appendChild(document.createTextNode('  '));");
				this.writeLine("    var isInEb = (exportBasketRefIDs[refId] == 'E');");
				this.writeLine("    var ebButton = addFunctionButton(options, (isInEb ? 'Do Not Export' : 'Export'), (isInEb ? 'Remove this reference from your Export Basket' : 'Add this reference to your Export Basket'), function() {updateExportBasket(refId, isInEb); return false;});");
				this.writeLine("    setAttribute(ebButton, 'id', ('ebButton' + refId));");
				this.writeLine("    options.appendChild(newElement('br'));");
				this.writeLine("  }");
				this.writeLine("  options.appendChild(newElement('span', null, 'referenceFormatLinkLabel', 'Contribute to Bibliography:'));");
				if (refParserUrl != null) {
					this.writeLine("  if (options.className.indexOf('parsed') != -1)");
					this.writeLine("    addOpenWindowButton(options, 'Refine Parsed Reference', 'Refine or correct the parsed version of this bibliographic reference', (subWindowBaseUrl + '/" + FRAME_PAGE_PATH + "?" + STRING_ID_ATTRIBUTE + "=' + refId + '&" + FORMAT_PARAMETER + "=" + PARSE_REF_FORMAT + "'), 'Parse Reference');");
					this.writeLine("  else addOpenWindowButton(options, 'Parse Reference', 'Parse this bibliographic reference so formatted versions become available', (subWindowBaseUrl + '/" + FRAME_PAGE_PATH + "?" + STRING_ID_ATTRIBUTE + "=' + refId + '&" + FORMAT_PARAMETER + "=" + PARSE_REF_FORMAT + "'), 'Parse Reference');");
				}
				if (refEditorUrl != null)
					this.writeLine("  addOpenWindowButton(options, 'Edit Reference', 'Correct this bibliographic reference string, e.g. to eliminate typos or punctuation errors', (subWindowBaseUrl + '/" + FRAME_PAGE_PATH + "?" + STRING_ID_ATTRIBUTE + "=' + refId + '&" + FORMAT_PARAMETER + "=" + EDIT_REF_FORMAT + "'), 'Edit Reference');");
				this.writeLine("  var dudButton = addFunctionButton(options, ((deletedRefIdSet[refId] == 'D') ? 'Un-Delete' : 'Delete'), null, function() {setDeleted(refId, (deletedRefIdSet[refId] != 'D')); return false;});");
				this.writeLine("  setAttribute(dudButton, 'id', ('dudButton' + refId));");
				this.writeLine("  var dButton = addFunctionButton(options, 'Show Duplicates', null, function() {toggleDuplicates(refId); return false;});");
				this.writeLine("  setAttribute(dButton, 'id', ('dButton' + refId));");
				this.writeLine("}");
				
				this.writeLine("function addOpenWindowButton(node, text, tooltip, url, title) {");
				this.writeLine("  var button = newElement('input');");
				this.writeLine("  setAttribute(button, 'type', 'button');");
				this.writeLine("  setAttribute(button, 'class', 'referenceFormatLink');");
				this.writeLine("  setAttribute(button, 'value', text);");
				this.writeLine("  setAttribute(button, 'title', tooltip);");
				this.writeLine("  button.onclick = function() {");
				this.writeLine("    var w = window.open(url, title, 'width=500,height=400,top=100,left=100,resizable=yes,scrollbar=yes,scrollbars=yes');");
				this.writeLine("    w.notifyExportBucketUpdated = window.notifyExportBucketUpdated;");
				this.writeLine("    return false;");
				this.writeLine("  };");
				this.writeLine("  node.appendChild(button);");
				this.writeLine("  return button;");
				this.writeLine("}");
				
				this.writeLine("function addFunctionButton(node, text, tooltip, onclick) {");
				this.writeLine("  var button = newElement('input');");
				this.writeLine("  setAttribute(button, 'type', 'button');");
				this.writeLine("  setAttribute(button, 'class', 'referenceFormatLink');");
				this.writeLine("  setAttribute(button, 'value', text);");
				this.writeLine("  setAttribute(button, 'title', tooltip);");
				this.writeLine("  button.onclick = onclick;");
				this.writeLine("  node.appendChild(button);");
				this.writeLine("  return button;");
				this.writeLine("}");
				
				//	mark reference string for copy & paste
				this.writeLine("function selectRefString(id) {");
				this.writeLine("  var refString = getById('refString' + id);");
				this.writeLine("  if (window.getSelection && document.createRange) {");
				this.writeLine("    var sel = window.getSelection();");
				this.writeLine("    var range = document.createRange();");
				this.writeLine("    range.selectNodeContents(refString);");
				this.writeLine("    sel.removeAllRanges();");
				this.writeLine("    sel.addRange(range);");
				this.writeLine("  }");
				this.writeLine("  else if (document.selection && document.body.createTextRange) {");
				this.writeLine("    var textRange = document.body.createTextRange();");
				this.writeLine("    textRange.moveToElementText(refString);");
				this.writeLine("    textRange.select();");
				this.writeLine("  }");
				this.writeLine("}");
				
				//	drag & drop for duplicate removal
				this.writeLine("var grabbedId = null;");
				this.writeLine("var draggingId = null;");
				this.writeLine("var dragging = null;");
				this.writeLine("var dragPosX = 0;");
				this.writeLine("var dragPosY = 0;");
				this.writeLine("function initDragReference() {");
				this.writeLine("  document.onmouseup = function() {");
				this.writeLine("    endDragReference();");
				this.writeLine("  };");
				this.writeLine("  document.onmousemove = function(e) {");
				this.writeLine("    dragPosX = e.pageX;");
				this.writeLine("    dragPosY = e.pageY;");
				this.writeLine("    if (dragging == null)");
				this.writeLine("      return;");
				this.writeLine("    dragging.style.left = dragPosX + 'px';");
				this.writeLine("    dragging.style.top = dragPosY + 'px';");
				this.writeLine("  };");
				this.writeLine("}");
				
				this.writeLine("function grabReference(id) {");
				this.writeLine("  grabbedId = id;");
				this.writeLine("  return false;");
				this.writeLine("}");
				
				this.writeLine("function dragGrabbedReference() {");
				this.writeLine("  if (draggingId != null)");
				this.writeLine("    grabbedId = null;");
				this.writeLine("  if (grabbedId != null)");
				this.writeLine("    startDragReference(grabbedId);");
				this.writeLine("  grabbedId = null;");
				this.writeLine("  if (document.selection)");
				this.writeLine("    document.selection.empty();");
				this.writeLine("  else if (window.getSelection)");
				this.writeLine("    window.getSelection().removeAllRanges();");
				this.writeLine("}");
				
				this.writeLine("function startDragReference(id) {");
				this.writeLine("  showDebugMessage('dragging ' + id);");
				this.writeLine("  draggingId = id;");
				this.writeLine("  ");
				this.writeLine("  var bodyRoot = document.getElementsByTagName('body')[0];");
				this.writeLine("  var dragged = getById('refString' + id);");
				this.writeLine("  dragging = newElement('div', null, 'referenceString', dragged.innerHTML);");
				this.writeLine("  dragging.style.display = '';");
				this.writeLine("  dragging.style.visibility = 'visible';");
				this.writeLine("  dragging.style.position = 'absolute';");
				this.writeLine("  dragging.style.opacity = 0.5;");
				this.writeLine("  dragging.style.className = 'draggingReference';");
				this.writeLine("  dragging.style.width = dragged.offsetWidth + 'px';");
				this.writeLine("  dragging.style.left = dragPosX + 'px';");
				this.writeLine("  dragging.style.top = dragPosY + 'px';");
				this.writeLine("  bodyRoot.appendChild(dragging);");
				this.writeLine("  return false;");
				this.writeLine("}");
				
				this.writeLine("function endDragReference() {");
				this.writeLine("  if ((draggingId != null) && (draggingId != activeReferenceId) && (activeReferenceId != null)) {");
				this.writeLine("    showDebugMessage('dragged to ' + activeReferenceId);");
				this.writeLine("    setCanonicalId(draggingId, activeReferenceId, 'MD');");
				this.writeLine("  }");
				this.writeLine("  else showDebugMessage('drag cancelled on ' + activeReferenceId);");
				this.writeLine("  grabbedId = null;");
				this.writeLine("  draggingId = null;");
				this.writeLine("  if (dragging != null)");
				this.writeLine("    dragging.parentNode.removeChild(dragging);");
				this.writeLine("  dragging = null;");
				this.writeLine("}");
				
				this.writeLine("var resultTableHeader = null;");
				this.writeLine("var refCount = 0;");
				this.writeLine("var deletedRefCount = 0;");
				this.writeLine("var duplicateRefCount = 0;");
				
				//	TODO put this function in a file to allow for customization, and document it
				this.writeLine("function displayResultRef(refId, refString, createUser, createDomain, updateUser, updateDomain, isParseAvailable, isDeleted, canonicalRefId) {");
				this.writeLine("  refCount++;");
				this.writeLine("  if (isDeleted)");
				this.writeLine("    deletedRefCount++;");
				this.writeLine("  if ((canonicalRefId != null) && (canonicalRefId != refId))");
				this.writeLine("    duplicateRefCount++;");
				
				this.writeLine("  var rs = newElement('div', ('refString' + refId), 'referenceString', refString);");
				this.writeLine("  rs.onmouseover = function() {");
				this.writeLine("    setActiveReference(refId);");
				this.writeLine("  };");
				this.writeLine("  rs.onmouseout = function() {");
				this.writeLine("    setActiveReference(null);");
				this.writeLine("    dragGrabbedReference();");
				this.writeLine("  };");
				this.writeLine("  rs.onmousedown = function() {");
				this.writeLine("    return grabReference(refId);");
				this.writeLine("  };");
				this.writeLine("  rs.ondblclick = function() {");
				this.writeLine("    selectRefString(refId);");
				this.writeLine("  };");
				
				this.writeLine("  var rc = newElement('div', ('credits' + refId), 'referenceStringCredits');");
				this.writeLine("  var rcc = newElement('span', null, 'referenceFormatLinkLabel', 'Contributed by ');");
				this.writeLine("  rcc.appendChild(newElement('b', null, null, createUser));");
				this.writeLine("  rcc.appendChild(document.createTextNode(' (at '));");
				this.writeLine("  rcc.appendChild(newElement('b', null, null, createDomain));");
				this.writeLine("  var ruc = newElement('span', null, 'referenceFormatLinkLabel', 'Last Updated by ');");
				this.writeLine("  ruc.appendChild(newElement('b', null, null, updateUser));");
				this.writeLine("  ruc.appendChild(document.createTextNode(' (at '));");
				this.writeLine("  ruc.appendChild(newElement('b', null, null, updateDomain));");
				this.writeLine("  rc.appendChild(rcc);");
				this.writeLine("  rc.appendChild(document.createTextNode('  '));");
				this.writeLine("  rc.appendChild(ruc);");
				
				this.writeLine("  var ro = newElement('div', ('optionsFor' + refId), ('resultOptions' + (isParseAvailable ? ' parsed' : '') + (((canonicalRefId == null) || (canonicalRefId == refId)) ? '' : ' duplicate')));");
				this.writeLine("  setAttribute(ro, 'style', 'display: none;');");
				
				this.writeLine("  var rsc = newElement('div', refId, 'referenceStringContainer');");
				this.writeLine("  rsc.appendChild(rs);");
				this.writeLine("  rsc.appendChild(rc);");
				this.writeLine("  rsc.appendChild(ro);");
				this.writeLine("  if (isDeleted)");
				this.writeLine("    setAttribute(rsc, 'style', 'display: none;');");
				
				this.writeLine("  if ((canonicalRefId == null) || (canonicalRefId == refId)) {");
				this.writeLine("    var rdc = newElement('div', ('duplicatesOf' + refId), 'resultDuplicates');");
				this.writeLine("    setAttribute(rdc, 'style', 'display: none;');");
				this.writeLine("    var rtd = newElement('td', null, 'resultTableCell');");
				this.writeLine("    setAttribute(rtd, 'colspan', '2');");
				this.writeLine("    rtd.appendChild(rsc);");
				this.writeLine("    rtd.appendChild(rdc);");
				this.writeLine("    var rtr = newElement('tr', ('row' + refId), 'resultTableRow');");
				this.writeLine("    rtr.appendChild(rtd);");
				this.writeLine("    var rt = getById('resultTable');");
				this.writeLine("    rt.style.display = '';");
				this.writeLine("    if (resultTableHeader != null) {");
				this.writeLine("      rt.appendChild(resultTableHeader);");
				this.writeLine("      resultTableHeader = null;");
				this.writeLine("    }");
				this.writeLine("    rt.appendChild(rtr);");
				this.writeLine("  }");
				this.writeLine("  else {");
				this.writeLine("    var dc = getById('duplicatesOf' + canonicalRefId);");
				this.writeLine("    if (dc != null)");
				this.writeLine("      dc.appendChild(rsc);");
				this.writeLine("  }");
				
				this.writeLine("  if (isDeleted) {");
				this.writeLine("    deletedRefIDs[deletedRefIDs.length] = refId;");
				this.writeLine("    deletedRefIdSet[refId] = 'D';");
				this.writeLine("  }");
				this.writeLine("}");
				
				this.writeLine("function updateSearchResultStats(total) {");
				this.writeLine("  document.title = 'RefBank Search Result';");
				this.writeLine("  var rt = getById('resultTable');");
				this.writeLine("  rt.style.display = '';");
				this.writeLine("  if (resultTableHeader != null) {");
				this.writeLine("    rt.appendChild(resultTableHeader);");
				this.writeLine("    resultTableHeader = null;");
				this.writeLine("  }");
				this.writeLine("  if (refCount == 0) {");
				this.writeLine("    var nrtd = newElement('td', null, 'resultTableCell', 'Your search did not return any results, sorry.');");
				this.writeLine("    setAttribute(nrtd, 'colspan', '2');");
				this.writeLine("    rt.appendChild(nrtd);");
				this.writeLine("  }");
				this.writeLine("  setNodeText('resultRefCount', ('' + refCount));");
				this.writeLine("  refCount = 0;");
				this.writeLine("  setNodeText('resultRefCountDeleted', ('' + deletedRefCount));");
				this.writeLine("  deletedRefCount = 0;");
				this.writeLine("  setNodeText('resultRefCountDuplicate', ('' + duplicateRefCount));");
				this.writeLine("  duplicateRefCount = 0;");
				this.writeLine("  setNodeText('refCountTotal', ('' + total));");
				this.writeLine("}");
				
				this.writeLine("function setNodeText(id, text) {");
				this.writeLine("  var node = getById(id);");
				this.writeLine("  if (node == null)");
				this.writeLine("    return;");
				this.writeLine("  while (node.firstChild != null)");
				this.writeLine("    removeElement(node.firstChild);");
				this.writeLine("  node.appendChild(document.createTextNode(text));");
				this.writeLine("}");
				
				this.writeLine("</script>");
			}
			
			protected String getPageTitle(String title) {
//				return ("RefBank Search" + ((psi == null) ? "" : " Results"));
				return "RefBank Search";
			}
		};
	}
//	
//	private static class SearchResultRefCluster {
//		final String id;
//		private PooledString representative;
//		private HashSet duplicates = new HashSet();
//		private boolean cluserDeleted = true;
//		SearchResultRefCluster(String id) {
//			this.id = id;
//		}
//		void add(PooledString ps) {
//			if (ps.id.equals(ps.getCanonicalStringID()))
//				this.representative = ps;
//			else this.duplicates.add(ps);
//			if (!ps.isDeleted())
//				this.cluserDeleted = false;
//		}
//		PooledString getRepresentative() {
//			return this.representative;
//		}
//		boolean isClusterDeleted() {
//			return this.cluserDeleted;
//		}
//		PooledStringIterator getDuplicateIterator() {
//			final Iterator dupIt = this.duplicates.iterator();
//			return new PooledStringIterator() {
//				public boolean hasNextString() {
//					return dupIt.hasNext();
//				}
//				public PooledString getNextString() {
//					return ((PooledString) dupIt.next());
//				}
//				public IOException getException() {
//					return null;
//				}
//			};
//		}
//	}
}