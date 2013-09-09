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
package de.uka.ipd.idaho.refBank.apps.editor;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.uka.ipd.idaho.easyIO.web.FormDataReceiver;
import de.uka.ipd.idaho.gamta.Annotation;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.TokenSequence;
import de.uka.ipd.idaho.gamta.TokenSequenceUtils;
import de.uka.ipd.idaho.gamta.util.SgmlDocumentReader;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient.PooledString;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient.PooledStringIterator;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils.RefData;
import de.uka.ipd.idaho.refBank.RefBankClient;
import de.uka.ipd.idaho.refBank.apps.RefBankAppServlet;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * Reference string editing facility for RefBank.
 * 
 * @author sautter
 */
public class RefBankEditorServlet extends RefBankAppServlet {
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	get ID
		final String refId = request.getParameter(STRING_ID_ATTRIBUTE);
		if (refId == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid reference ID");
			return;
		}
		
		//	get result URL
		final String resultUrlPrefix = request.getParameter("resultUrlPrefix");
		if (resultUrlPrefix == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid result URL");
			return;
		}
		
		//	get user name
		final String user = request.getParameter(USER_PARAMETER);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid user name");
			return;
		}
		
		//	resolve ID
		final PooledString ref = this.getRefBankClient().getString(refId);
		if (ref == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, ("Invalid reference ID: " + refId));
			return;
		}
		
		//	send edit form
		response.setContentType("text/html");
		response.setCharacterEncoding(ENCODING);
		HtmlPageBuilder pageBuilder = new HtmlPageBuilder(this, request, response) {
			protected void include(String type, String tag) throws IOException {
				if ("includeBody".equals(type))
					this.includeParsedReference();
				else super.include(type, tag);
			}
			private void includeParsedReference() throws IOException {
//				this.writeLine("<form id=\"refEditorForm\" method=\"POST\" action=\"" + this.request.getContextPath() + this.request.getServletPath() + "\" accept-charset=\"" + ENCODING + "\">");
				this.writeLine("<form id=\"refEditorForm\" method=\"POST\" action=\"" + this.request.getContextPath() + this.request.getServletPath() + "\" accept-charset=\"utf8\" encrypt=\"application/x-www-form-urlencoded; charset=utf8\">");
				this.writeLine("<table class=\"editTable\">");
				
				this.writeLine("<input type=\"hidden\" name=\"sourceRefId\" value=\"" + refId + "\" />");
				this.writeLine("<input type=\"hidden\" name=\"resultUrlPrefix\" value=\"" + resultUrlPrefix + "\" />");
				this.writeLine("<input type=\"hidden\" name=\"" + USER_PARAMETER + "\" value=\"" + user + "\" />");
				
				this.writeLine("<tr class=\"editTableRow\">");
				this.writeLine("<td class=\"editTableCell\">");
				this.write("<p class=\"referenceString\">" + xmlGrammar.escape(ref.getStringPlain()) + "</p>");
				this.writeLine("</td>");
				this.writeLine("</tr>");
				
				this.writeLine("<tr class=\"editTableRow\">");
				this.writeLine("<td class=\"editTableCell\">");
				this.writeLine("<textarea name=\"refString\" id=\"refEditorField\">");
				this.writeLine(ref.getStringPlain());
				this.writeLine("</textarea>");
				this.writeLine("</td>");
				this.writeLine("</tr>");
				
				this.writeLine("<tr class=\"resultTableRow\">");
				this.writeLine("<td class=\"resultTableCell\">");
				this.writeLine("<input type=\"button\" id=\"edit\" class=\"referenceFormatLink\" onclick=\"document.getElementById('refEditorForm').submit();\" value=\"Confirm Edit\" />");
				this.writeLine("</td>");
				this.writeLine("</tr>");
				
				this.writeLine("</table>");
				this.writeLine("</form>");
			}
			
			protected String getPageTitle(String title) {
				return "Edit Reference";
			}
		};
		this.sendPopupHtmlPage(pageBuilder);
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		//	use form data receiver to take control of character encoding
		FormDataReceiver data = FormDataReceiver.receive(request, Integer.MAX_VALUE, null, -1, new HashSet(1));
		
		//	get source reference ID
		String sourceRefId = data.getFieldValue("sourceRefId");
		if (sourceRefId == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid source reference ID");
			return;
		}
		
		//	get result URL
		String resultUrlPrefix = data.getFieldValue("resultUrlPrefix");
		if (resultUrlPrefix == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid result URL");
			return;
		}
		
		//	get user name
		String user = data.getFieldValue(USER_PARAMETER);
		if (user == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid user name");
			return;
		}
		
		//	get edited reference
		String refString = new String(data.getFieldByteValue("refString"), ENCODING);
		if (refString == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid reference string");
			return;
		}
		
		//	connect to RefBank
		RefBankClient rbk = this.getRefBankClient();
		
		//	resolve ID
		PooledString sourceRef = rbk.getString(sourceRefId);
		if (sourceRef == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, ("Invalid source reference ID: " + sourceRefId));
			return;
		}
		
		//	verify edited reference against source reference
		String srNoSpace = sourceRef.getStringPlain().replaceAll("\\s+", "");
		String erNoSpace = refString.replaceAll("\\s+", "");
		int avgLength = ((srNoSpace.length() + erNoSpace.length()) / 2);
		int estEditDist = StringUtils.estimateLevenshteinDistance(srNoSpace, erNoSpace);
		if ((estEditDist * 5) > avgLength) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Original and edited reference have to be at least 80% the same");
			return;
		}
		int editDist = StringUtils.getLevenshteinDistance(srNoSpace, erNoSpace, ((avgLength + 4) / 5));
		if ((editDist * 5) > avgLength) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Original and edited reference have to be at least 80% the same");
			return;
		}
		
		//	store edited reference
		PooledString ref = rbk.updateString(refString, user);
		refString = ref.getStringPlain();
		
		//	add parse to edited reference is source reference has one
		if ((sourceRef.getStringParsed() != null) && (ref.getParseChecksum() == null)) try {
			String sourceRefParsedString = sourceRef.getStringParsed();
			MutableAnnotation sourceRefParsed = SgmlDocumentReader.readDocument(new StringReader(sourceRefParsedString));
			MutableAnnotation sourceRefString = Gamta.newDocument(Gamta.newTokenSequence(sourceRef.getStringPlain(), sourceRefParsed.getTokenizer()));
			if (annotateDetails(sourceRefParsed, sourceRefString)) {
				
				//	char-wise Levenshtein transform source into edited reference
				int[] editSequence = StringUtils.getLevenshteinEditSequence(sourceRef.getStringPlain(), refString);
				int sourceRefOffset = 0;
				int refOffset = 0;
				for (int e = 0; e < editSequence.length; e++) {
					if (editSequence[e] == StringUtils.LEVENSHTEIN_KEEP) {
						sourceRefOffset++;
						refOffset++;
					}
					else if (editSequence[e] == StringUtils.LEVENSHTEIN_INSERT) {
						sourceRefString.insertChar(refString.charAt(refOffset), sourceRefOffset);
						sourceRefOffset++;
						refOffset++;
					}
					else if (editSequence[e] == StringUtils.LEVENSHTEIN_DELETE) {
						sourceRefString.removeChar(sourceRefOffset);
					}
					else if (editSequence[e] == StringUtils.LEVENSHTEIN_REPLACE) {
						sourceRefString.setChar(refString.charAt(refOffset), sourceRefOffset);
						sourceRefOffset++;
						refOffset++;
					}
				}
				
				//	generate MODS from transformation result ==> parse for edited reference
				RefData refData = BibRefUtils.genericXmlToRefData(sourceRefString);
				String refParsed = BibRefUtils.toModsXML(refData);
				rbk.updateString(refString, refParsed, user);
			}
		}
		catch (Exception e) {
			System.out.println("Error transforming parsed reference: " + e.getMessage());
			e.printStackTrace(System.out);
		}
		
		//	update canonical string ID to edited reference for all references having source reference in that spot
		rbk.setCanonicalStringId(sourceRefId, ref.id, user);
		PooledStringIterator psit = rbk.getLinkedStrings(sourceRefId);
		while (psit.hasNextString())
			rbk.setCanonicalStringId(psit.getNextString().id, ref.id, user);
		
		//	flag source reference as deleted
		rbk.setDeleted(sourceRefId, true, user);
		
		//	shows new reference in sub window
		response.sendRedirect(resultUrlPrefix + ref.id);
	}
	
	private static boolean annotateDetails(MutableAnnotation sourceRefParsed, MutableAnnotation sourceRefString) {
		RefData sourceRefData = BibRefUtils.modsXmlToRefData(sourceRefParsed);
		ArrayList detailAnnotLists = new ArrayList();
		if (debug) System.out.println("Annotating details:");
		
		//	get detail names
		String[] detailNames = sourceRefData.getAttributeNames();
		
		//	make sure cassified part designators are handles before generic ones
		Arrays.sort(detailNames, new Comparator() {
			public int compare(Object dn1, Object dn2) {
				if (BibRefUtils.PART_DESIGNATOR_ANNOTATION_TYPE.equals(dn1) && partDesignatorTypes.contains(dn2))
					return 1;
				else if (partDesignatorTypes.contains(dn1) && BibRefUtils.PART_DESIGNATOR_ANNOTATION_TYPE.equals(dn2))
					return -1;
				else return 0;
			}
		});
		
		//	annotate all occurences of all attribute values
		for (int d = 0; d < detailNames.length; d++) {
			if (BibRefUtils.PUBLICATION_TYPE_ATTRIBUTE.equals(detailNames[d]) || BibRefUtils.PUBLICATION_IDENTIFIER_ANNOTATION_TYPE.equals(detailNames[d]))
				continue;
			String[] detailValues = sourceRefData.getAttributeValues(detailNames[d]);
			for (int v = 0; v < detailValues.length; v++) {
				if (debug) System.out.println(" - seeking " + detailNames[d] + " '" + detailValues[v] + "'");
				ArrayList detailValueAnnots = new ArrayList(1);
				TokenSequence detailValueTokens = Gamta.newTokenSequence(detailValues[v], sourceRefString.getTokenizer());
				int detailValueStart = -1;
				while ((detailValueStart = TokenSequenceUtils.indexOf(sourceRefString, detailValueTokens, (detailValueStart+1))) != -1) {
					detailValueAnnots.add(Gamta.newAnnotation(sourceRefString, detailNames[d], detailValueStart, detailValueTokens.size()));
					if (debug) System.out.println("   - found at " + detailValueStart);
				}
				if (detailValueAnnots.isEmpty() && (detailValueTokens.size() > 1) && (".,".indexOf(detailValueTokens.lastValue()) != -1)) {
					if (debug) System.out.println("   --> not found, cutting terminal dot or comma");
					detailValues[v] = detailValues[v].substring(0, (detailValues[v].length()-1)).trim();
					v--;
					continue;
				}
				if (detailValueAnnots.isEmpty()) {
					if (debug) System.out.println("   --> not found, match failed");
					return false;
				}
				detailAnnotLists.add(detailValueAnnots);
			}
		}
		
		//	annotate all occurrences of all IDs
		String[] idTypes = sourceRefData.getIdentifierTypes();
		for (int i = 0; i < idTypes.length; i++) {
			String idValue = sourceRefData.getIdentifier(idTypes[i]);
			if (debug) System.out.println(" - seeking " + idTypes[i] + " ID '" + idValue + "'");
			TokenSequence idValueTokens = Gamta.newTokenSequence(idValue, sourceRefString.getTokenizer());
			int idValueStart = -1;
			ArrayList idValueAnnots = new ArrayList(1);
			while ((idValueStart = TokenSequenceUtils.indexOf(sourceRefString, idValueTokens, (idValueStart+1))) != -1) {
				idValueAnnots.add(Gamta.newAnnotation(sourceRefString, idTypes[i], idValueStart, idValueTokens.size()));
				if (debug) System.out.println("   - found at " + idValueStart);
			}
			if (idValueAnnots.isEmpty()) {
				if (debug) System.out.println("   --> not found");
				continue;
			}
			detailAnnotLists.add(idValueAnnots);
		}
		
		//	annotate all details with one occurrence
		for (int d = 0; d < detailAnnotLists.size(); d++) {
			ArrayList detailValueAnnots = ((ArrayList) detailAnnotLists.get(d));
			if (detailValueAnnots.size() != 1)
				continue;
			Annotation detailAnnot = ((Annotation) detailValueAnnots.get(0));
			if (debug) System.out.println(" - annotating " + detailAnnot.getType() + " '" + detailAnnot.getValue() + "'");
			for (int t = detailAnnot.getStartIndex(); t < detailAnnot.getEndIndex(); t++)
				if (sourceRefString.tokenAt(t).hasAttribute(BibRefUtils.TYPE_ATTRIBUTE)) {
					if (BibRefUtils.PART_DESIGNATOR_ANNOTATION_TYPE.equals(detailAnnot.getType()) && partDesignatorTypes.contains(sourceRefString.tokenAt(t).getAttribute(BibRefUtils.TYPE_ATTRIBUTE))) {
						if (debug) System.out.println("   --> skipping generic part designator already assigned to specific one");
						detailAnnot = null;
						break;
					}
					else {
						if (debug) System.out.println("   --> already assigned");
						return false;
					}
				}
			if (detailAnnot == null) {
				detailAnnotLists.remove(d--);
				continue;
			}
			detailAnnot = sourceRefString.addAnnotation(detailAnnot);
			for (int t = detailAnnot.getStartIndex(); t < detailAnnot.getEndIndex(); t++)
				sourceRefString.tokenAt(t).setAttribute(BibRefUtils.TYPE_ATTRIBUTE, detailAnnot.getType());
			detailAnnotLists.remove(d--);
			if (debug) System.out.println("   - annotated");
		}
		
		//	annotate all remaining details soon as only one occurrence left and mark tokens
		int remainingDetailCount;
		do {
			remainingDetailCount = detailAnnotLists.size();
			for (int d = 0; d < detailAnnotLists.size(); d++) {
				ArrayList detailValueAnnots = ((ArrayList) detailAnnotLists.get(d));
				for (int v = 0; v < detailValueAnnots.size(); v++) {
					Annotation detailAnnot = ((Annotation) detailValueAnnots.get(v));
					for (int t = detailAnnot.getStartIndex(); t < detailAnnot.getEndIndex(); t++)
						if (sourceRefString.tokenAt(t).hasAttribute(BibRefUtils.TYPE_ATTRIBUTE)) {
							detailAnnot = null;
							break;
						}
					if (detailAnnot == null)
						detailValueAnnots.remove(v--);
				}
				if (detailValueAnnots.size() == 0)
					return false;
				if (detailAnnotLists.size() != 1)
					continue;
				Annotation detailAnnot = sourceRefString.addAnnotation((Annotation) detailValueAnnots.get(0));
				if (debug) System.out.println(" - annotating " + detailAnnot.getType() + " '" + detailAnnot.getValue() + "'");
				for (int t = detailAnnot.getStartIndex(); t < detailAnnot.getEndIndex(); t++)
					sourceRefString.tokenAt(t).setAttribute(BibRefUtils.TYPE_ATTRIBUTE, detailAnnot.getType());
				detailAnnotLists.remove(d--);
				if (debug) System.out.println("   - annotated");
			}
		}
		
		//	did we newly annotate anything in this round?
		while (detailAnnotLists.size() < remainingDetailCount);
		
		//	remove list of generic part designators
		for (int d = 0; d < detailAnnotLists.size(); d++) {
			ArrayList detailValueAnnots = ((ArrayList) detailAnnotLists.get(d));
			if (BibRefUtils.PART_DESIGNATOR_ANNOTATION_TYPE.equals(((Annotation) detailValueAnnots.get(0)).getType()))
				detailAnnotLists.remove(d--);
		}
		
		//	clean up
		for (int t = 0; t < sourceRefString.size(); t++)
			sourceRefString.tokenAt(t).removeAttribute(BibRefUtils.TYPE_ATTRIBUTE);
		
		//	did we assign everything? (this should work in the very most cases)
		return detailAnnotLists.isEmpty();
		
		//	TODO use greedy overlay technique to deal with details that have equal values, etc
		//		 - use same technique as for generating structures
		//		 - select first to cover all
	}
//	
//	public static void main(String[] args) throws Exception {
//		debug = true;
//		
//		//	get source reference ID
//		String sourceRefId = "ED39FFA7FFA4FFEC2E7F3403FFC5C428";//request.getParameter("sourceRefId");
//		if (sourceRefId == null) {
////			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid source reference ID");
//			System.out.println("Could not get source ref ID");
//			return;
//		}
//		System.out.println("Got source ref");
//		
////		//	get result URL
////		String resultUrlPrefix = request.getParameter("resultUrlPrefix");
////		if (resultUrlPrefix == null) {
//////			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid result URL");
////			return;
////		}
////		
////		//	get user name
////		String user = request.getParameter(USER_PARAMETER);
////		if (user == null) {
//////			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid user name");
////			return;
////		}
////		
//		//	get edited reference
//		String refString = "Pankevicius, R, 2000. Returusiuvabal u steb e jimai Kupiskio ir Anyksci u rajonuose [Monitoring of rare beetles species in Kupiskis and Anyksciai districts]. Raudoni lapai 7: 35 - 36";//request.getParameter("refString");
//		if (refString == null) {
////			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid reference string");
//			System.out.println("Could not get ref string");
//			return;
//		}
//		System.out.println("Got ref string");
//		
//		//	connect to RefBank
////		RefBankClient rbk = this.getRefBankClient();
//		RefBankClient rbk = new RefBankRestClient("http://plazi2.cs.umb.edu/RefBank/rbk");
//		
//		//	resolve ID
//		PooledString sourceRef = rbk.getString(sourceRefId);
//		if (sourceRef == null) {
////			response.sendError(HttpServletResponse.SC_BAD_REQUEST, ("Invalid source reference ID: " + sourceRefId));
//			System.out.println("Could not get source ref");
//			return;
//		}
//		System.out.println("Got source ref: " + sourceRef.getStringPlain());
//		
//		//	verify edited reference against source reference
//		String srNoSpace = sourceRef.getStringPlain().replaceAll("\\s+", "");
//		System.out.println("De-spaced source ref is " + srNoSpace);
//		String erNoSpace = refString.replaceAll("\\s+", "");
//		System.out.println("De-spaced ref is " + erNoSpace);
//		int avgLength = ((srNoSpace.length() + erNoSpace.length()) / 2);
//		System.out.println("Average length is " + avgLength);
//		int estEditDist = StringUtils.estimateLevenshteinDistance(srNoSpace, erNoSpace);
//		System.out.println("Estimated edit distance is " + estEditDist);
//		if ((estEditDist * 5) > avgLength) {
////			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Original and edited reference have to be at least 80% the same");
//			System.out.println("Too different");
//			return;
//		}
//		int editDist = StringUtils.getLevenshteinDistance(srNoSpace, erNoSpace, ((avgLength + 4) / 5));
//		System.out.println("Edit distance is " + editDist);
//		if ((editDist * 5) > avgLength) {
////			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Original and edited reference have to be at least 80% the same");
//			System.out.println("Too different");
//			return;
//		}
//		
////		//	store edited reference
////		PooledString ref = rbk.updateString(refString, user);
////		refString = ref.getStringPlain();
////		
//		//	add parse to edited reference is source reference has one
//		if ((sourceRef.getStringParsed() != null)/* && (ref.getParseChecksum() == null)*/) try {
//			String sourceRefParsedString = sourceRef.getStringParsed();
//			System.out.println("Got parsed source ref: " + sourceRefParsedString);
//			MutableAnnotation sourceRefParsed = SgmlDocumentReader.readDocument(new StringReader(sourceRefParsedString));
//			MutableAnnotation sourceRefString = Gamta.newDocument(Gamta.newTokenSequence(sourceRef.getStringPlain(), sourceRefParsed.getTokenizer()));
//			if (annotateDetails(sourceRefParsed, sourceRefString)) {
//				System.out.println("Got annotated source ref string");
//				
//				//	char-wise Levenshtein transform source into edited reference
//				int[] editSequence = StringUtils.getLevenshteinEditSequence(sourceRef.getStringPlain(), refString);
//				System.out.println("Got edit sequence");
//				int sourceRefOffset = 0;
//				int refOffset = 0;
//				for (int e = 0; e < editSequence.length; e++) {
//					if (editSequence[e] == StringUtils.LEVENSHTEIN_KEEP) {
//						sourceRefOffset++;
//						refOffset++;
//					}
//					else if (editSequence[e] == StringUtils.LEVENSHTEIN_INSERT) {
//						sourceRefString.insertChar(refString.charAt(refOffset), sourceRefOffset);
//						sourceRefOffset++;
//						refOffset++;
//					}
//					else if (editSequence[e] == StringUtils.LEVENSHTEIN_DELETE) {
//						sourceRefString.removeChar(sourceRefOffset);
//					}
//					else if (editSequence[e] == StringUtils.LEVENSHTEIN_REPLACE) {
//						sourceRefString.setChar(refString.charAt(refOffset), sourceRefOffset);
//						sourceRefOffset++;
//						refOffset++;
//					}
//				}
//				System.out.println("Source ref string transformed:");
//				AnnotationUtils.writeXML(sourceRefString, new OutputStreamWriter(System.out));
//				
//				//	generate MODS from transformation result ==> parse for edited reference
//				RefData refData = BibRefUtils.genericXmlToRefData(sourceRefString);
//				String refParsed = BibRefUtils.toModsXML(refData);
//				System.out.println(refParsed);
////				rbk.updateString(refString, refParsed, user);
//			}
//		}
//		catch (Exception e) {
//			System.out.println("Error transforming parsed reference: " + e.getMessage());
//			e.printStackTrace(System.out);
//		}
////		
////		//	update canonical string ID to edited reference for all references having source reference in that spot
////		rbk.setCanonicalStringId(sourceRefId, ref.id, user);
////		PooledStringIterator psit = rbk.getLinkedStrings(sourceRefId);
////		while (psit.hasNextString())
////			rbk.setCanonicalStringId(psit.getNextString().id, ref.id, user);
////		
////		//	flag source reference as deleted
////		rbk.setDeleted(sourceRefId, true, user);
////		
////		//	shows new reference in sub window
////		response.sendRedirect(resultUrlPrefix + ref.id);
//	}
//	
	private static boolean debug = false;
	private static HashSet partDesignatorTypes = new HashSet(4, 0.9f);
	static {
		partDesignatorTypes.add(BibRefUtils.VOLUME_DESIGNATOR_ANNOTATION_TYPE);
		partDesignatorTypes.add(BibRefUtils.ISSUE_DESIGNATOR_ANNOTATION_TYPE);
		partDesignatorTypes.add(BibRefUtils.NUMERO_DESIGNATOR_ANNOTATION_TYPE);
	}
}
