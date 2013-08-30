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
package de.uka.ipd.idaho.refBank;

import java.io.File;
import java.util.Iterator;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import de.uka.ipd.idaho.easyIO.sql.TableDefinition;
import de.uka.ipd.idaho.gamta.Annotation;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.TokenSequenceUtils;
import de.uka.ipd.idaho.onn.stringPool.StringPoolServlet;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefTypeSystem;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils.RefData;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * @author sautter
 *
 */
public class RefBankServlet extends StringPoolServlet implements RefBankClient, RefBankConstants {
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#getExternalDataName()
	 */
	protected String getExternalDataName() {
		return "BibRef";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#getNamespaceAttribute()
	 */
	public String getNamespaceAttribute() {
		return RBK_XML_NAMESPACE_ATTRIBUTE;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#getStringNodeType()
	 */
	public String getStringNodeType() {
		return REF_NODE_TYPE;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#getStringParsedNodeType()
	 */
	public String getStringParsedNodeType() {
		return REF_PARSED_NODE_TYPE;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#getStringPlainNodeType()
	 */
	public String getStringPlainNodeType() {
		return REF_PLAIN_NODE_TYPE;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#getStringSetNodeType()
	 */
	public String getStringSetNodeType() {
		return REF_SET_NODE_TYPE;
	}
	
	private BibRefTypeSystem refTypeSystem = null;
	
	private static final String AUTHOR_COLUMN_NAME = "DocAuthor";
	private static final int AUTHOR_COLUMN_LENGTH = 256;
	private static final String DATE_COLUMN_NAME = "DocDate";
	private static final int DATE_COLUMN_LENGTH = 16;
	private static final String TITLE_COLUMN_NAME = "DocTitle";
	private static final int TITLE_COLUMN_LENGTH = 496;
	private static final String ORIGIN_COLUMN_NAME = "DocOrigin";
	private static final int ORIGIN_COLUMN_LENGTH = 256;
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#doInit()
	 */
	protected void doInit() throws ServletException {
		super.doInit();
		
		//	get type system
		String refTypeSystemPath = this.getSetting("refTypeSystemPath");
		this.refTypeSystem = ((refTypeSystemPath == null) ? BibRefTypeSystem.getDefaultInstance() : BibRefTypeSystem.getInstance(new File(this.webInfFolder, refTypeSystemPath), true));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#extendIndexTableDefinition(de.uka.ipd.idaho.easyIO.sql.TableDefinition)
	 */
	protected boolean extendIndexTableDefinition(TableDefinition itd) {
		itd.addColumn(AUTHOR_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, AUTHOR_COLUMN_LENGTH);
		itd.addColumn(DATE_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, DATE_COLUMN_LENGTH);
		itd.addColumn(TITLE_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, TITLE_COLUMN_LENGTH);
		itd.addColumn(ORIGIN_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, ORIGIN_COLUMN_LENGTH);
		return true;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#addIndexPredicates(javax.servlet.http.HttpServletRequest, java.util.Properties)
	 */
	protected void addIndexPredicates(HttpServletRequest request, Properties detailPredicates) {
		String authorQueryPredicate = request.getParameter(AUTHOR_PARAMETER);
		if (authorQueryPredicate != null)
			detailPredicates.setProperty(AUTHOR_COLUMN_NAME, authorQueryPredicate);
		String titleQueryPredicate = request.getParameter(TITLE_PARAMETER);
		if (titleQueryPredicate != null)
			detailPredicates.setProperty(TITLE_COLUMN_NAME, titleQueryPredicate);
		String dateQueryPredicate = request.getParameter(DATE_PARAMETER);
		if (dateQueryPredicate != null)
			detailPredicates.setProperty(DATE_COLUMN_NAME, dateQueryPredicate);
		String originQueryPredicate = request.getParameter(ORIGIN_PARAMETER);
		if (originQueryPredicate != null)
			detailPredicates.setProperty(ORIGIN_COLUMN_NAME, originQueryPredicate);
	}

	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#extendIndexData(de.uka.ipd.idaho.onn.stringPool.StringPoolServlet.ParsedStringIndexData, de.uka.ipd.idaho.gamta.MutableAnnotation)
	 */
	protected void extendIndexData(ParsedStringIndexData indexData, MutableAnnotation stringParsed) {
		
		//	get index data
		RefIndexData refIndexData = this.getIndexData(stringParsed);
		
		//	add attributes
		indexData.addIndexAttribute(AUTHOR_COLUMN_NAME, refIndexData.author.toLowerCase());
		indexData.addIndexAttribute(DATE_COLUMN_NAME, refIndexData.date.toLowerCase());
		indexData.addIndexAttribute(TITLE_COLUMN_NAME, refIndexData.title.toLowerCase());
		indexData.addIndexAttribute(ORIGIN_COLUMN_NAME, refIndexData.origin.toLowerCase());
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#extendIdentifierData(de.uka.ipd.idaho.onn.stringPool.StringPoolServlet.ParsedStringIdentifierData, de.uka.ipd.idaho.gamta.MutableAnnotation)
	 */
	protected void extendIdentifierData(ParsedStringIdentifierData identifierData, MutableAnnotation stringParsed) {
		Annotation[] ids = stringParsed.getAnnotations("mods:identifier");
		for (int i = 0; i < ids.length; i++) {
			String type = ((String) ids[i].getAttribute("type"));
			if ((type == null) || "RefBankID".equalsIgnoreCase(type))
				continue;
			String id = TokenSequenceUtils.concatTokens(ids[i], true, true).replaceAll("\\s", "");
			identifierData.addIdentifier(type, id);
		}
	}
	
	private static class RefIndexData {
		final String author;
		final String title;
		final String date;
		final String origin;
		RefIndexData(String author, String title, String date, String origin) {
			this.author = author;
			this.title = title;
			this.date = date;
			this.origin = origin;
		}
	}
	
	private RefIndexData getIndexData(MutableAnnotation stringParsed) {
		
		//	unify redord
		RefData ref = BibRefUtils.modsXmlToRefData(stringParsed);
		
		//	what do we want to index?
		StringBuffer authorString = new StringBuffer();
		String[] authors = ref.getAttributeValues(BibRefUtils.AUTHOR_ANNOTATION_TYPE);
		if (authors != null)
			for (int a = 0; a < authors.length; a++) {
				if (a != 0) {
					if ((a + 1) == authors.length)
						authorString.append(" & ");
					else authorString.append(", ");
				}
				authorString.append(authors[a]);
			}
		String author = authorString.toString();
		String date = ref.getAttribute(BibRefUtils.YEAR_ANNOTATION_TYPE);
		if (date == null)
			date = "";
		String title = ref.getAttribute(BibRefUtils.TITLE_ANNOTATION_TYPE);
		if (title == null)
			title = "";
		String origin = this.refTypeSystem.getOrigin(ref);
		if (origin == null)
			origin = "";
		
		//	trim data
		if (author.length() > AUTHOR_COLUMN_LENGTH)
			author = author.substring(0, AUTHOR_COLUMN_LENGTH);
		if (date.length() > DATE_COLUMN_LENGTH)
			date = date.substring(0, DATE_COLUMN_LENGTH);
		if (title.length() > TITLE_COLUMN_LENGTH)
			title = title.substring(0, TITLE_COLUMN_LENGTH);
		if (origin.length() > ORIGIN_COLUMN_LENGTH)
			origin = origin.substring(0, ORIGIN_COLUMN_LENGTH);
		
		//	finally ...
		return new RefIndexData(author, title, date, origin);
	}

	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#checkParsedString(java.lang.String, java.lang.String, de.uka.ipd.idaho.gamta.MutableAnnotation)
	 */
	protected String checkParsedString(String stringId, String stringPlain, MutableAnnotation stringParsed) {
		String parseError = null;
		
		StringVector extraTokens = new StringVector();
		
		Annotation[] idAnnots = stringParsed.getAnnotations("mods:identifier");
		boolean refBankId = false;
		for (int i = 0; (i < idAnnots.length) && (parseError == null); i++) {
			addTokens(extraTokens, idAnnots[i]);
			if ("RefBankID".equalsIgnoreCase((String) idAnnots[i].getAttribute("type"))) {
				if (refBankId)
					parseError = "Duplicate identifier in parsed string.";
				else {
					String id = TokenSequenceUtils.concatTokens(idAnnots[i], false, true);
					if (stringId.equals(id))
						refBankId = true;
					else parseError = ("Invalid identifier in parsed string: " + id + " does not match " + stringId);
				}
			}
		}
		
		if (!refBankId && (parseError == null)) {
			int refBankIdStart = stringParsed.size();
			stringParsed.addChars(" " + stringId);
			Annotation refBankIdAnnot = stringParsed.addAnnotation("mods:identifier", refBankIdStart, (stringParsed.size() - refBankIdStart));
			refBankIdAnnot.setAttribute("type", "RefBankID");
			addTokens(extraTokens, refBankIdAnnot);
			Annotation[] modsAnnots = stringParsed.getAnnotations("mods:mods");
			if (modsAnnots.length == 0)
				stringParsed.addAnnotation("mods:mods", 0, stringParsed.size());
			else if (modsAnnots[0].size() < stringParsed.size()) {
				stringParsed.removeAnnotation(modsAnnots[0]);
				stringParsed.addAnnotation("mods:mods", 0, stringParsed.size());
			}
		}
		
		Annotation[] typeAnnots = stringParsed.getAnnotations("mods:classification");
		String type = ((typeAnnots.length == 0) ? null : typeAnnots[0].getValue());
		
		if (parseError == null) {
			for (int t = 0; t < typeAnnots.length; t++)
				addTokens(extraTokens, typeAnnots[t]);
			Annotation[] urlAnnots = stringParsed.getAnnotations("mods:url");
			for (int u = 0; u < urlAnnots.length; u++)
				addTokens(extraTokens, urlAnnots[u]);
			Annotation[] roleTermAnnots = stringParsed.getAnnotations("mods:roleTerm");
			for (int rt = 0; rt < roleTermAnnots.length; rt++)
				addTokens(extraTokens, roleTermAnnots[rt]);
			Annotation[] resourceTypeAnnots = stringParsed.getAnnotations("mods:typeOfResource");
			for (int rt = 0; rt < resourceTypeAnnots.length; rt++)
				addTokens(extraTokens, resourceTypeAnnots[rt]);
			for (int t = 0; (t < stringParsed.size()) && (parseError == null); t++) {
				String value = stringParsed.valueAt(t);
				if (stringPlain.indexOf(value) != -1)
					continue;
				if ((type != null) && (type.indexOf(value) != -1))
					continue;
				if (extraTokens.contains(value))
					continue;
				parseError = ("Parsed string is inconsistent with string string: '" + value + "' is not a part of '" + stringPlain + "'");
			}
		}
		
		return parseError;
	}
	
	private static void addTokens(StringVector tokens, Annotation annot) {
		for (int t = 0; t < annot.size(); t++)
			tokens.addElement(annot.valueAt(t));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolServlet#getStringType(de.uka.ipd.idaho.gamta.MutableAnnotation)
	 */
	protected String getStringType(MutableAnnotation stringParsed) {
		Annotation[] typeAnnots = stringParsed.getAnnotations("mods:classification");
		return ((typeAnnots.length == 0) ? this.refTypeSystem.classify(BibRefUtils.modsXmlToRefData(stringParsed)) : typeAnnots[0].getValue());
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.RefBankClient#findReferences(java.lang.String[], boolean, java.lang.String, java.lang.String, java.lang.String, java.lang.String, int, java.lang.String, java.util.Properties, int)
	 */
	public PooledStringIterator findReferences(String[] textPredicates, boolean disjunctive, String type, String user, String author, String title, int year, String origin, Properties externalIDsByType, int limit) {
		return this.findReferences(textPredicates, disjunctive, type, user, author, title, year, origin, externalIDsByType, false, limit);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.RefBankClient#findReferences(java.lang.String[], boolean, java.lang.String, java.lang.String, java.lang.String, java.lang.String, int, java.lang.String, java.util.Properties, boolean, int)
	 */
	public PooledStringIterator findReferences(String[] textPredicates, boolean disjunctive, String type, String user, String author, String title, int year, String origin, Properties externalIDsByType, boolean concise, int limit) {
		Properties detailPredicates = new Properties();
		if (author != null)
			detailPredicates.setProperty(AUTHOR_COLUMN_NAME, author);
		if (title != null)
			detailPredicates.setProperty(TITLE_COLUMN_NAME, title);
		if (year > 0)
			detailPredicates.setProperty(DATE_COLUMN_NAME, ("" + year));
		if (origin != null)
			detailPredicates.setProperty(ORIGIN_COLUMN_NAME, origin);
		if (externalIDsByType != null)
			for (Iterator eidit = externalIDsByType.keySet().iterator(); eidit.hasNext();) {
				String idType = ((String) eidit.next());
				String id = externalIDsByType.getProperty(idType);
				detailPredicates.setProperty(("ID-" + idType), id);
			}
		return this.findStrings(textPredicates, disjunctive, type, user, concise, limit, detailPredicates);
	}
//	
//	public static void main(String[] args) throws Exception {
//		long time = System.currentTimeMillis();
//		System.out.println(time);
//		
//		System.out.println(TIMESTAMP_DATE_FORMAT.format(new Date(time)));
//		
//		String dtdf = "EEE, dd MMM yyyy HH:mm:ss Z";
//		DateFormat tdf = new SimpleDateFormat(dtdf, Locale.US);
//		
//		System.out.println(tdf.format(new Date(time)));
//		
//		System.out.println(tdf.parse("Wed, 12 Oct 2011 13:43:15 +0200").getTime());
//		System.out.println(TIMESTAMP_DATE_FORMAT.parse("Wed, 12 Oct 2011 13:43:15 +0200").getTime());
//		
//		if (true)
//			return;
//		
////		String string;
////		string = "Lintott,C.J., Schawinski, K., Slosar, A., Land, K., Bamford, S., Thomas, D., Raddick, M. J., Nichol, R. C., Szalay, A., Andreescu, D., Murray, P. and Vandenberg, J. Galaxy Zoo: morphologies derived from visual inspection of galaxies from the Sloan Digital Sky Survey. Monthly Notices of the Royal Astronomical Society, 389, 2008. doi: 10.1111/j.1365-2966.2008.13689.x";
//////		string = "The Amazon Mechanical Turk, http://www.mturk.com";
////		string = "Snow, R., O’Connor, B., Jurafsky, D., Ng, A. Y. Cheap and fast—but is it good?: evaluating non-expert annotations for natural language tasks. In EMNLP 2008, Morristown, NJ, USA, 2008.";
////		String nString = getNormalizedString(string);
////		System.out.println(nString);
//		
//		StringPoolServlet grpbs = new StringPoolServlet();
//		grpbs.dataFolder = new File("E:/Projektdaten/GNUB/Test/");
//		Settings config = Settings.loadSettings(new File(grpbs.dataFolder, "config.cnfg"));
//		grpbs.init(config);
//		
//		File stringUploadFile = new File("E:/Projektdaten/GNUB", ("21330.upload.xml"));
//		BufferedReader stringUploadIn = new BufferedReader(new InputStreamReader(new FileInputStream(stringUploadFile), ENCODING));
//		grpbs.readStrings(stringUploadIn, "xml", System.currentTimeMillis());
//		if (true)
//			return;
//		
//		File stringFile = new File("E:/Projektdaten/GNUB", ("21330.xml"));
//		Reader stringIn = new InputStreamReader(new FileInputStream(stringFile), ENCODING);
//		MutableAnnotation stringParsed = Gamta.newDocument(Gamta.newTokenSequence(null, Gamta.INNER_PUNCTUATION_TOKENIZER));
//		SgmlDocumentReader.readDocument(stringIn, stringParsed);
//		
//		PooledStringIterator gpri;
//		
////		long time = System.currentTimeMillis();
////		String stringPlain = "Sharaf, M. R." +
////				" (2007)" +
////				" Monomorium dentatum sp. n., a new ant species from Egypt (Hymenoptera: Formicidae) related to the fossulatum group." +
////				" Zoology in the Middle East (41): 93-98.";
////		PooledString gprUpload = new PooledString(time, time, time, stringPlain, stringParsed);
////		final PooledString gpr = grpbs.updateString(gprUpload);
////		gpri = new PooledStringIterator() {
////			boolean hasNext = true;
////			boolean hasNextString() {
////				return this.hasNext;
////			}
////			PooledString getNextString() {
////				this.hasNext = false;
////				return gpr;
////			}
////			void close() {}
////		};
////		String[] stringIds = {"1E20FF95FFCCFFCCFF93DA79FFA0C505"};
////		gpri = grpbs.getStrings(stringIds);
//		String[] ftqps = {"", ""};
////		ftqps[0] = "98";
//		Properties dps = new Properties();
////		dps.setProperty("DocAuthor", "Sharaf");
////		dps.setProperty("DocTitle", "");
////		dps.setProperty("DocTitle", "Monomorium");
//		dps.setProperty("DocOrigin", "Zoology Middle East");
//		gpri = grpbs.findStrings(ftqps, true, dps);
//		
//		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(System.out));
//		grpbs.writeStrings(gpri, bw, -1, true);
//		bw.flush();
//	}
}
