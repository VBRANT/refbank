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
package de.uka.ipd.idaho.refBank.apps.webInterface;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.TreeMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefTypeSystem.BibRefType;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils.RefData;
import de.uka.ipd.idaho.refBank.RefBankClient;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * Search facility for RefBank.
 * 
 * @author sautter
 */
public class RefBankSearchServlet extends RefBankWiServlet {
	
	private static final String IS_FRAME_PAGE_PARAMETER = "isFramePage";
	
	private static final String YEAR_PARAMETER = "year";
	
	private TreeMap formats = new TreeMap(String.CASE_INSENSITIVE_ORDER);
	
	private static final String PARSE_REF_FORMAT = "PaRsEtHeReF";
	private static final String EDIT_REF_FORMAT = "EdItReFsTrInG";
	
	private static final String MINOR_UPDATE_FORM_REF_ID = "MiNoRuPdAtE";
	
	private static final String DELETED_PARAMETER = "deleted";
	
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
		
		//	read available data formats and respective XSL transformers
		Settings formats = this.config.getSubset("format");
		String[] formatNames = formats.getKeys();
		for (int f = 0; f < formatNames.length; f++) {
			String xsltName = formats.getSetting(formatNames[f]);
			try {
				Transformer xslt = XsltUtils.getTransformer(new File(this.dataFolder, xsltName));
				this.formats.put(formatNames[f], xslt);
			} catch (IOException ioe) {}
		}
		
		//	load reference string styles
		Settings styles = this.config.getSubset("style");
		String[] styleNames = styles.getKeys();
		for (int s = 0; s < styleNames.length; s++) {
			String xsltName = styles.getSetting(styleNames[s]);
			try {
				Transformer xslt = XsltUtils.getTransformer(new File(this.dataFolder, xsltName));
				BibRefUtils.addRefStringStyle(styleNames[s], xslt);
			} catch (IOException ioe) {}
		}
		
		//	get layout for styled references
		this.styledRefLayout = this.getSetting("styledRefLayout", this.styledRefLayout);
		
		//	get link to reference parser and reference string editor
		this.refParserUrl = this.getSetting("refParserUrl");
		this.refEditorUrl = this.getSetting("refEditorUrl");
		
		//	get type system
		String refTypeSystemPath = this.getSetting("refTypeSystemPath");
		this.refTypeSystem = ((refTypeSystemPath == null) ? BibRefTypeSystem.getDefaultInstance() : BibRefTypeSystem.getInstance(new File(this.webInfFolder, refTypeSystemPath), true));
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	get parameters
		String id = request.getParameter(STRING_ID_ATTRIBUTE);
		String canonicalId = request.getParameter(CANONICAL_STRING_ID_ATTRIBUTE);
		String deleted = request.getParameter(DELETED_PARAMETER);
		String user = request.getParameter(USER_PARAMETER);
		if (user == null)
			user = "Anonymous";
		
		//	make reference cluster representative
		if (canonicalId != null) {
			System.out.println("Making reference " + id + " representative of its cluster");
			RefBankClient rbc = this.getRefBankClient();
			PooledString ps = rbc.getString(canonicalId);
			if (ps != null) {
				String prevCanonicalId = ps.getCanonicalStringID();
				if (!ps.id.equals(prevCanonicalId)) {
					PooledStringIterator psi = rbc.getLinkedStrings(prevCanonicalId);
					if (psi.getException() == null) {
						ArrayList refIDs = new ArrayList();
						while (psi.hasNextString())
							refIDs.add(psi.getNextString().id);
						for (Iterator idit = refIDs.iterator(); idit.hasNext();)
							rbc.setCanonicalStringId(((String) idit.next()), canonicalId, user);
					}
				}
			}
		}
		
		//	delete or undelete reference
		else if ((id != null) && (deleted != null)) {
			System.out.println("Setting deletion status of reference " + id + " to " + deleted);
			RefBankClient rbc = this.getRefBankClient();
			rbc.setDeleted(id, "true".equals(deleted), user);
		}
		
		//	send back form
		this.sendMinorUpdateForm(request, response);
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	retrieve RefBank client on the fly to use local bridge if possible
		RefBankClient rbc = this.getRefBankClient();
		
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
				if ("true".equals(request.getParameter(IS_FRAME_PAGE_PARAMETER)))
					this.sendFormattedReferenceFrame(request, id, format, style, response);
				else if (style != null)
					this.sendStyledReference(request, id, style, response);
				else this.sendFormattedReference(request, id, ((format == null) ? "MODS" : format), response);
				return;
			}
			else if (MINOR_UPDATE_FORM_REF_ID.equals(id)) {
				this.sendMinorUpdateForm(request, response);
				return;
			}
		}
		
		//	query parameters and perform search if given
		PooledStringIterator psi = null;
		
		//	get search parameters
		String canonicalStringId = request.getParameter(CANONICAL_STRING_ID_ATTRIBUTE);
		String query = request.getParameter(QUERY_PARAMETER);
		String type = request.getParameter(TYPE_PARAMETER);
		String user = request.getParameter(USER_PARAMETER);
		String author = request.getParameter(AUTHOR_PARAMETER);
		String title = request.getParameter(TITLE_PARAMETER);
		String date = request.getParameter(DATE_PARAMETER);
		if (date == null) date = request.getParameter(YEAR_PARAMETER);
		String origin = request.getParameter(ORIGIN_PARAMETER);
		String idType = request.getParameter(ID_TYPE_PARAMETER);
		String idValue = request.getParameter(ID_VALUE_PARAMETER);
		
		//	request for specific reference cluster
		if (canonicalStringId != null) {
			psi = rbc.getLinkedStrings(canonicalStringId);
			if (psi.getException() != null)
				throw psi.getException();
		}
		
		//	perform search if query given;
		else if ((query != null) || (author != null) || (title != null) || (date != null) || (origin != null) || ((idType != null) && (idValue != null))) {
			int year = -1;
			if (date != null) try {
				year = Integer.parseInt(date);
			} catch (NumberFormatException nfe) {}
			Properties ids = null;
			if ((idType != null) && (idValue != null)) {
				ids = new Properties();
				ids.setProperty(idType, idValue);
			}
			String[] textPredicates = { query };
			psi = rbc.findReferences(textPredicates, false, type, user, author, title, year, origin, ids, true);
			if (psi.getException() != null)
				throw psi.getException();
		}
		
		//	create page builder
		HtmlPageBuilder pageBuilder = this.getSearchPageBuilder(request, psi, canonicalStringId, query, type, author, title, date, origin, response);
		
		//	send page
		this.sendHtmlPage(pageBuilder);
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
	
	private void sendMinorUpdateForm(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setContentType("text/html");
		response.setCharacterEncoding(ENCODING);
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
		bw.write("<html><head></head><body>");
		bw.newLine();
		bw.write("<form id=\"minorUpdateForm\" method=\"POST\" action=\"" + request.getContextPath() + request.getServletPath() + "\">");
		bw.write("<input type=\"hidden\" name=\"" + CANONICAL_STRING_ID_ATTRIBUTE + "\" value=\"\" id=\"" + CANONICAL_STRING_ID_ATTRIBUTE + "\">");
		bw.write("<input type=\"hidden\" name=\"" + STRING_ID_ATTRIBUTE + "\" value=\"\" id=\"" + STRING_ID_ATTRIBUTE + "\">");
		bw.write("<input type=\"hidden\" name=\"" + DELETED_PARAMETER + "\" value=\"\" id=\"" + DELETED_PARAMETER + "\">");
		bw.write("<input type=\"hidden\" name=\"" + USER_PARAMETER + "\" value=\"\" id=\"" + USER_PARAMETER + "\">");
		bw.write("</form>");
		bw.newLine();
		bw.write("</body></html>");
		bw.newLine();
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
		bw.write("<p style=\"" + this.styledRefLayout + "\">" + sr + "</p>");
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
				if ((ps.getStringParsed() != null) && ((formats.size() + styles.length) > 1)) {
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
				
				this.write("<iframe id=\"minorUpdateFrame\" height=\"0px\" style=\"border-width: 0px;\" src=\"" + this.request.getContextPath() + this.request.getServletPath() + "?" + STRING_ID_ATTRIBUTE + "=" + MINOR_UPDATE_FORM_REF_ID + "\">");
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
				this.writeLine("  var minorUpdateFrame = document.getElementById('minorUpdateFrame');");
				this.writeLine("  if (minorUpdateFrame == null)");
				this.writeLine("    return false;");
				this.writeLine("  var minorUpdateForm = minorUpdateFrame.contentWindow.document.getElementById('minorUpdateForm');");
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
				
//				if ((refParserUrl != null) || (refEditorUrl != null)) {
//					this.writeLine("var user = null;");
//					this.writeLine("function getUser() {");
//					this.writeLine("  if ((user == null) || (user.length == 0)) {");
//					this.writeLine("    var cUser = document.cookie;");
//					this.writeLine("    if ((cUser != null) && (cUser.indexOf('" + USER_PARAMETER + "=') != -1)) {");
//					this.writeLine("      cUser = cUser.substring(cUser.indexOf('" + USER_PARAMETER + "=') + '" + USER_PARAMETER + "='.length);");
//					this.writeLine("      if (cUser.indexOf(';') != -1)");
//					this.writeLine("        cUser = cUser.substring(0, cUser.indexOf(';'));");
//					this.writeLine("      user = unescape(cUser);");
//					this.writeLine("    }");
//					this.writeLine("    if ((user == null) || (user.length == 0)) {");
//					this.writeLine("      user = window.prompt('Please enter a user name so RefBank can credit your contribution', '');");
//					this.writeLine("      if ((user == null) || (user.length == 0))");
//					this.writeLine("        return false;");
//					this.writeLine("      document.cookie = ('" + USER_PARAMETER + "=' + escape(user) + ';domain=' + escape(window.location.hostname) + ';expires=' + new Date(" + (System.currentTimeMillis() + (1000L * 60L * 60L * 24L * 365L * 5L)) + ").toGMTString());");
//					this.writeLine("    }");
//					this.writeLine("  }");
//					this.writeLine("  return true;");
//					this.writeLine("}");
//				}
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
					String editedRefBaseLink = (this.request.getContextPath() + this.request.getServletPath() + 
							"?" + IS_FRAME_PAGE_PARAMETER + "=true" + 
							"&" + FORMAT_PARAMETER + "=MODS" + 
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
				
				this.writeLine("</script>");
			}
		};
		this.sendPopupHtmlPage(pageBuilder);
	}
	
//	/* (non-Javadoc)
//	 * @see de.uka.ipd.idaho.easyIO.web.HtmlServlet#getOnloadCalls()
//	 */
//	public String[] getOnloadCalls() {
//		String[] olcs = {"showUserMenu();"};
//		return olcs;
//	}
//	
//	public void writePageHeadExtensions(HtmlPageBuilder out) throws IOException {
//		out.writeLine("<script type=\"text/javascript\">");
//		
//		out.writeLine("var user = '';");
//		
//		out.writeLine("function getUser(silent) {");
//		out.writeLine("  if (user.length == 0) {");
//		out.writeLine("    var cUser = document.cookie;");
//		out.writeLine("    if ((cUser != null) && (cUser.indexOf('user=') != -1)) {");
//		out.writeLine("      cUser = cUser.substring(cUser.indexOf('user=') + 'user='.length);");
//		out.writeLine("      if (cUser.indexOf(';') != -1)");
//		out.writeLine("        cUser = cUser.substring(0, cUser.indexOf(';'));");
//		out.writeLine("      user = unescape(cUser);");
//		out.writeLine("    }");
//		out.writeLine("    if ((silent == null) && (user.length == 0))");
//		out.writeLine("      editUserName();");
//		out.writeLine("  }");
//		out.writeLine("  return (user.length != 0);");
//		out.writeLine("}");
//		
//		out.writeLine("function editUserName() {");
//		out.writeLine("  var pUser = window.prompt('Please enter a user name so RefBank can credit your contribution', ((user == null) ? '' : user));");
//		out.writeLine("  if (pUser == null)");
//		out.writeLine("    return false;");
//		out.writeLine("  else user = pUser.replace(/^\\s+|\\s+$/g,'');");
//		out.writeLine("  document.cookie = ('user=' + user + ';domain=' + escape(window.location.hostname) + ';expires=' + new Date(1509211565944).toGMTString());");
//		out.writeLine("  var undl = document.getElementById('userNameDisplayLabel');");
//		out.writeLine("  if (undl != null) {");
//		out.writeLine("    if (undl.firstChild)");
//		out.writeLine("      undl.firstChild.nodeValue = ((user.length != 0) ? 'Current screen name:' : 'No screen name specified');");
//		out.writeLine("    else undl.appendChild(document.createTextNode((user.length != 0) ? 'Current screen name:' : 'No screen name specified'));");
//		out.writeLine("  }");
//		out.writeLine("  var unds = document.getElementById('userNameDisplaySpan');");
//		out.writeLine("  if (unds != null) {");
//		out.writeLine("    if (unds.firstChild)");
//		out.writeLine("      unds.firstChild.nodeValue = user;");
//		out.writeLine("    else unds.appendChild(document.createTextNode(user));");
//		out.writeLine("  }");
//		out.writeLine("  return false;");
//		out.writeLine("}");
//		
//		out.writeLine("function showUserMenu() {");
//		out.writeLine("  var bodyRoot = document.getElementsByTagName('body')[0];");
//		out.writeLine("  if (bodyRoot == null)");
//		out.writeLine("    return;");
//		out.writeLine("  var umRoot = document.createElement('div');");
//		out.writeLine("  setAttribute(umRoot, 'width', '100%');");
//		out.writeLine("  setAttribute(umRoot, 'align', 'right');");
//		out.writeLine("  var undl = document.createElement('span');");
//		out.writeLine("  setAttribute(undl, 'id', 'userNameDisplayLabel');");
//		out.writeLine("  undl.appendChild(document.createTextNode(getUser('silent') ? 'Current screen name:' : 'No screen name specified'));");
//		out.writeLine("  umRoot.appendChild(undl);");
//		out.writeLine("  var unds = document.createElement('span');");
//		out.writeLine("  setAttribute(unds, 'id', 'userNameDisplaySpan');");
//		out.writeLine("  setAttribute(unds, 'style', 'margin-left: 5px; font-weight: bold;');");
//		out.writeLine("  unds.appendChild(document.createTextNode(user));");
//		out.writeLine("  umRoot.appendChild(unds);");
//		out.writeLine("  var uneb = document.createElement('a');");
//		out.writeLine("  setAttribute(uneb, 'href', '#');");
//		out.writeLine("  setAttribute(uneb, 'class', 'footerNavigationLink');");
//		out.writeLine("  setAttribute(uneb, 'onclick', 'return editUserName();');");
//		out.writeLine("  setAttribute(uneb, 'style', 'margin-left: 10px;');");
//		out.writeLine("  uneb.appendChild(document.createTextNode('Enter / Edit'));");
//		out.writeLine("  umRoot.appendChild(uneb);");
//		out.writeLine("  bodyRoot.insertBefore(umRoot, bodyRoot.firstChild);");
//		out.writeLine("}");
//		
//		out.writeLine("function setAttribute(node, name, value) {");
//		out.writeLine("  if (!node.setAttributeNode)");
//		out.writeLine("    return;");
//		out.writeLine("  var attribute = document.createAttribute(name);");
//		out.writeLine("  attribute.nodeValue = value;");
//		out.writeLine("  node.setAttributeNode(attribute);");
//		out.writeLine("}");
//		out.writeLine("</script>");
//	}
//	
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
	
	private HtmlPageBuilder getSearchPageBuilder(HttpServletRequest request, final PooledStringIterator psi, final String canonicalStringId, final String query, final String refType, final String author, final String title, final String dateString, final String origin, HttpServletResponse response) throws IOException {
		response.setContentType("text/html");
		response.setCharacterEncoding(ENCODING);
		return new HtmlPageBuilder(this, request, response) {
			protected void include(String type, String tag) throws IOException {
				if ("includeForm".equals(type)) {
					if (canonicalStringId == null)
						this.includeSearchForm();
				}
				else if ("includeResult".equals(type)) {
					if (psi != null)
						this.includeSearchResult();
				}
				else if ("includeRefTypeOptions".equals(type))
					this.includeRefTypeOptions();
				else super.include(type, tag);
			}
			private boolean inSearchForm = false;
			public void storeToken(String token, int treeDepth) throws IOException {
				if (this.inSearchForm && html.isTag(token)) {
					String type = html.getType(token);
					if ("input".equals(type)) {
						TreeNodeAttributeSet tnas = TreeNodeAttributeSet.getTagAttributes(token, html);
						String name = tnas.getAttribute("name");
						if ((name != null) && !tnas.containsAttribute("value")) {
							String value = null;
							if (QUERY_PARAMETER.equals(name)) value = query;
							else if (AUTHOR_PARAMETER.equals(name))
								value = author;
							else if (TITLE_PARAMETER.equals(name))
								value = title;
							else if (DATE_PARAMETER.equals(name))
								value = dateString;
							else if (YEAR_PARAMETER.equals(name))
								value = dateString;
							else if (ORIGIN_PARAMETER.equals(name))
								value = origin;
							if (value != null)
								token = (token.substring(0, (token.length() - 1)) + " value=\"" + xmlGrammar.escape(value) + "\">");
						}
					}
					else if ((refType != null) && "option".equals(type) && !html.isEndTag(token)) {
						TreeNodeAttributeSet tnas = TreeNodeAttributeSet.getTagAttributes(token, html);
						String value = tnas.getAttribute("value");
						if (refType.equals(value))
							token = (token.substring(0, (token.length() - 1)) + " selected>");
					}
				}
				super.storeToken(token, treeDepth);
			}
			
			private void includeSearchForm() throws IOException {
				this.writeLine("<form" +
						" method=\"GET\"" +
						" action=\"" + this.request.getContextPath() + this.request.getServletPath() + "\"" +
						">");
				this.inSearchForm = true;
				this.includeFile("searchFields.html");
				this.inSearchForm = false;
				this.writeLine("</form>");
			}
			
			private void includeRefTypeOptions() throws IOException {
				BibRefType[] refTypes = refTypeSystem.getBibRefTypes();
				for (int t = 0; t < refTypes.length; t++)
					this.storeToken(("<option value=\"" + xmlGrammar.escape(refTypes[t].name) + "\">" + refTypes[t].getLabel() + "</option>"), 0);
			}
			
			private void includeSearchResult() throws IOException {
				this.writeLine("<table class=\"resultTable\">");
				StringVector deletedRefIDs = new StringVector();
				if (!psi.hasNextString()) {
					this.writeLine("<tr class=\"resultTableRow\">");
					this.writeLine("<td class=\"resultTableCell\">");
					this.writeLine("Your search did not return any results, sorry.");
					this.writeLine("</td>");
					this.writeLine("</tr>");
				}
				else {
					this.writeLine("<tr class=\"resultTableRow\">");
					this.writeLine("<td class=\"resultTableCell\" width=\"20%\">");
					this.writeLine("<p class=\"referenceString\" style=\"font-size: 60%;\">Hover&nbsp;references&nbsp;for&nbsp;further&nbsp;options</p>");
					this.writeLine("</td>");
					this.writeLine("<td class=\"resultTableCell\" style=\"text-align: right;\">");
					this.writeLine("<input type=\"button\" id=\"showDeleted\" class=\"referenceFormatLink\" onclick=\"return toggleDeleted(true);\" value=\"Show references flagged as deleted\">");
					this.writeLine("<input type=\"button\" id=\"hideDeleted\" class=\"referenceFormatLink\" style=\"display: none;\" onclick=\"return toggleDeleted(false);\" value=\"Hide references flagged as deleted\">");
					this.writeLine("</td>");
					this.writeLine("</tr>");
					
					while (psi.hasNextString()) {
						PooledString ps = psi.getNextString();
						if ((canonicalStringId == null) && !ps.id.equals(ps.getCanonicalStringID()))
							continue;
						if (ps.isDeleted())
							deletedRefIDs.addElement(ps.id);
						this.writeLine("<tr class=\"resultTableRow\" id=\"ref" + ps.id + "\"" + ((ps.isDeleted() && (canonicalStringId == null)) ? " style=\"display: none;\"" : "") + ">");
						this.writeLine("<td class=\"resultTableCell\" colspan=\"2\">");
						this.writeLine("<p class=\"referenceString" + (ps.id.equals(canonicalStringId) ? " representative" : "") + "\" onmouseover=\"showOptionsFor('" + ps.id + "')\">" + xmlGrammar.escape(ps.getStringPlain()) + "</p>");
						
						//	TODO make link clickable if present
						
						this.writeLine("<span class=\"referenceFormatLinkLabel\">");
						this.writeLine("Contributed by <b>" + ps.getCreateUser() + "</b> (at <b>" + ps.getCreateDomain() + "</b>)");
						this.writeLine("</span>");
						if (ps.getParseChecksum() != null) {
							this.writeLine("&nbsp;&nbsp;");
							this.writeLine("<span class=\"referenceFormatLinkLabel\">");
							this.writeLine("Parsed by <b>" + ps.getUpdateUser() + "</b> (at <b>" + ps.getUpdateDomain() + "</b>)");
							this.writeLine("</span>");
						}
						this.writeLine("<div id=\"optionsFor" + ps.id + "\" class=\"resultOptions\" style=\"display: none;\">");
						String[] styles = BibRefUtils.getRefStringStyles();
						if ((ps.getParseChecksum() != null) && ((formats.size() + styles.length) != 0)) {
							this.writeLine("<span class=\"referenceFormatLinkLabel\">Additional Formats &amp; Styles:</span>");
							for (Iterator fit = formats.keySet().iterator(); fit.hasNext();) {
								String format = ((String) fit.next());
								String formatLink = (this.request.getContextPath() + this.request.getServletPath() + "?" + 
										STRING_ID_ATTRIBUTE + "=" + URLEncoder.encode(ps.id, ENCODING) + "&" + 
										IS_FRAME_PAGE_PARAMETER + "=true&" + 
										FORMAT_PARAMETER + "=" + URLEncoder.encode(format, ENCODING)
									);
								this.writeLine("<input" + 
										" class=\"referenceFormatLink\"" + 
										" type=\"button\"" + 
										" value=\"" + format + "\"" + 
										" title=\"Get this reference formatted as " + format + "\""	+ 
										" onclick=\"" +
											"window.open(" +
												"'" + formatLink + "'" +
												", " +
												"'Parsed Reference'" +
												", " +
												"'width=500,height=400,top=100,left=100,resizable=yes,scrollbar=yes,scrollbars=yes'" +
											");" +
											" return false;\"" + 
										">");
							}
							if ((formats.size() != 0) && (styles.length != 0))
								this.writeLine("&nbsp;");
							for (int s = 0; s < styles.length; s++) {
								String styleLink = (this.request.getContextPath() + this.request.getServletPath() + "?" + 
										STRING_ID_ATTRIBUTE + "=" + URLEncoder.encode(ps.id, ENCODING) + "&" + 
										IS_FRAME_PAGE_PARAMETER + "=true&" + 
										STYLE_PARAMETER + "=" + URLEncoder.encode(styles[s], ENCODING)
									);
								this.writeLine("<input" + 
										" class=\"referenceFormatLink\"" + 
										" type=\"button\"" + 
										" value=\"" + styles[s] + "\"" + 
										" title=\"Get this reference formatted in " + styles[s] + " style\""	+ 
										" onclick=\"" +
											"window.open(" +
												"'" + styleLink + "'" +
												", " +
												"'Formatted Reference'" +
												", " +
												"'width=500,height=400,top=100,left=100,resizable=yes,scrollbar=yes,scrollbars=yes'" +
											");" +
											" return false;\"" + 
										">");
							}
						}
						if ((ps.getParseChecksum() != null) && ((formats.size() + styles.length) != 0))
							this.writeLine("<br>");
						this.writeLine("<span class=\"referenceFormatLinkLabel\">Contribute to Bibliography:</span>");
						if (refParserUrl != null) {
							String parserLink = (this.request.getContextPath() + this.request.getServletPath() + "?" + 
									STRING_ID_ATTRIBUTE + "=" + URLEncoder.encode(ps.id, ENCODING) + "&" +
									IS_FRAME_PAGE_PARAMETER + "=true&" + 
									FORMAT_PARAMETER + "=" + URLEncoder.encode(PARSE_REF_FORMAT, ENCODING));
							this.writeLine("<input" +
									" class=\"referenceFormatLink\"" +
									" type=\"button\"" +
									" value=\"" + ((ps.getParseChecksum() == null) ? "Parse Reference" : "Refine Parsed Reference") + "\"" +
									" title=\"" + ((ps.getParseChecksum() == null) ? "Parse this bibliographic reference so formatted versions become available" : "Refine or correct the parsed version of this bibliographic reference") + "\"" + 
									" onclick=\"" +
										"window.open(" +
											"'" + parserLink + "'" +
											", " +
											"'Parse Reference'" +
											", " +
											"'width=500,height=400,top=100,left=100,resizable=yes,scrollbar=yes,scrollbars=yes'" +
										");" +
										" return false;\"" + 
									">");
						}
						if (refEditorUrl != null) {
							String editorLink = (this.request.getContextPath() + this.request.getServletPath() + "?" + 
									STRING_ID_ATTRIBUTE + "=" + URLEncoder.encode(ps.id, ENCODING) + "&" +
									IS_FRAME_PAGE_PARAMETER + "=true&" + 
									FORMAT_PARAMETER + "=" + URLEncoder.encode(EDIT_REF_FORMAT, ENCODING));
							this.writeLine("<input" +
									" class=\"referenceFormatLink\"" +
									" type=\"button\"" +
									" value=\"Edit Reference\"" +
									" title=\"Correct this bibliographic reference string, e.g. to eliminate typos or punctuation errors\"" + 
									" onclick=\"" +
										"window.open(" +
											"'" + editorLink + "'" +
											", " +
											"'Edit Reference'" +
											", " +
											"'width=500,height=400,top=100,left=100,resizable=yes,scrollbar=yes,scrollbars=yes'" +
										");" +
										" return false;\"" + 
									">");
						}
						
						this.writeLine("<input type=\"button\" id=\"delete" + ps.id + "\" class=\"referenceFormatLink\"" + ((ps.isDeleted() && (canonicalStringId == null)) ? " style=\"display: none;\"" : "") + " onclick=\"return setDeleted('" + ps.id + "', true);\" value=\"Delete\">");
						this.writeLine("<input type=\"button\" id=\"unDelete" + ps.id + "\" class=\"referenceFormatLink\"" + ((ps.isDeleted() && (canonicalStringId == null)) ? "" : " style=\"display: none;\"") + " onclick=\"return setDeleted('" + ps.id + "', false);\" value=\"Un-Delete\">");
						
						if (canonicalStringId == null)
							this.writeLine("<input type=\"button\" id=\"showVersions" + ps.id + "\" class=\"referenceFormatLink\" onclick=\"showVersions('" + ps.id + "');\" value=\"Show All Versions\">");
						else if (!ps.id.equals(canonicalStringId))
							this.writeLine("<input type=\"button\" id=\"makeRepresentative" + ps.id + "\" class=\"referenceFormatLink\" onclick=\"return makeRepresentative('" + ps.id + "');\" value=\"Make Representative\">");
						
						this.writeLine("</div>");
						this.writeLine("</td>");
						this.writeLine("</tr>");
					}
				}
				this.writeLine("</table>");
				
				this.writeLine("<script type=\"text/javascript\">");
				this.writeLine("function buildDeletedArray() {");
				this.writeLine("  deletedRefIDs = new Array(" + deletedRefIDs.size() + ");");
				for (int d = 0; d < deletedRefIDs.size(); d++)
					this.writeLine("  deletedRefIDs[" + d + "] = '" + deletedRefIDs.get(d) + "';");
				this.writeLine("}");
				this.writeLine("</script>");
				
				this.write("<iframe id=\"minorUpdateFrame\" height=\"0px\" style=\"border-width: 0px;\" src=\"" + this.request.getContextPath() + this.request.getServletPath() + "?" + STRING_ID_ATTRIBUTE + "=" + MINOR_UPDATE_FORM_REF_ID + "\">");
				this.writeLine("</iframe>");
			}
			
			protected void writePageHeadExtensions() throws IOException {
				this.writeLine("<script type=\"text/javascript\">");
				
				this.writeLine("var showingOptionsFor = null;");
				this.writeLine("function showOptionsFor(refId) {");
				this.writeLine("  if (showingOptionsFor != null) {");
				this.writeLine("    var showingOptions = document.getElementById('optionsFor' + showingOptionsFor);");
				this.writeLine("    if (showingOptions != null)");
				this.writeLine("      showingOptions.style.display = 'none';");
				this.writeLine("    showingOptionsFor = null;");
				this.writeLine("  }");
				this.writeLine("  if (refId != null) {");
				this.writeLine("    var toShowOptions = document.getElementById('optionsFor' + refId);");
				this.writeLine("    if (toShowOptions != null)");
				this.writeLine("      toShowOptions.style.display = '';");
				this.writeLine("    showingOptionsFor = refId;");
				this.writeLine("  }");
				this.writeLine("}");
				
				this.writeLine("var deletedRefIDs = null;");
				this.writeLine("var showingDeleted = " + ((canonicalStringId == null) ? "false" : "true") + ";");
				this.writeLine("function toggleDeleted(showDeleted) {");
				this.writeLine("  if (deletedRefIDs == null)");
				this.writeLine("    buildDeletedArray();");
				this.writeLine("  showingDeleted = showDeleted;");
				this.writeLine("  for (var d = 0; d < deletedRefIDs.length; d++) {");
				this.writeLine("    var deletedRef = document.getElementById('ref' + deletedRefIDs[d]);");
				this.writeLine("    if (deletedRef != null)");
				this.writeLine("      deletedRef.style.display = (showDeleted ? '' : 'none');");
				this.writeLine("  }");
				this.writeLine("  document.getElementById('showDeleted').style.display = (showDeleted ? 'none' : '');");
				this.writeLine("  document.getElementById('hideDeleted').style.display = (showDeleted ? '' : 'none');");
				this.writeLine("  return false;");
				this.writeLine("}");
				
				this.writeLine("function setDeleted(refId, deleted) {");
				this.writeLine("  if (!getUser())");
				this.writeLine("    return false;");
				this.writeLine("  var minorUpdateFrame = document.getElementById('minorUpdateFrame');");
				this.writeLine("  if (minorUpdateFrame == null)");
				this.writeLine("    return false;");
				this.writeLine("  var minorUpdateForm = minorUpdateFrame.contentWindow.document.getElementById('minorUpdateForm');");
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
				this.writeLine("  if (deletedRefIDs == null)");
				this.writeLine("    buildDeletedArray();");
				this.writeLine("  minorUpdateForm.submit();");
				this.writeLine("  document.getElementById('delete' + refId).style.display = (deleted ? 'none' : '');");
				this.writeLine("  document.getElementById('unDelete' + refId).style.display = (deleted ? '' : 'none');");
				this.writeLine("  if (!showingDeleted && deleted)");
				this.writeLine("    document.getElementById('ref' + refId).style.display = 'none';");
				this.writeLine("  if (deleted)");
				this.writeLine("    deletedRefIDs[deletedRefIDs.length] = refId;");
				this.writeLine("  else {");
				this.writeLine("    for (var d = 0; d < deletedRefIDs.length; d++) {");
				this.writeLine("      if (deletedRefIDs[d] == refId) {");
				this.writeLine("        deletedRefIDs[d] = '';");
				this.writeLine("        d = deletedRefIDs.length;");
				this.writeLine("      }");
				this.writeLine("    }");
				this.writeLine("  }");
				this.writeLine("  return false;");
				this.writeLine("}");
				
				this.writeLine("function showVersions(canRefId) {");
				this.writeLine("  window.location.href = ('" + this.request.getContextPath() + this.request.getServletPath() + "?" + CANONICAL_STRING_ID_ATTRIBUTE + "=' + canRefId);");
				this.writeLine("}");
				
				this.writeLine("function makeRepresentative(canRefId) {");
				this.writeLine("  if (!getUser())");
				this.writeLine("    return false;");
				this.writeLine("  var minorUpdateFrame = document.getElementById('minorUpdateFrame');");
				this.writeLine("  if (minorUpdateFrame == null)");
				this.writeLine("    return false;");
				this.writeLine("  var minorUpdateForm = minorUpdateFrame.contentWindow.document.getElementById('minorUpdateForm');");
				this.writeLine("  if (minorUpdateForm == null)");
				this.writeLine("    return false;");
				this.writeLine("  var canRefIdField = minorUpdateFrame.contentWindow.document.getElementById('" + CANONICAL_STRING_ID_ATTRIBUTE + "');");
				this.writeLine("  if (canRefIdField == null)");
				this.writeLine("    return false;");
				this.writeLine("  canRefIdField.value = canRefId;");
				this.writeLine("  var userField = minorUpdateFrame.contentWindow.document.getElementById('" + USER_PARAMETER + "');");
				this.writeLine("  if (userField == null)");
				this.writeLine("    return false;");
				this.writeLine("  userField.value = user;");
				this.writeLine("  minorUpdateForm.submit();");
				this.writeLine("  refreshVersionsId = canRefId;");
				this.writeLine("  window.setTimeout('refreshVersions()', 250);");
				this.writeLine("  return false;");
				this.writeLine("}");
				//	TODO consider using layover DIV to block page
				
				this.writeLine("var refreshVersionsId = '';");
				this.writeLine("function refreshVersions() {");
				this.writeLine("  if (refreshVersionsId == '')");
				this.writeLine("    return false;");
				this.writeLine("  var minorUpdateFrame = document.getElementById('minorUpdateFrame');");
				this.writeLine("  if (minorUpdateFrame == null)");
				this.writeLine("    return false;");
				this.writeLine("  var minorUpdateForm = minorUpdateFrame.contentWindow.document.getElementById('minorUpdateForm');");
				this.writeLine("  if (minorUpdateForm == null) {");
				this.writeLine("    window.setTimeout('refreshVersions()', 250);");
				this.writeLine("    return false;");
				this.writeLine("  }");
				this.writeLine("  var canRefIdField = minorUpdateFrame.contentWindow.document.getElementById('" + CANONICAL_STRING_ID_ATTRIBUTE + "');");
				this.writeLine("  if (canRefIdField == null) {");
				this.writeLine("    window.setTimeout('refreshVersions()', 250);");
				this.writeLine("    return false;");
				this.writeLine("  }");
				this.writeLine("  if (canRefIdField.value != '') {");
				this.writeLine("    window.setTimeout('refreshVersions()', 250);");
				this.writeLine("    return false;");
				this.writeLine("  }");
				this.writeLine("  window.location.href = ('" + this.request.getContextPath() + this.request.getServletPath() + "?" + CANONICAL_STRING_ID_ATTRIBUTE + "=' + refreshVersionsId);");
				this.writeLine("  return false;");
				this.writeLine("}");
				
				this.writeLine("</script>");
			}
			
			protected String getPageTitle(String title) {
				return ("RefBank Search" + ((psi == null) ? "" : " Results"));
			}
		};
	}
}
