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
package de.uka.ipd.idaho.plugins.bibRefs.dataParsers;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;

import de.uka.ipd.idaho.easyIO.streams.PeekReader;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefDataParser;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils.RefData;
import de.uka.ipd.idaho.stringUtils.StringUtils;

/**
 * Parser for (files of) references represented in the BibTeX data format.
 * 
 * @author sautter
 */
public class BibTeXParser extends BibRefDataParser {
	public BibTeXParser() {
		super("BibTeX");
	}
	public String getLabel() {
		return "BibTeX";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.plugins.bibRefs.BibRefDataFormat#streamParse(java.io.Reader)
	 */
	public ParsedRefDataIterator streamParse(Reader in) throws IOException {
		RecordingPeekReader rpr = new RecordingPeekReader(in, 32);
		final HashMap objects = new HashMap();
		while (rpr.peek() != -1) {
			if (rpr.peek() == '%')
				cropCommentLine(rpr);
			else if (rpr.peek() == '@') try {
				cropObject(rpr, objects);
			}
			catch (IOException ioe) {
				ioe.printStackTrace(System.out);
			}
			else System.out.print((char) rpr.read());
		}
		rpr.close();
		
		int rc = 0;
		for (Iterator oidit = objects.keySet().iterator(); oidit.hasNext();) {
			String oid = ((String) oidit.next());
			Object o = objects.get(oid);
			if (o instanceof BtObject)
				rc++;
		}
		
		final Iterator oidit = objects.keySet().iterator();
		final int refCount = rc;
		return new ParsedRefDataIterator() {
			private ParsedRefData next = null;
			private int r = 0;
			public int estimateRemaining() {
				return (refCount - this.r);
			}
			public ParsedRefData nextRefData() throws IOException {
				ParsedRefData rd = this.next;
				this.next = null;
				return rd;
			}
			public boolean hasNextRefData() throws IOException {
				if (this.next != null)
					return true;
				if (!oidit.hasNext())
					return false;
				String oid = ((String) oidit.next());
				Object o = objects.get(oid);
				if (o instanceof BtObject) {
					ParsedRefData ref = ((BtObject) o).toRefData(objects);
					this.unifyAttributes(ref);
					this.next = ref;
					this.r++;
				}
				return this.hasNextRefData();
			}
			private void unifyAttributes(RefData ref) {
				
				//	retrieve type
				String type = ref.getAttribute(PUBLICATION_TYPE_ATTRIBUTE);
				if (type != null)
					type = typeMappings.getProperty(type.toLowerCase(), type);
				ref.setAttribute(PUBLICATION_TYPE_ATTRIBUTE, type);
				
				//	parse author and editor strings
				String author = ref.getAttribute("author");
				if (author != null) {
					String[] authors = author.split("\\s+and\\s+");
					ref.setAttribute(AUTHOR_ANNOTATION_TYPE, authors[0]);
					for (int a = 1; a < authors.length; a++)
						ref.addAttribute(AUTHOR_ANNOTATION_TYPE, authors[a]);
				}
				String editor = ref.getAttribute("editor");
				if (editor != null) {
					String[] editors = editor.split("\\s+and\\s+");
					ref.setAttribute(EDITOR_ANNOTATION_TYPE, editors[0]);
					for (int e = 1; e < editors.length; e++)
						ref.addAttribute(EDITOR_ANNOTATION_TYPE, editors[e]);
				}
				
				//	year
				ref.renameAttribute("year", YEAR_ANNOTATION_TYPE);
				
				//	various titles
				ref.renameAttribute("title", TITLE_ANNOTATION_TYPE);
				ref.renameAttribute("booktitle", VOLUME_TITLE_ANNOTATION_TYPE);
				ref.renameAttribute("series", VOLUME_TITLE_ANNOTATION_TYPE);
				
				//	location & publisher
				ref.renameAttribute("address", LOCATION_ANNOTATION_TYPE);
				boolean gotPublisher = ref.renameAttribute("publisher", PUBLISHER_ANNOTATION_TYPE);
				if (!gotPublisher)
					gotPublisher = ref.renameAttribute("institution", PUBLISHER_ANNOTATION_TYPE);
				if (!gotPublisher)
					gotPublisher = ref.renameAttribute("school", PUBLISHER_ANNOTATION_TYPE);
				if (!gotPublisher)
					gotPublisher = ref.renameAttribute("howpublished", PUBLISHER_ANNOTATION_TYPE);
				
				//	add remaining institutions to volume title
				ref.renameAttribute("institution", VOLUME_TITLE_ANNOTATION_TYPE);
				ref.renameAttribute("school", VOLUME_TITLE_ANNOTATION_TYPE);
				ref.renameAttribute("howpublished", VOLUME_TITLE_ANNOTATION_TYPE);
				
				//	take care of pages
				if (ref.renameAttribute("pages", PAGINATION_ANNOTATION_TYPE))
					BibRefUtils.checkPaginationAndType(ref);
				
				//	journal data
				if (ref.renameAttribute("journal", JOURNAL_NAME_ANNOTATION_TYPE))
					ref.renameAttribute("series", SERIES_IN_JOURNAL_ANNOTATION_TYPE);
				ref.renameAttribute("volume", VOLUME_DESIGNATOR_ANNOTATION_TYPE);
				ref.renameAttribute("number", NUMERO_DESIGNATOR_ANNOTATION_TYPE);
				
				//	URL
				ref.renameAttribute("url", PUBLICATION_URL_ANNOTATION_TYPE);
			}
		};
	}
	
	private static class RecordingPeekReader extends PeekReader {
		private StringBuffer record = null;
		RecordingPeekReader(Reader in, int maxLookahead) throws IOException {
			super(in, maxLookahead);
		}
		void startRecord() {
			this.record = new StringBuffer();
		}
		String endRecord() {
			String record = ((this.record == null) ? null : this.record.toString());
			this.record = null;
			return record;
		}
		public int read() throws IOException {
			int r = super.read();
			if ((r != -1) && (this.record != null))
				this.record.append((char) r);
			return r;
		}
		public int read(char[] cbuf) throws IOException {
			// is fine, loops through to the three argument version
			return super.read(cbuf);
		}
		public int read(char[] cbuf, int off, int len) throws IOException {
			int r = super.read(cbuf, off, len);
			if ((r != -1) && (this.record != null))
				this.record.append(cbuf, off, r);
			return r;
		}
		public int read(CharBuffer target) throws IOException {
			// is fine, loops through to the char array version
			return super.read(target);
		}
	}
	
//	address: Publisher's address (usually just the city, but can be the full address for lesser-known publishers)
//	annote: An annotation for annotated bibliography styles (not typical)
//	author: The name(s) of the author(s) (in the case of more than one author, separated by and)
//	booktitle: The title of the book, if only part of it is being cited
//	chapter: The chapter number
//	crossref: The key of the cross-referenced entry
//	edition: The edition of a book, long form (such as "first" or "second")
//	editor: The name(s) of the editor(s)
//	eprint: A specification of an electronic publication, often a preprint or a technical report
//	howpublished: How it was published, if the publishing method is nonstandard
//	institution: The institution that was involved in the publishing, but not necessarily the publisher
//	journal: The journal or magazine the work was published in
//	key: A hidden field used for specifying or overriding the alphabetical order of entries (when the "author" and "editor" fields are missing). Note that this is very different from the key (mentioned just after this list) that is used to cite or cross-reference the entry.
//	month: The month of publication (or, if unpublished, the month of creation)
//	note: Miscellaneous extra information
//	number: The "(issue) number" of a journal, magazine, or tech-report, if applicable. (Most publications have a "volume", but no "number" field.)
//	organization: The conference sponsor
//	pages: Page numbers, separated either by commas or double-hyphens.
//	publisher: The publisher's name
//	school: The school where the thesis was written
//	series: The series of books the book was published in (e.g. "The Hardy Boys" or "Lecture Notes in Computer Science")
//	title: The title of the work
//	type: The field overriding the default type of publication (e.g. "Research Note" for techreport, "{PhD} dissertation" for phdthesis, "Section" for inbook/incollection)
//	url: The WWW address
//	volume: The volume of a journal or multi-volume book
//	year: The year of publication (or, if unpublished, the year of creation)
	
	
// article
//    An article from a journal or magazine.
//    Required fields: author, title, journal, year
//    Optional fields: volume, number, pages, month, note, key
// book
//    A book with an explicit publisher.
//    Required fields: author/editor, title, publisher, year
//    Optional fields: volume/number, series, address, edition, month, note, key
// booklet
//    A work that is printed and bound, but without a named publisher or sponsoring institution.
//    Required fields: title
//    Optional fields: author, howpublished, address, month, year, note, key
// conference
//    The same as inproceedings, included for Scribe compatibility.
// inbook
//    A part of a book, usually untitled. May be a chapter (or section or whatever) and/or a range of pages.
//    Required fields: author/editor, title, chapter/pages, publisher, year
//    Optional fields: volume/number, series, type, address, edition, month, note, key
// incollection
//    A part of a book having its own title.
//    Required fields: author, title, booktitle, publisher, year
//    Optional fields: editor, volume/number, series, type, chapter, pages, address, edition, month, note, key
// inproceedings
//    An article in a conference proceedings.
//    Required fields: author, title, booktitle, year
//    Optional fields: editor, volume/number, series, pages, address, month, organization, publisher, note, key
// manual
//    Technical documentation.
//    Required fields: title
//    Optional fields: author, organization, address, edition, month, year, note, key
// mastersthesis
//    A Master's thesis.
//    Required fields: author, title, school, year
//    Optional fields: type, address, month, note, key
// misc
//    For use when nothing else fits.
//    Required fields: none
//    Optional fields: author, title, howpublished, month, year, note, key
// phdthesis
//    A Ph.D. thesis.
//    Required fields: author, title, school, year
//    Optional fields: type, address, month, note, key
// proceedings
//    The proceedings of a conference.
//    Required fields: title, year
//    Optional fields: editor, volume/number, series, address, month, publisher, organization, note, key
// techreport
//    A report published by a school or other institution, usually numbered within a series.
//    Required fields: author, title, institution, year
//    Optional fields: type, number, address, month, note, key
// unpublished
//    A document having an author and title, but not formally published.
//    Required fields: author, title, note
//    Optional fields: month, year, key
	private static Properties typeMappings = new Properties();
	static {
		typeMappings.setProperty("article", JOURNAL_ARTICEL_REFERENCE_TYPE); // Journal
		typeMappings.setProperty("book", BOOK_REFERENCE_TYPE); // Whole book
		typeMappings.setProperty("conference", PROCEEDINGS_PAPER_REFERENCE_TYPE); // Conference paper
		typeMappings.setProperty("inbook", BOOK_CHAPTER_REFERENCE_TYPE); // Book chapter
		typeMappings.setProperty("incollection", BOOK_CHAPTER_REFERENCE_TYPE); // Book chapter
		typeMappings.setProperty("inproceedings", PROCEEDINGS_PAPER_REFERENCE_TYPE); // Conference paper
		typeMappings.setProperty("manual", BOOK_REFERENCE_TYPE); // Standard
		typeMappings.setProperty("mastersthesis", BOOK_REFERENCE_TYPE); // Thesis/Dissertation
		typeMappings.setProperty("misc", ""); // Misc, map to blank to auto-classify
		typeMappings.setProperty("phdthesis", BOOK_REFERENCE_TYPE); // Thesis/Dissertation
		typeMappings.setProperty("proceedings", PROCEEDINGS_REFERENCE_TYPE); // Conference proceeding
		typeMappings.setProperty("techreport", BOOK_REFERENCE_TYPE); // Report
	}
	
	private static HashSet ignoreOperators = new HashSet();
	static {
		ignoreOperators.add("tt");
		ignoreOperators.add("bf");
		ignoreOperators.add("if");
		ignoreOperators.add("path");
		ignoreOperators.add("em");
		ignoreOperators.add("par");
		//	TODO extend this
	}
	
	private static class BtObject {
		final String id;
		final String type;
		private String source;
		private HashMap attributes = new HashMap(8);
		BtObject(String id, String type) {
			this.id = id;
			this.type = type;
		}
		void addAttribute(String name, BtString value) {
			this.attributes.put(name, value);
		}
		BtString getAttribute(String name) {
			return ((BtString) this.attributes.get(name));
		}
		ParsedRefData toRefData(HashMap strings) {
			ParsedRefData rd = new ParsedRefData();
			rd.addAttribute(PUBLICATION_TYPE_ATTRIBUTE, this.type);
			for (Iterator anit = this.attributes.keySet().iterator(); anit.hasNext();) {
				String name = ((String) anit.next());
				BtString value = this.getAttribute(name);
				if (value != null) {
					String valStr = value.toString(strings);
					rd.addAttribute(name, valStr);
				}
			}
			if (this.source != null)
				rd.setSource(this.source);
			return rd;
		}
		void setSource(String source) {
			this.source = source;
		}
	}
	
	private static class BtString {
		private ArrayList parts = new ArrayList(2);
		BtString() {}
		void addPart(BtStringPart part) {
			this.parts.add(part);
		}
		String toString(HashMap strings) {
			StringBuffer sb = new StringBuffer();
			for (int p = 0; p < this.parts.size(); p++) {
				BtStringPart part = ((BtStringPart) this.parts.get(p));
				if (part.operand == textInsertMarker) {
					String operator = (part.string.startsWith("text") ? part.string.substring("text".length()) : part.string);
					if (ignoreOperators.contains(operator)) {}
					else if ("asciicircum".equals(operator))
						sb.append("^");
					else if ("visiblespace".equals(operator))
						sb.append("_");
					else if ("ss".equals(operator))
						sb.append("ß");
					else if (operator.startsWith("asterisk"))
						sb.append("*");
					else if ("dh".equalsIgnoreCase(operator) || "dj".equalsIgnoreCase(operator))
						sb.append(operator.charAt(0));
					else {
						if ("aa".equalsIgnoreCase(operator))
							operator = (operator.charAt(0) + "ring");
						else if ("o".equalsIgnoreCase(operator))
							operator = (operator + "slash");
						char ch = StringUtils.getCharForName(operator);
						if (ch != 0)
							sb.append(ch);
						else sb.append("OP-I[" + part.string + "]");
					}
				}
				else if (part.operand != null) {
					if ((part.string.length() == 1) && ("\"'´`^.=|~bcdGhHkrtuUv".indexOf(part.string.charAt(0)) != -1)) {
						String oValue = part.operand.toString(strings);
						if (oValue.length() == 0)
							sb.append(part.string);
						else {
							sb.append(extendChar(part.string.charAt(0), oValue.charAt(0)));
							sb.append(oValue.substring(1));
						}
					}
					else {
						String operator = (part.string.startsWith("text") ? part.string.substring("text".length()) : part.string);
						if ("gravis".equals(operator))
							operator = "grave";
						else if ("acutus".equals(operator))
							operator = "acute";
						else if ("circumflexus".equals(operator))
							operator = "circumflex";
						else if ("diaeresis".equals(operator))
							operator = "dieresis";
						String oValue = part.operand.toString(strings);
						if (ignoreOperators.contains(operator))
							sb.append(oValue);
						else if (oValue.length() == 0)
							sb.append(part.string);
						else {
							sb.append(extendChar(operator, oValue.charAt(0)));
							sb.append(oValue.substring(1));
						}
					}
				}
				else if (part.isQuoted)
					sb.append(part.string);
				else {
					String[] keys = part.string.split("\\s+", -1);
					for (int k = 0; k < keys.length; k++) {
						if (keys[k].length() == 0) {
							if ((k+1) != keys.length)
								sb.append(' ');
							continue;
						}
						Object valObj = strings.get(keys[k]);
						if (valObj instanceof BtString) {
							String valStr = ((BtString) valObj).toString(strings);
							if (valStr != null) {
								valStr = valStr.trim();
								if (valStr.length() != 0) {
									sb.append(valStr.trim());
									if ((k+1) != keys.length)
										sb.append(' ');
								}
							}
						}
						else {
							sb.append(keys[k]);
							if ((k+1) != keys.length)
								sb.append(' ');
						}
					}
				}
			}
			return sb.toString().trim();
		}
	}
	
	private static final BtString textInsertMarker = new BtString();
	
	private static class BtStringPart {
		final String string;
		final boolean isQuoted; // if not, we'll have to check it against string constants
		final BtString operand; // if not null, string is command: either (1) insert marker operand, it's some text insertion (character name or other), (2) or, full command
		BtStringPart(String string, boolean isQuoted, BtString operand) {
			this.string = string;
			this.isQuoted = isQuoted;
			this.operand = operand;
		}
	}
	
	private static void cropCommentLine(RecordingPeekReader rpr) throws IOException {
		StringBuffer comment = new StringBuffer();
		while ((rpr.peek() != '\n') && (rpr.peek() != '\r') && (rpr.peek() != -1))
			comment.append((char) rpr.read());
		while ((rpr.peek() == '\n') || (rpr.peek() == '\r'))
			rpr.read();
		System.out.println("COMMENT: " + comment.toString());
	}
	
	private static void cropObject(RecordingPeekReader rpr, HashMap objects) throws IOException {
		rpr.startRecord();
		rpr.read(); // consume '@'
		StringBuffer objType = new StringBuffer();
		while ((32 < rpr.peek()) && Character.isLetterOrDigit(rpr.peek()))
			objType.append((char) rpr.read());
		if (objType.length() == 0)
			throw new IOException("Invalid object type after '@'");
		skipLeadingSpace(rpr);
		
		String type = objType.toString();
		System.out.println("OBJECT TYPE: " + type);
		if ((rpr.peek() != '(') && (rpr.peek() != '{'))
			throw new IOException("Invalid object bracket after '@" + type.toString() + "'");
		char objStart = ((char) rpr.read());
		char objEnd = ((objStart == '(') ? ')' : '}');
		if ("string".equalsIgnoreCase(type)) {
			rpr.endRecord();
			cropString(rpr, objEnd, objects);
		}
		else if ("comment".equalsIgnoreCase(type)) {
			rpr.endRecord();
			cropComment(rpr, objEnd);
		}
		else if ("preamble".equalsIgnoreCase(type)) {
			rpr.endRecord();
			cropPreamble(rpr, objEnd);
		}
		else {
			BtObject bto = cropObjectBody(rpr, objEnd, type);
			String btoSource = rpr.endRecord();
			if (bto != null) {
				bto.setSource(btoSource);
				objects.put(bto.id, bto);
			}
		}
	}
	
	private static boolean skipLeadingSpace(RecordingPeekReader rpr) throws IOException {
		boolean skipped = false;
		while ((rpr.peek() != -1) && (rpr.peek() < 33)) {
			skipped = true;
			rpr.read();
		}
		return skipped;
	}
	
	private static BtObject cropObjectBody(RecordingPeekReader rpr, char oEnd, String type) throws IOException {
		
		//	crop ID
		StringBuffer objId = new StringBuffer();
		while ((rpr.peek() != -1) && (rpr.peek() != ',')) {
			char ch = ((char) rpr.read());
			if (32 < ch)
				objId.append(ch);
		}
		if (objId.length() == 0)
			throw new IOException("Invalid object ID in '@" + type + "'");
		if (rpr.peek() == ',')
			rpr.read();
		skipLeadingSpace(rpr);
		System.out.println(" - ID: " + objId.toString());
		
		BtObject bto = new BtObject(objId.toString(), type);
		while ((rpr.peek() != -1) && (rpr.peek() != oEnd)) {
			StringBuffer name = new StringBuffer();
			while ((32 < rpr.peek()) && (rpr.peek() != '='))
				name.append((char) rpr.read());
			skipLeadingSpace(rpr);
			if (rpr.peek() != '=')
				throw new IOException("Invalid assignment marker after '" + name.toString() + "'");
			rpr.read();
			skipLeadingSpace(rpr);
			BtString value = cropValue(rpr, ',', '}');
			skipLeadingSpace(rpr);
			bto.addAttribute(name.toString(), value);
		}
		if (rpr.peek() == oEnd)
			rpr.read();
		return bto;
	}
	
	private static void cropComment(RecordingPeekReader rpr, char cEnd) throws IOException {
		boolean escaped = false;
		while ((rpr.peek() != -1) && (rpr.peek() != '@') && (escaped || (rpr.peek() != cEnd))) {
			char ch = ((char) rpr.read());
			if (escaped) {
				escaped = false;
				continue;
			}
			else if (ch == '\\')
				escaped = true;
			else if (ch == '"')
				cropQuoted(rpr);
			else if (ch == '{')
				cropScope(rpr, false);
		}
		if (rpr.peek() == cEnd)
			rpr.read();
	}
	
	private static void cropPreamble(RecordingPeekReader rpr, char pEnd) throws IOException {
		boolean escaped = false;
		while ((rpr.peek() != -1) && (rpr.peek() != '@') && (escaped || (rpr.peek() != pEnd))) {
			char ch = ((char) rpr.read());
			if (escaped) {
				escaped = false;
				continue;
			}
			else if (ch == '\\')
				escaped = true;
			else if (ch == '"')
				cropQuoted(rpr);
			else if (ch == '{')
				cropScope(rpr, false);
		}
		if (rpr.peek() == pEnd)
			rpr.read();
	}
	
	private static void cropString(RecordingPeekReader rpr, char sEnd, HashMap strings) throws IOException {
		skipLeadingSpace(rpr);
		StringBuffer name = new StringBuffer();
		while ((32 < rpr.peek()) && (rpr.peek() != '='))
			name.append((char) rpr.read());
		skipLeadingSpace(rpr);
		if (rpr.peek() != '=')
			throw new IOException("Invalid assignment marker after '" + name.toString() + "'");
		rpr.read();
		skipLeadingSpace(rpr);
		BtString value = cropValue(rpr, sEnd, ((char) 0));
		strings.put(name.toString(), value);
	}
	
	private static BtString cropValue(RecordingPeekReader rpr, char vEnd, char oEnd) throws IOException {
		BtString bts = new BtString();
		boolean escaped = false;
		StringBuffer value = new StringBuffer();
		while ((rpr.peek() != -1) && (escaped || ((rpr.peek() != vEnd) && (rpr.peek() != oEnd)))) {
			char ch = ((char) rpr.read());
			if (escaped) {
				escaped = false;
				if (handleEscaped(rpr, ch, bts, value, false))
					value = new StringBuffer();
			}
			else if ((ch == '`') && (rpr.peek() == '`')) {
				value.append('"');
				rpr.read();
			}
			else if ((ch == '\'') && (rpr.peek() == '\'')) {
				value.append('"');
				rpr.read();
			}
			else if (ch == '-') {
				value.append(ch);
				while (rpr.peek() == '-')
					rpr.read();
			}
			else if (ch == '\\')
				escaped = true;
			else if (ch == '"') {
				if (value.length() != 0) {
					bts.addPart(new BtStringPart(value.toString(), false, null));
					value = new StringBuffer();
				}
				BtString qbt = cropQuoted(rpr);
				bts.parts.addAll(qbt.parts);
			}
			else if (ch == '#') {
				if (value.length() != 0) {
					bts.addPart(new BtStringPart(value.toString(), false, null));
					value = new StringBuffer();
				}
			}
			else if (ch == '{') {
				if (value.length() != 0) {
					bts.addPart(new BtStringPart(value.toString(), false, null));
					value = new StringBuffer();
				}
				BtString sbt = cropScope(rpr, false);
				bts.parts.addAll(sbt.parts);
			}
			else if (ch == '@')
				throw new IOException("Unquoted @ in '" + value.toString() + "'");
			else if ((ch < 33) || (ch == '~')) {
				if ((value.length() == 0) || (32 < value.charAt(value.length()-1)))
					value.append(' ');
			}
			else value.append(ch);
		}
		if (value.length() != 0)
			bts.addPart(new BtStringPart(value.toString(), false, null));
		if (rpr.peek() == vEnd)
			rpr.read();
		return bts;
	}
	
	private static BtString cropScope(RecordingPeekReader rpr, boolean inQuotes) throws IOException {
		BtString bts = new BtString();
		boolean escaped = false;
		StringBuffer value = new StringBuffer();
		while ((rpr.peek() != -1) && (escaped || (rpr.peek() != '}'))) {
			char ch = ((char) rpr.read());
			if (escaped) {
				escaped = false;
				if (handleEscaped(rpr, ch, bts, value, inQuotes))
					value = new StringBuffer();
			}
			else if (ch == '\\')
				escaped = true;
			else if (ch == '-') {
				value.append(ch);
				while (rpr.peek() == '-')
					rpr.read();
			}
			else if ((ch == '`') && (rpr.peek() == '`')) {
				value.append('"');
				rpr.read();
			}
			else if ((ch == '\'') && (rpr.peek() == '\'')) {
				value.append('"');
				rpr.read();
			}
			else if (ch == '#') {
				if (inQuotes)
					value.append(ch);
				else if (value.length() != 0) {
					bts.addPart(new BtStringPart(value.toString(), inQuotes, null));
					value = new StringBuffer();
				}
			}
			else if (ch == '{') {
				if (value.length() != 0) {
					bts.addPart(new BtStringPart(value.toString(), inQuotes, null));
					value = new StringBuffer();
				}
				BtString sbt = cropScope(rpr, inQuotes);
				bts.parts.addAll(sbt.parts);
			}
			else if (ch == '@')
				throw new IOException("Unquoted @ in '" + value.toString() + "'");
			else if ((ch < 33) || (ch == '~')) {
				if ((value.length() == 0) || (32 < value.charAt(value.length()-1)))
					value.append(' ');
			}
			else value.append(ch);
		}
		if (value.length() != 0)
			bts.addPart(new BtStringPart(value.toString(), inQuotes, null));
		if (rpr.peek() == '}')
			rpr.read();
		return bts;
	}
	
	private static BtString cropQuoted(RecordingPeekReader rpr) throws IOException {
		BtString bts = new BtString();
		boolean escaped = false;
		StringBuffer value = new StringBuffer();
		while ((rpr.peek() != -1) && (escaped || (rpr.peek() != '"'))) {
			char ch = ((char) rpr.read());
			if (escaped) {
				escaped = false;
				if (handleEscaped(rpr, ch, bts, value, true))
					value = new StringBuffer();
			}
			else if (ch == '\\')
				escaped = true;
			else if (ch == '-') {
				value.append(ch);
				while (rpr.peek() == '-')
					rpr.read();
			}
			else if ((ch == '`') && (rpr.peek() == '`')) {
				value.append('"');
				rpr.read();
			}
			else if ((ch == '\'') && (rpr.peek() == '\'')) {
				value.append('"');
				rpr.read();
			}
			else if (ch == '{') {
				if (value.length() != 0) {
					bts.addPart(new BtStringPart(value.toString(), true, null));
					value = new StringBuffer();
				}
				BtString sbt = cropScope(rpr, true);
				bts.parts.addAll(sbt.parts);
			}
			else if ((ch < 33) || (ch == '~')) {
				if ((value.length() == 0) || (32 < value.charAt(value.length()-1)))
					value.append(' ');
			}
			else value.append(ch);
		}
		if (value.length() != 0)
			bts.addPart(new BtStringPart(value.toString(), true, null));
		if (rpr.peek() == '"')
			rpr.read();
		return bts;
	}
	
	private static boolean handleEscaped(RecordingPeekReader rpr, char ch, BtString bts, StringBuffer value, boolean inQuotes) throws IOException {
		
		//	escaped control character
		if ("$%_{}#&".indexOf(ch) != -1) {
			value.append(ch);
			return false;
		}
		
		//	digit grouping comma
		if (ch == ',') {
			value.append(ch);
			return false;
		}
		
		//	cleans inline tag layout commands
		if (ch == '/')
			return false;
		
		//	soft hyphen or line break (ignore it, we're not printing, so hyphenation is obsolete)
		if ((ch == '-') || (ch == '\\'))
			return false;
		
		//	encoded accent operator
		if ("\"'´`^.=|~".indexOf(ch) != -1) {
			char nCh = ((char) rpr.peek());
			
			//	if next is letter, that's the operand
			if ((('a' <= nCh) && (nCh <= 'z')) || (('A' <= nCh) && (nCh <= 'Z'))) {
				rpr.read();
				value.append(extendChar(ch, nCh));
				return false;
			}
			
			//	if next is opening curly bracket, operand is scope
			if (nCh == '{') {
				
				//	we have a sub call, store value
				if (value.length() != 0) {
					bts.addPart(new BtStringPart(value.toString(), inQuotes, null));
					value = new StringBuffer();
				}
				
				//	consume start of scope
				rpr.read();
				
				//	read and store scope
				BtString sValue = cropScope(rpr, inQuotes);
				bts.addPart(new BtStringPart(("" + ch), inQuotes, sValue));
				return true;
			}
			
			//	otherwise, output operator
			value.append(ch);
			return false;
		}
		
		//	other (textual) operator
		if ((('a' <= ch) && (ch <= 'z')) || (('A' <= ch) && (ch <= 'Z'))) {
			
			//	we have a sub call, store value
			if (value.length() != 0) {
				bts.addPart(new BtStringPart(value.toString(), inQuotes, null));
				value = new StringBuffer();
			}
			
			//	read operator
			StringBuffer operator = new StringBuffer();
			operator.append(ch);
			char nCh = ((char) rpr.peek());
			while ((('a' <= nCh) && (nCh <= 'z')) || (('A' <= nCh) && (nCh <= 'Z'))) {
				operator.append((char) rpr.read());
				nCh = ((char) rpr.peek());
			}
			
			//	read scoped operand
			if (nCh == '{') {
				
				//	consume start of scope
				rpr.read();
				
				//	read and store scope
				BtString sValue = cropScope(rpr, inQuotes);
				bts.addPart(new BtStringPart(operator.toString().toLowerCase(), inQuotes, sValue));
			}
			
			//	some character name or other command
			else {
				
				//	store text replacement
				bts.addPart(new BtStringPart(operator.toString(), inQuotes, textInsertMarker));
				
				//	consume next space
				if (rpr.peek() < 33)
					rpr.read();
			}
			
			//	we're done here
			return true;
		}
		
		//	invalid operator
		else throw new IOException("Invalid operator after \\: " + ch);
	}
	
	private static char extendChar(char op, char baseChar) {
		String operator = null;
		if (op == '"')
			operator = "dieresis";
		else if (op == '\'')
			operator = "acute";
		else if (op == '´')
			operator = "acute";
		else if (op == '`')
			operator = "grave";
		else if (op == '^')
			operator = "circumflex";
		else if (op == '=')
			operator = "macron";
		else if (op == '~')
			operator = "tilde";
		else if (op == 'r')
			operator = "ring";
		else if (op == 'v')
			operator = "caron";
		else if (op == 'u')
			operator = "breve";
		else if (op == 'G')
			operator = "dblgrave";
		else if (op == 'H')
			operator = "dieresis";
		else if (op == 'U')
			operator = "dieresis";
		else if (op == 'd')
			operator = "dotbelow";
		else if (op == 'k')
			operator = "ogonek";
		else if (op == 'c')
			operator = "cedilla";
		else if (op == 'h')
			operator = "hookabove";
		else if (op == 'h')
			operator = "invertedbreve";
		//	NOT INTERPRETED FOR NOW: .|b
		return ((operator == null) ? baseChar : extendChar(operator, baseChar));
	}
	private static char extendChar(String operator, char baseChar) {
		String charName = (baseChar + operator);
		char ch = StringUtils.getCharForName(charName);
		return ((ch == 0) ? baseChar : ch);
	}
	
	public static void main(String[] args) throws Exception {
		File testDataPath = new File("E:/Projektdaten/RefBank/DataImport/BibTeX");
		BibTeXParser btf = new BibTeXParser();
//		Reader rr = new FileReader(new File(testDataPath, "ABLE-Bibtex.bib"));
//		Reader rr = new FileReader(new File(testDataPath, "papers.test.bib"));
		Reader rr = new FileReader(new File(testDataPath, "Bormene.bib"));
		ParsedRefDataIterator rdit = btf.streamParse(rr);
		while (rdit.hasNextRefData()) {
			RefData rd = rdit.nextRefData();
			System.out.println(rd.toXML());
			System.out.println(" ==> " + BibRefUtils.toRefString(rd));
		}
		if (true)
			return;
		
//		Reader reader = new FileReader(new File(testDataPath, "papers.bib")); // multi file from Eric
//		Reader r = new FileReader(new File(testDataPath, "unix.bib")); // single large file
//		Reader r = new FileReader(new File(testDataPath, "journals.bib")); // single small file with string constant definitions only
//		Reader r = new FileReader(new File(testDataPath, "journals.tests.bib")); // file with syntax tests
		
		//	use PeekReader to take file apart
//		HashMap strings = new HashMap();
//		PeekReader pr = new PeekReader(r, 32);
//		while (pr.peek() != -1) {
//			if (pr.startsWith("%%%", true))
//				cropCommentLine(pr);
//			else if (pr.peek() == '@')
//				cropObject(pr, strings);
//			else System.out.print((char) pr.read());
//		}
		
		//	read multi-part bibliography from Eric
		Reader r;
		RecordingPeekReader rpr;
		HashMap objects = new HashMap();
//		
//		//	read author file
//		r = new FileReader(new File(testDataPath, "authors.bib"));
//		pr = new PeekReader(r, 32);
//		while (pr.peek() != -1) {
//			if (pr.peek() == '%')
//				cropCommentLine(pr);
//			else if (pr.peek() == '@') try {
//				cropObject(pr, objects);
//			}
//			catch (IOException ioe) {
//				ioe.printStackTrace(System.out);
//			}
//			else System.out.print((char) pr.read());
//		}
//		
//		//	read journals file
//		r = new FileReader(new File(testDataPath, "journals.bib"));
//		pr = new PeekReader(r, 32);
//		while (pr.peek() != -1) {
//			if (pr.peek() == '%')
//				cropCommentLine(pr);
//			else if (pr.peek() == '@') try {
//				cropObject(pr, objects);
//			}
//			catch (IOException ioe) {
//				ioe.printStackTrace(System.out);
//			}
//			else System.out.print((char) pr.read());
//		}
//		
//		//	read conferences file
//		r = new FileReader(new File(testDataPath, "conf.bib"));
//		pr = new PeekReader(r, 32);
//		while (pr.peek() != -1) {
//			if (pr.peek() == '%')
//				cropCommentLine(pr);
//			else if (pr.peek() == '@') try {
//				cropObject(pr, objects);
//			}
//			catch (IOException ioe) {
//				ioe.printStackTrace(System.out);
//			}
//			else System.out.print((char) pr.read());
//		}
//		
//		//	read main file
//		r = new FileReader(new File(testDataPath, "papers.bib"));
//		pr = new PeekReader(r, 32);
//		while (pr.peek() != -1) {
//			if (pr.peek() == '%')
//				cropCommentLine(pr);
//			else if (pr.peek() == '@') try {
//				cropObject(pr, objects);
//			}
//			catch (IOException ioe) {
//				ioe.printStackTrace(System.out);
//			}
//			else System.out.print((char) pr.read());
//		}
		
		//	read main file
		r = new FileReader(new File(testDataPath, "ABLE-Bibtex.bib"));
		rpr = new RecordingPeekReader(r, 32);
		while (rpr.peek() != -1) {
			if (rpr.peek() == '%')
				cropCommentLine(rpr);
			else if (rpr.peek() == '@') try {
				cropObject(rpr, objects);
			}
			catch (IOException ioe) {
				ioe.printStackTrace(System.out);
			}
			else System.out.print((char) rpr.read());
		}
		
		//	output something
		for (Iterator idit = objects.keySet().iterator(); idit.hasNext();) {
			String oid = ((String) idit.next());
			Object o = objects.get(oid);
			if (o instanceof BtObject) {
				BtObject bto = ((BtObject) o);
				BtString author = bto.getAttribute("author");
				if (author == null)
					continue;
				System.out.println(bto.type + " (" + bto.id + "):\n - author is " + author.toString(objects));
				BtString title = bto.getAttribute("title");
				if (title != null)
					System.out.println(" - title is " + title.toString(objects));
				BtString editor = bto.getAttribute("editor");
				if (editor != null)
					System.out.println(" - editor is " + editor.toString(objects));
			}
		}
	}
}