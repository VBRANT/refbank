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
package de.uka.ipd.idaho.refBank.apps.parser;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.uka.ipd.idaho.gamta.Annotation;
import de.uka.ipd.idaho.gamta.AnnotationUtils;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.QueriableAnnotation;
import de.uka.ipd.idaho.gamta.TokenSequence;
import de.uka.ipd.idaho.gamta.TokenSequenceUtils;
import de.uka.ipd.idaho.gamta.util.Analyzer;
import de.uka.ipd.idaho.gamta.util.AnalyzerDataProvider;
import de.uka.ipd.idaho.gamta.util.AnalyzerDataProviderFileBased;
import de.uka.ipd.idaho.gamta.util.AnnotationFilter;
import de.uka.ipd.idaho.gamta.util.SgmlDocumentReader;
import de.uka.ipd.idaho.gamta.util.constants.LiteratureConstants;
import de.uka.ipd.idaho.gamta.util.feedback.html.AsynchronousRequestHandler;
import de.uka.ipd.idaho.gamta.util.feedback.html.AsynchronousRequestHandler.AsynchronousRequest;
import de.uka.ipd.idaho.gamta.util.gPath.GPath;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder.HtmlPageBuilderHost;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient.PooledString;
import de.uka.ipd.idaho.plugins.bibRefs.refParse.RefParse;
import de.uka.ipd.idaho.plugins.bibRefs.refParse.RefParseAutomatic;
import de.uka.ipd.idaho.plugins.bibRefs.refParse.RefParseManual;
import de.uka.ipd.idaho.refBank.apps.RefBankAppServlet;
import de.uka.ipd.idaho.stringUtils.StringIndex;

/**
 * @author sautter
 */
public class RefBankParserServlet extends RefBankAppServlet {
	
	private AsynchronousRequestHandler parseHandler;
	
	private Analyzer refParser;
	private Analyzer refFeedbacker;
	
	private Properties refParseParams = new Properties();
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.goldenGate.webServices.WebServiceFrontendServlet#doInit()
	 */
	protected void doInit() throws ServletException {
		super.doInit();
		
		//	create parse handler
		this.parseHandler = new AsynchronousParsingHandler();
		try {
			this.parseHandler.setFeedbackTimeout(Integer.parseInt(this.getSetting("feedbackTimeout", ("" + this.parseHandler.getFeedbackTimeout()))));
		} catch (RuntimeException re) {}
		
		//	create RefParse analyzers
		AnalyzerDataProvider refParseAdp = new AnalyzerDataProviderFileBased(new File(this.dataFolder, "RefParseData"));
		this.refParser = new RefParseAutomatic();
		this.refParser.setDataProvider(refParseAdp);
		this.refFeedbacker = new RefParseManual();
		this.refFeedbacker.setDataProvider(refParseAdp);
		
		//	TODO centralize RefParse for whole web application
		
		//	create parameters
		this.refParseParams.setProperty(Analyzer.INTERACTIVE_PARAMETER, "true");
		this.refParseParams.setProperty(Analyzer.ONLINE_PARAMETER, "true");
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	check for status request, etc.
		if (this.parseHandler.handleRequest(request, response))
			return;
		
		//	check for invokation
		String user = request.getParameter(USER_PARAMETER);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Reference ID, User Name, or Result URL Missing");
			return;
		}
		String apId = this.parseHandler.createRequest(request, user);
		if (apId == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Reference ID, User Name, or Result URL Missing");
			return;
		}
		
		//	send status page
		this.parseHandler.sendStatusDisplayFrame(request, apId, response);
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	feedback submission
		if (this.parseHandler.handleRequest(request, response))
			return;
		
		//	other post request, send error
		response.sendError(HttpServletResponse.SC_BAD_REQUEST, "POST Not Supported");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.apps.StringPoolAppServlet#exit()
	 */
	protected void exit() {
		super.exit();
		this.parseHandler.shutdown();
	}
	
	private class AsynchronousParsingHandler extends AsynchronousRequestHandler {
		AsynchronousParsingHandler() {
			super(false);
		}
		public AsynchronousRequest buildAsynchronousRequest(HttpServletRequest request) throws IOException {
			
			//	get invokation parameters
			String refId = request.getParameter(STRING_ID_ATTRIBUTE);
			String user = request.getParameter(USER_PARAMETER);
			String resultUrl = request.getParameter("resultUrl");
			if ((refId == null) || (user == null) || (resultUrl == null))
				return null;
			
			//	create parse request
			return new AsynchronousParser(user, refId, resultUrl);
		}
		protected HtmlPageBuilderHost getPageBuilderHost() {
			return RefBankParserServlet.this;
		}
		protected void sendHtmlPage(HtmlPageBuilder hpb) throws IOException {
			RefBankParserServlet.this.sendHtmlPage(hpb);
		}
		protected void sendPopupHtmlPage(HtmlPageBuilder hpb) throws IOException {
			RefBankParserServlet.this.sendPopupHtmlPage(hpb);
		}
		protected void sendStatusDisplayIFramePage(HtmlPageBuilder hpb) throws IOException {
			RefBankParserServlet.this.sendHtmlPage("processing.html", hpb);
		}
		protected void sendFeedbackFormPage(HtmlPageBuilder hpb) throws IOException {
			RefBankParserServlet.this.sendHtmlPage("feedback.html", hpb);
		}
		protected boolean retainAsynchronousRequest(AsynchronousRequest ar, int finishedArCount) {
			/* client not yet notified that parsing is complete, we have to hold
			 * on to this one, unless last status update was more than 5 minutes
			 * ago, which indicates the client side is likely dead */
			if (!ar.isFinishedStatusSent())
				return (System.currentTimeMillis() < (ar.getLastAccessTime() + (1000 * 60 * 5)));
			/* once the client has been notified that parsing is finished, we
			 * don't need this one any longer */
			return false;
		}
	}
	
	private class AsynchronousParser extends AsynchronousRequest {
		String userName;
		String refId;
		String resultUrl;
		PooledString ref;
		AsynchronousParser(String userName, String refId, String resultUrl) {
			super("Parse reference " + refId);
			this.userName = userName;
			this.refId = refId;
			this.resultUrl = resultUrl;
		}
		protected void init() throws Exception {
			
			//	update status
			this.setStatus("Loading reference ...");
			
			//	load reference from RefBank
			this.ref = getRefBankClient().getString(this.refId);
			if (this.ref == null)
				throw new IOException("Invalid Reference ID " + this.refId);
			
			//	update status
			this.setStatus("Reference loaded.");
			this.setPercentFinished(5);
		}
		protected void process() throws Exception {
			
			//	wrap reference string
			MutableAnnotation bibRefDoc = Gamta.newDocument(Gamta.newTokenSequence(this.ref.getStringPlain(), Gamta.INNER_PUNCTUATION_TOKENIZER));
			bibRefDoc.setAttribute(LiteratureConstants.DOCUMENT_ID_ATTRIBUTE, this.refId);
			bibRefDoc.addAnnotation(MutableAnnotation.PARAGRAPH_TYPE, 0, bibRefDoc.size());
			MutableAnnotation bibRef = bibRefDoc.addAnnotation(RefParse.BIBLIOGRAPHIC_REFERENCE_TYPE, 0, bibRefDoc.size());
			Properties storedDetails = new Properties();
			
			//	get parsed reference
			MutableAnnotation modsDoc = null;
			if (this.ref.getStringParsed() != null)	try {
//				System.out.println("GOT PARSED REF: " + this.ref.getStringParsed());
				modsDoc = Gamta.newDocument(Gamta.newTokenSequence("", bibRefDoc.getTokenizer()));
				SgmlDocumentReader.readDocument(new StringReader(this.ref.getStringParsed()), modsDoc);
			} catch (IOException ioe) { /* this is never gonna happen with a StringReader, but Java don't know */ }
			
			//	annotate details from parsed version
			if (modsDoc != null) {
				CountingTokenSequence[] ctss = extractDetails(modsDoc, storedDetails);
//				System.out.println("GOT DETAILS: " + ctss.length);
				
				//	annotate results
				for (int a = 0; a < ctss.length; a++) {
					ctss[a].reset();
					annotate(bibRef, ctss[a]);
					if (ctss[a].remaining() == 0)
						ctss[a] = null;
				}
				
				//	annotate remainders of partial matches
				for (int a = 0; a < ctss.length; a++) {
					if (ctss[a] == null)
						continue;
					System.out.println("UNMATCHED " + ctss[a].type + " '" + ctss[a].toString() + "'");
				}
				
				//	remove consumed token marker
				for (int t = 0; t < bibRef.size(); t++)
					bibRef.tokenAt(t).removeAttribute("C");
				
				//	get reference type
				Annotation[] typeAnnots = modsDoc.getAnnotations("mods:classification");
				String type = ((typeAnnots.length == 0) ? "" : typeAnnots[0].getValue().toLowerCase());
				
				//	set reference type attribute
				if (type.length() != 0)
					bibRef.setAttribute(RefParse.TYPE_ATTRIBUTE, type);
				
				//	handle host item title
				if (type.startsWith("journal")) {
					AnnotationFilter.renameAnnotations(bibRef, "hostTitle", RefParse.JOURNAL_NAME_OR_PUBLISHER_ANNOTATION_TYPE);
					AnnotationFilter.renameAnnotations(bibRef, "hostVolumeTitle", RefParse.VOLUME_TITLE_ANNOTATION_TYPE);
				}
				else {
					AnnotationFilter.renameAnnotations(bibRef, "hostTitle", RefParse.VOLUME_TITLE_ANNOTATION_TYPE);
					AnnotationFilter.renameAnnotations(bibRef, "hostVolumeTitle", RefParse.VOLUME_TITLE_ANNOTATION_TYPE);
				}
				
				//	merge locations to adjacent journalOrPublisher or volumeTitle
				mergeAnnotation(bibRef, "location", RefParse.JOURNAL_NAME_OR_PUBLISHER_ANNOTATION_TYPE, RefParse.JOURNAL_NAME_OR_PUBLISHER_ANNOTATION_TYPE);
				mergeAnnotation(bibRef, "location", RefParse.VOLUME_TITLE_ANNOTATION_TYPE, RefParse.VOLUME_TITLE_ANNOTATION_TYPE);
				
				//	unify pagination
				mergeAnnotation(bibRef, "firstPage", "lastPage", RefParse.PAGINATION_ANNOTATION_TYPE);
				AnnotationFilter.renameAnnotations(bibRef, "firstPage", RefParse.PAGINATION_ANNOTATION_TYPE);
				AnnotationFilter.renameAnnotations(bibRef, "lastPage", RefParse.PAGINATION_ANNOTATION_TYPE);
//				mergeAnnotation(bibRef, "firstPage", "lastPage", RefParse.PAGE_DATA_ANNOTATION_TYPE);
//				AnnotationFilter.renameAnnotations(bibRef, "firstPage", RefParse.PAGE_DATA_ANNOTATION_TYPE);
//				AnnotationFilter.renameAnnotations(bibRef, "lastPage", RefParse.PAGE_DATA_ANNOTATION_TYPE);
			}
			
			//	update status
			this.setStatus("Parsing ...");
			this.setPercentFinished(10);
			
			//	run parser if required
			if (modsDoc == null)
				refParser.process(bibRef, refParseParams);
//			else AnnotationUtils.writeXML(bibRef, new PrintWriter(System.out));
			
			//	update status
			this.setStatus("Getting user input ...");
			this.setPercentFinished(50);
			
			//	get feedback
			refFeedbacker.process(bibRef, refParseParams);
			
			//	update status
			this.setPercentFinished(90);
			this.setStatus("Parsing finished.");
			
			//	generate MODS
			String refParsed = getParsedReference(bibRef, modsDoc, storedDetails);
			if (refParsed == null) // TODO figure out if exception makes sense
				throw new RuntimeException("Incomplete Parse");
			
			//	store reference back to RefBank
			getRefBankClient().updateString(this.ref.getStringPlain(), refParsed, this.userName);
			
			//	update status
			this.setPercentFinished(100);
			this.setStatus("Parsed reference stored.");
		}
		public boolean doImmediateResultForward() {
			return true;
		}
		public String getResultLink(HttpServletRequest request) {
			return this.resultUrl;
		}
		public String getResultLinkLabel() {
			return "Return to Parsed Reference";
		}
		public String getFinishedStatusLabel() {
			return "Parsed finished, returning to reference";
		}
		public String getRunningStatusLabel() {
			return "The reference is being parsed, please wait";
		}
	}
	
	//	TODO remove 'mods:' namespace prefix from paths
	
	private static final GPath titlePath = new GPath("//mods:titleInfo/mods:title");
	
	private static final GPath authorsPath = new GPath("//mods:name[.//mods:roleTerm = 'Author']/mods:namePart");
	
	private static final GPath hostItemPath = new GPath("//mods:relatedItem[./@type = 'host']");
	private static final GPath hostItem_titlePath = new GPath("//mods:titleInfo/mods:title");
	private static final GPath hostItem_volumeNumberPath = new GPath("//mods:part/mods:detail[./@type = 'volume']/mods:number");
	private static final GPath hostItem_volumeTitlePath = new GPath("//mods:part/mods:detail[./@type = 'title']/mods:title");
	private static final GPath hostItem_startPagePath = new GPath("//mods:part/mods:extent[./@unit = 'page']/mods:start");
	private static final GPath hostItem_endPagePath = new GPath("//mods:part/mods:extent[./@unit = 'page']/mods:end");
	private static final GPath hostItem_datePath = new GPath("//mods:part/mods:date");
	private static final GPath hostItem_editorsPath = new GPath("//mods:name[.//mods:roleTerm = 'Editor']/mods:namePart");
	
	private static final GPath originInfoPath = new GPath("//mods:originInfo");
	private static final GPath originInfo_publisherNamePath = new GPath("//mods:publisher");
	private static final GPath originInfo_publisherLocationPath = new GPath("//mods:place/mods:placeTerm");
	private static final GPath originInfo_issueDatePath = new GPath("//mods:dateIssued");
	
	private static LinkedHashMap baseDetailPathsByType = new LinkedHashMap();
	static {
		baseDetailPathsByType.put(RefParse.TITLE_ANNOTATION_TYPE, titlePath);
		baseDetailPathsByType.put(RefParse.AUTHOR_ANNOTATION_TYPE, authorsPath);
	}
	private static LinkedHashMap hostItemDetailPathsByType = new LinkedHashMap();
	static {
		hostItemDetailPathsByType.put(RefParse.PART_DESIGNATOR_ANNOTATION_TYPE, hostItem_volumeNumberPath);
		hostItemDetailPathsByType.put(RefParse.YEAR_ANNOTATION_TYPE, hostItem_datePath);
		hostItemDetailPathsByType.put(RefParse.EDITOR_ANNOTATION_TYPE, hostItem_editorsPath);
		hostItemDetailPathsByType.put("hostTitle", hostItem_titlePath);
		hostItemDetailPathsByType.put("hostVolumeTitle", hostItem_volumeTitlePath);
		hostItemDetailPathsByType.put("firstPage", hostItem_startPagePath);
		hostItemDetailPathsByType.put("lastPage", hostItem_endPagePath);
	}
	private static LinkedHashMap originInfoDetailPathsByType = new LinkedHashMap();
	static {
		originInfoDetailPathsByType.put(RefParse.YEAR_ANNOTATION_TYPE, originInfo_issueDatePath);
		originInfoDetailPathsByType.put(RefParse.JOURNAL_NAME_OR_PUBLISHER_ANNOTATION_TYPE, originInfo_publisherNamePath);
		originInfoDetailPathsByType.put("location", originInfo_publisherLocationPath);
	}
	
	private static class CountingTokenSequence {
		String type;
		private String plain;
		private StringIndex counts = new StringIndex(true);
		private StringIndex rCounts = new StringIndex(true);
		private ArrayList tokens = new ArrayList();
		private LinkedList rTokens = new LinkedList();
		public CountingTokenSequence(String type, TokenSequence tokens) {
			this.type = type;
			this.plain = TokenSequenceUtils.concatTokens(tokens, true, true);
			for (int t = 0; t < tokens.size(); t++) {
				String token = tokens.valueAt(t);
//				if (!Gamta.isPunctuation(token)) {
					this.counts.add(token);
					this.tokens.add(token);
//				}
			}
		}
		public String toString() {
			return this.plain;
		}
//		public boolean contains(String token) {
//			return (this.rCounts.getCount(token) < this.counts.getCount(token));
//		}
		public boolean remove(String token) {
			if (this.rCounts.getCount(token) < this.counts.getCount(token)) {
				this.rCounts.add(token);
				this.rTokens.addLast(token);
				return true;
			}
			else return false;
		}
//		public int matched() {
//			return this.rCounts.size();
//		}
		public int remaining() {
			return (this.counts.size() - this.rCounts.size());
		}
		public String next() {
			return ((this.rTokens.size() < this.tokens.size()) ? ((String) this.tokens.get(this.rTokens.size())) : null);
		}
		public void reset() {
			this.rCounts.clear();
			this.rTokens.clear();
		}
	}
	
	private CountingTokenSequence[] extractDetails(MutableAnnotation modsDoc, Properties storedDetails) {
		
		//	TODO remove 'mods:' namespace prefix where given
		
		ArrayList ctss = new ArrayList();
		this.extractDetails(modsDoc, RefParse.AUTHOR_ANNOTATION_TYPE, authorsPath, ctss, storedDetails);
		QueriableAnnotation[] originInfo = originInfoPath.evaluate(modsDoc, null);
		if (originInfo.length != 0)
			this.extractDetails(originInfo[0], originInfoDetailPathsByType, ctss, storedDetails);
		QueriableAnnotation[] hostItem = hostItemPath.evaluate(modsDoc, null);
		if (hostItem.length != 0)
			this.extractDetails(hostItem[0], hostItemDetailPathsByType, ctss, null);
		this.extractDetails(modsDoc, RefParse.TITLE_ANNOTATION_TYPE, titlePath, ctss, storedDetails);
		return ((CountingTokenSequence[]) ctss.toArray(new CountingTokenSequence[ctss.size()]));
	}
	private void extractDetails(QueriableAnnotation data, HashMap detailPathsByType, ArrayList ctss, Properties storedDetails) {
		for (Iterator tit = detailPathsByType.keySet().iterator(); tit.hasNext();) {
			String type = ((String) tit.next());
			this.extractDetails(data, type, ((GPath) detailPathsByType.get(type)), ctss, storedDetails);
		}
	}
	private void extractDetails(QueriableAnnotation data, String type, GPath path, ArrayList ctss, Properties storedDetails) {
		Annotation[] details = path.evaluate(data, null);
		for (int d = 0; d < details.length; d++) {
			ctss.add(new CountingTokenSequence(type, details[d]));
			System.out.println("GOT DETAIL " + type + ": '" + TokenSequenceUtils.concatTokens(details[d], true, true) + "'");
			if ((storedDetails != null) && !storedDetails.containsKey(type))
				storedDetails.setProperty(type, TokenSequenceUtils.concatTokens(details[d], true, true));
		}
	}
	
	private void annotate(MutableAnnotation bibRef, CountingTokenSequence cts) {
		System.out.println("Matching " + cts.type + " '" + cts.toString() + "'");
		
		//	try full sequential match
		for (int t = 0; t < bibRef.size(); t++) {
			if (bibRef.tokenAt(t).hasAttribute("C"))
				continue;
			String token = bibRef.valueAt(t);
			if (!token.equals(cts.next()))
				continue;
			
			//	got anchor, attempt match
			cts.remove(token);
			System.out.println(" - found sequence anchor '" + token + "', " + cts.remaining() + " tokens remaining");
			
			//	found end of one-sized sequence, match successful
			if (cts.remaining() == 0) {
				Annotation a = bibRef.addAnnotation(cts.type, t, 1);
				a.firstToken().setAttribute("C", "C");
				System.out.println("   ==> single-token match: " + a.toXML());
				return;
			}
			
			//	continue matching
			for (int l = (t+1); l < bibRef.size(); l++) {
				token = bibRef.valueAt(l);
				
				//	next token continues match
				if (token.equals(cts.next())) {
					cts.remove(token);
					System.out.println("   - found continuation '" + token + "', " + cts.remaining() + " tokens remaining");
					
					//	found end of sequence, match successful
					if (cts.remaining() == 0) {
						Annotation a = bibRef.addAnnotation(cts.type, t, (l-t+1));
						for (int c = 0; c < a.size(); c++)
							a.tokenAt(c).setAttribute("C", "C");
						System.out.println("   ==> sequence match: " + a.toXML());
						return;
					}
				}
				
				//	next token is punctuation, ignore it
				else if (Gamta.isPunctuation(token)) {
					System.out.println("   - ignoring punctuation '" + token + "'");
					continue;
				}
				
				//	next token does not match, reset matcher and start over
				else {
					System.out.println("   ==> cannot continue with '" + token + "'");
					cts.reset();
					break;
				}
			}
		}
	}
	
	private void mergeAnnotation(MutableAnnotation bibRef, String mType1, String mType2, String type) {
		Annotation[] mAnnots1 = bibRef.getAnnotations(mType1);
		if (mAnnots1.length == 0)
			return;
		Annotation[] mAnnots2 = bibRef.getAnnotations(mType2);
		if (mAnnots2.length == 0)
			return;
		for (int ma1 = 0; ma1 < mAnnots1.length; ma1++) {
			if (mAnnots1[ma1] == null)
				continue;
			for (int ma2 = 0; ma2 < mAnnots2.length; ma2++) {
				if (mAnnots2[ma2] == null)
					continue;
				if (mAnnots2[ma2].getEndIndex() <= mAnnots1[ma1].getStartIndex()) {
					boolean canMerge = true;
					for (int t = mAnnots2[ma2].getEndIndex(); t < mAnnots1[ma1].getStartIndex(); t++)
						if (!Gamta.isPunctuation(bibRef.valueAt(t))) {
							canMerge = false;
							break;
						}
					if (canMerge) {
						Annotation merged = bibRef.addAnnotation(type, mAnnots2[ma2].getStartIndex(), (mAnnots1[ma1].getEndIndex() - mAnnots2[ma2].getStartIndex()));
						bibRef.removeAnnotation(mAnnots2[ma2]);
						bibRef.removeAnnotation(mAnnots1[ma1]);
						mAnnots1[ma1] = (mType1.equals(type) ? merged : null);
						mAnnots2[ma2] = (mType2.equals(type) ? merged : null);
						break;
					}
				}
				else if (mAnnots1[ma1].getEndIndex() <= mAnnots2[ma2].getStartIndex()) {
					boolean canMerge = true;
					for (int t = mAnnots1[ma1].getEndIndex(); t < mAnnots2[ma2].getStartIndex(); t++)
						if (!Gamta.isPunctuation(bibRef.valueAt(t))) {
							canMerge = false;
							break;
						}
					if (canMerge) {
						Annotation merged = bibRef.addAnnotation(type, mAnnots1[ma1].getStartIndex(), (mAnnots2[ma2].getEndIndex() - mAnnots1[ma1].getStartIndex()));
						bibRef.removeAnnotation(mAnnots2[ma2]);
						bibRef.removeAnnotation(mAnnots1[ma1]);
						mAnnots1[ma1] = (mType1.equals(type) ? merged : null);
						mAnnots2[ma2] = (mType2.equals(type) ? merged : null);
						break;
					}
				}
			}
		}
	}
	
	private String getParsedReference(MutableAnnotation bibRef, MutableAnnotation existingParsedReference, Properties storedDetails) {
//		TODO switch back to using BibRefUtils
//		
//		RefData rd = BibRefUtils.genericXmlToRefData(bibRef);
//		if (!rd.hasAttribute(BibRefUtils.AUTHOR_ANNOTATION_TYPE)) {
//			String author = ((String) bibRef.getAttribute(RefParse.AUTHOR_ANNOTATION_TYPE));
//			if (author == null)
//				return null;
//			rd.setAttribute(BibRefUtils.AUTHOR_ANNOTATION_TYPE, author);
//		}
//		
//		String type = ((String) rd.getAttribute(RefParse.TYPE_ATTRIBUTE));
//		if (type == null)
//			BibRefUtils.classify(rd);
//		if (type == null)
//			return null;
//		
//		if (existingParsedReference != null) {
//			Annotation[] ids = existingParsedReference.getAnnotations("mods:identifier");
//			for (int i = 0; i < ids.length; i++) {
//				String idType = ((String) ids[i].getAttribute("type"));
//				if (!"RefBankID".equals(idType)) // have this one set by server
//					rd.setIdentifier(idType, TokenSequenceUtils.concatTokens(ids[i], false, true));
//			}
//			
//			MutableAnnotation[] locations = existingParsedReference.getMutableAnnotations("mods:location");
//			for (int l = 0; l < locations.length; l++) {
//				Annotation[] url = locations[l].getAnnotations("mods:url");
//				if (url.length == 1)
//					rd.addAttribute(BibRefUtils.PUBLICATION_URL_ANNOTATION_TYPE, TokenSequenceUtils.concatTokens(url[0], false, true));
//			}
//		}
//		
//		return BibRefUtils.toModsXML(rd);
		
		StringBuffer bibRefParsed = new StringBuffer();
		String type = ((String) bibRef.getAttribute(RefParse.TYPE_ATTRIBUTE));
		if (type == null)
			return null;
		
		bibRefParsed.append("<mods:mods xmlns:mods=\"http://www.loc.gov/mods/v3\">");
		
		Annotation[] title = bibRef.getAnnotations(RefParse.TITLE_ANNOTATION_TYPE);
		if (title.length != 1)
			return null;
		bibRefParsed.append("<mods:titleInfo>");
		bibRefParsed.append("<mods:title>" + AnnotationUtils.escapeForXml(TokenSequenceUtils.concatTokens(title[0], true, true)) + "</mods:title>");
		bibRefParsed.append("</mods:titleInfo>");
		
		Annotation[] authors = bibRef.getAnnotations(RefParse.AUTHOR_ANNOTATION_TYPE);
		if (authors.length == 0) {
			String author = ((String) bibRef.getAttribute(RefParse.AUTHOR_ANNOTATION_TYPE));
			if (author == null)
				return null;
			bibRefParsed.append("<mods:name type=\"personal\">");
			bibRefParsed.append("<mods:role>");
			bibRefParsed.append("<mods:roleTerm>Author</mods:roleTerm>");
			bibRefParsed.append("</mods:role>");
			bibRefParsed.append("<mods:namePart>" + AnnotationUtils.escapeForXml(author) + "</mods:namePart>");
			bibRefParsed.append("</mods:name>");
		}
		else for (int a = 0; a < authors.length; a++) {
			bibRefParsed.append("<mods:name type=\"personal\">");
			bibRefParsed.append("<mods:role>");
			bibRefParsed.append("<mods:roleTerm>Author</mods:roleTerm>");
			bibRefParsed.append("</mods:role>");
			bibRefParsed.append("<mods:namePart>" + AnnotationUtils.escapeForXml(TokenSequenceUtils.concatTokens(authors[a], true, true)) + "</mods:namePart>");
			bibRefParsed.append("</mods:name>");
		}
		
		bibRefParsed.append("<mods:typeOfResource>text</mods:typeOfResource>");
		
//		Annotation[] pageData = bibRef.getAnnotations(RefParse.PAGE_DATA_ANNOTATION_TYPE);
		Annotation[] pageData = bibRef.getAnnotations(RefParse.PAGINATION_ANNOTATION_TYPE);
		if (pageData.length == 1) {
			bibRefParsed.append("<mods:relatedItem type=\"host\">");
			if (type.toLowerCase().indexOf("journal") != -1) {
				Annotation[] journalName = bibRef.getAnnotations(RefParse.JOURNAL_NAME_OR_PUBLISHER_ANNOTATION_TYPE);
				if (journalName.length != 1)
					return null;
				bibRefParsed.append("<mods:titleInfo>");
				bibRefParsed.append("<mods:title>" + AnnotationUtils.escapeForXml(TokenSequenceUtils.concatTokens(journalName[0], true, true)) + "</mods:title>");
				bibRefParsed.append("</mods:titleInfo>");
			}
			else if (type.toLowerCase().indexOf("book") != -1) {
				Annotation[] proceedingsVolumeTitle = bibRef.getAnnotations(RefParse.VOLUME_TITLE_ANNOTATION_TYPE);
				if (proceedingsVolumeTitle.length != 1)
					return null;
				bibRefParsed.append("<mods:titleInfo>");
				bibRefParsed.append("<mods:title>" + AnnotationUtils.escapeForXml(TokenSequenceUtils.concatTokens(proceedingsVolumeTitle[0], true, true)) + "</mods:title>");
				bibRefParsed.append("</mods:titleInfo>");
				if (!this.addOriginInfo(bibRef, bibRefParsed, storedDetails))
					return null;
			}
			
			Annotation[] proceedingsVolumeTitle = bibRef.getAnnotations(RefParse.VOLUME_TITLE_ANNOTATION_TYPE);
			if (proceedingsVolumeTitle.length == 1) {
				Annotation[] editors = bibRef.getAnnotations(RefParse.EDITOR_ANNOTATION_TYPE);
				for (int e = 0; e < editors.length; e++) {
					bibRefParsed.append("<mods:name type=\"personal\">");
					bibRefParsed.append("<mods:role>");
					bibRefParsed.append("<mods:roleTerm>Editor</mods:roleTerm>");
					bibRefParsed.append("</mods:role>");
					bibRefParsed.append("<mods:namePart>" + AnnotationUtils.escapeForXml(TokenSequenceUtils.concatTokens(editors[e], true, true)) + "</mods:namePart>");
					bibRefParsed.append("</mods:name>");
				}
			}
			
			bibRefParsed.append("<mods:part>");
			
			if (type.toLowerCase().indexOf("journal") != -1) {
				Annotation[] volumeNumber = bibRef.getAnnotations(RefParse.PART_DESIGNATOR_ANNOTATION_TYPE);
				if (volumeNumber.length == 0)
					return null;
				bibRefParsed.append("<mods:detail type=\"volume\">");
				bibRefParsed.append("<mods:number>" + AnnotationUtils.escapeForXml(TokenSequenceUtils.concatTokens(volumeNumber[0], true, true)) + "</mods:number>");
				bibRefParsed.append("</mods:detail>");
				if (proceedingsVolumeTitle.length == 1) {
					bibRefParsed.append("<mods:detail type=\"title\">");
					bibRefParsed.append("<mods:title>" + AnnotationUtils.escapeForXml(TokenSequenceUtils.concatTokens(proceedingsVolumeTitle[0], true, true)) + "</mods:title>");
					bibRefParsed.append("</mods:detail>");
				}
				Annotation[] year = bibRef.getAnnotations(RefParse.YEAR_ANNOTATION_TYPE);
				if (year.length != 1)
					return null;
				bibRefParsed.append("<mods:date>" + AnnotationUtils.escapeForXml(TokenSequenceUtils.concatTokens(year[0], true, true)) + "</mods:date>");
			}
			bibRefParsed.append("<mods:extent unit=\"page\">");
			bibRefParsed.append("<mods:start>" + pageData[0].firstValue() + "</mods:start>");
			bibRefParsed.append("<mods:end>" + pageData[0].lastValue() + "</mods:end>");
			bibRefParsed.append("</mods:extent>");
			
			bibRefParsed.append("</mods:part>");
			
			bibRefParsed.append("</mods:relatedItem>");
		}
		else if (type.toLowerCase().indexOf("journal") != -1) {
			bibRefParsed.append("<mods:relatedItem type=\"host\">");
			Annotation[] journalName = bibRef.getAnnotations(RefParse.JOURNAL_NAME_OR_PUBLISHER_ANNOTATION_TYPE);
			if (journalName.length != 1)
				return null;
			bibRefParsed.append("<mods:titleInfo>");
			bibRefParsed.append("<mods:title>" + AnnotationUtils.escapeForXml(TokenSequenceUtils.concatTokens(journalName[0], true, true)) + "</mods:title>");
			bibRefParsed.append("</mods:titleInfo>");
			bibRefParsed.append("<mods:part>");
			Annotation[] volumeNumber = bibRef.getAnnotations(RefParse.PART_DESIGNATOR_ANNOTATION_TYPE);
			if (volumeNumber.length == 0)
				return null;
			bibRefParsed.append("<mods:detail type=\"volume\">");
			bibRefParsed.append("<mods:number>" + AnnotationUtils.escapeForXml(TokenSequenceUtils.concatTokens(volumeNumber[0], true, true)) + "</mods:number>");
			bibRefParsed.append("</mods:detail>");
			Annotation[] year = bibRef.getAnnotations(RefParse.YEAR_ANNOTATION_TYPE);
			if (year.length != 1)
				return null;
			bibRefParsed.append("<mods:date>" + AnnotationUtils.escapeForXml(TokenSequenceUtils.concatTokens(year[0], true, true)) + "</mods:date>");
			bibRefParsed.append("</mods:part>");
			bibRefParsed.append("</mods:relatedItem>");
		}
		else if (type.toLowerCase().indexOf("book") != -1) {
			if (!this.addOriginInfo(bibRef, bibRefParsed, storedDetails))
				return null;
		}
		
		if (existingParsedReference != null) {
			Annotation[] ids = existingParsedReference.getAnnotations("mods:identifier");
			for (int i = 0; i < ids.length; i++) {
				if ("RefBankID".equals(ids[i].getAttribute("type"))) // have this one set by server
					continue;
				bibRefParsed.append(AnnotationUtils.produceStartTag(ids[i]));
				bibRefParsed.append(AnnotationUtils.escapeForXml(TokenSequenceUtils.concatTokens(ids[i], false, true)));
				bibRefParsed.append(AnnotationUtils.produceEndTag(ids[i]));
			}
			
			MutableAnnotation[] locations = existingParsedReference.getMutableAnnotations("mods:location");
			for (int l = 0; l < locations.length; l++) {
				Annotation[] url = locations[l].getAnnotations("mods:url");
				if (url.length != 1)
					continue;
				bibRefParsed.append(AnnotationUtils.produceStartTag(locations[l]));
				bibRefParsed.append(AnnotationUtils.produceStartTag(url[0]));
				bibRefParsed.append(AnnotationUtils.escapeForXml(TokenSequenceUtils.concatTokens(url[0], false, true)));
				bibRefParsed.append(AnnotationUtils.produceEndTag(url[0]));
				bibRefParsed.append(AnnotationUtils.produceEndTag(locations[l]));
			}
		}
		
		bibRefParsed.append("<mods:classification>" + AnnotationUtils.escapeForXml(type.toLowerCase()) + "</mods:classification>");
		
		bibRefParsed.append("</mods:mods>");
		
		return bibRefParsed.toString();
	}
	
	private final boolean addOriginInfo(MutableAnnotation bibRef, StringBuffer bibRefParsed, Properties storedDetails) {
		bibRefParsed.append("<mods:originInfo>");
		Annotation[] year = bibRef.getAnnotations(RefParse.YEAR_ANNOTATION_TYPE);
		if (year.length != 1)
			return false;
		bibRefParsed.append("<mods:dateIssued>" + AnnotationUtils.escapeForXml(TokenSequenceUtils.concatTokens(year[0], true, true)) + "</mods:dateIssued>");
		String publisherName = storedDetails.getProperty(RefParse.JOURNAL_NAME_OR_PUBLISHER_ANNOTATION_TYPE);
		String publisherLocation = storedDetails.getProperty("location");
		if ((publisherName == null) && (publisherLocation == null)) {
			Annotation[] publisherAnnot = bibRef.getAnnotations(RefParse.JOURNAL_NAME_OR_PUBLISHER_ANNOTATION_TYPE);
			if (publisherAnnot.length != 1)
				return false;
			String publisher = TokenSequenceUtils.concatTokens(publisherAnnot[0], true, true);
			int split;
			split = publisher.indexOf(':');
			if (split == -1)
				split = publisher.indexOf(',');
			if (split == -1)
				return false;
			if (publisher.charAt(split) == ':') {
				publisherName = publisher.substring(split+1).trim();
				publisherLocation = publisher.substring(0, split).trim();
			}
			else {
				publisherName = publisher.substring(0, split).trim();
				publisherLocation = publisher.substring(split+1).trim();
			}
		}
		else if ((publisherName == null) || (publisherLocation == null)) {
			Annotation[] publisherAnnot = bibRef.getAnnotations(RefParse.JOURNAL_NAME_OR_PUBLISHER_ANNOTATION_TYPE);
			if (publisherAnnot.length != 1)
				return false;
			String publisher = TokenSequenceUtils.concatTokens(publisherAnnot[0], true, true);
			if (publisherName == null)
				publisherName = getRemainder(publisher, publisherLocation);
			else if (publisherLocation == null)
				publisherLocation = getRemainder(publisher, publisherName);
		}
		if ((publisherName == null) || (publisherLocation == null))
			return false;
		bibRefParsed.append("<mods:publisher>" + AnnotationUtils.escapeForXml(publisherName) + "</mods:publisher>");
		bibRefParsed.append("<mods:place>");
		bibRefParsed.append("<mods:placeTerm>" + AnnotationUtils.escapeForXml(publisherLocation) + "</mods:placeTerm>");
		bibRefParsed.append("</mods:place>");
		bibRefParsed.append("</mods:originInfo>");
		return true;
	}
	
	private static String getRemainder(String data, String part) {
		int split = data.indexOf(part);
		if (split == -1)
			return null;
		String before = data.substring(0, split);
		while ((before.length() > 0) && Gamta.isPunctuation(before.substring(before.length()-1)))
			before = before.substring(0, (before.length()-1)).trim();
		String after = data.substring(split + part.length());
		while ((after.length() > 0) && Gamta.isPunctuation(after.substring(0, 1)))
			after = after.substring(1).trim();
		if (before.length() < after.length())
			return before;
		else return after;
	}
}