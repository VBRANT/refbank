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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashSet;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.uka.ipd.idaho.htmlXmlUtil.Parser;
import de.uka.ipd.idaho.htmlXmlUtil.TokenReceiver;
import de.uka.ipd.idaho.htmlXmlUtil.TreeNodeAttributeSet;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.Grammar;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.StandardGrammar;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefDataParser;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils.RefData;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * Parser for (files of) references represented in the EndNote XML data format.
 * 
 * @author sautter
 */
public class EndNoteXmlParser extends BibRefDataParser {
	public EndNoteXmlParser() {
		super("EndNoteXML");
	}
	
	public String getLabel() {
		return "EndNote XML";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.plugins.bibRefs.BibRefDataFormat#streamParse(java.io.Reader)
	 */
	public ParsedRefDataIterator streamParse(Reader in) throws IOException {
		final BufferedReader br = ((in instanceof BufferedReader) ? ((BufferedReader) in) : new BufferedReader(in));
		final Object lock = new Object();
		final ParsedRefData[] theNext = {null};
		final IOException[] theIoe = {null};
		
		//	set up token receiver
		final TokenReceiver refTr = new TokenReceiver() {
			private boolean inContent = true;
			private String authorType = null;
			private String dbId = null;
			private String lastTag = null;
			private ParsedRefData ref = null;
			private StringVector refSourceTokens = new StringVector();
			public void storeToken(String token, int treeDepth) throws IOException {
				
				//	we have a next reference, wait until another is required
				if (theNext[0] != null) {
					synchronized(lock) {
						lock.notify();
						try {
							lock.wait();
						} catch (InterruptedException ie) {}
					}
				}
				if (grammar.isTag(token)) {
					if (this.ref != null)
						this.refSourceTokens.addElement(token);
					if ("style".equals(grammar.getType(token)))
						return;
					this.lastTag = grammar.getType(token);
					boolean startTag = !grammar.isEndTag(token);
					if ("record".equals(this.lastTag)) {
						if (startTag) {
							this.ref = new ParsedRefData();
							this.refSourceTokens.addElement(token);
						}
						else {
							if (this.ref != null) {
								this.ref.setSource(this.refSourceTokens.concatStrings(""));
								theNext[0] = this.ref;
							}
							this.ref = null;
							this.refSourceTokens.clear();
						}
						return;
					}
					else if (this.lastTag.endsWith("-authors")) {
						this.authorType = (startTag ? this.lastTag.substring(0, (this.lastTag.length()-1)) : null);
						return;
					}
					else if ("keywords".equals(this.lastTag)) {
						this.inContent = false;
						return;
					}
					else if ("notes".equals(this.lastTag)) {
						this.inContent = false;
						return;
					}
					else if ("abstract".equals(this.lastTag)) {
						this.inContent = false;
						return;
					}
					else if ("database".equals(this.lastTag)) {
						this.inContent = false;
						return;
					}
					else if ("auth-address".equals(this.lastTag)) {
						this.inContent = false;
						return;
					}
					else if ("language".equals(this.lastTag)) {
						this.inContent = false;
						return;
					}
					else if ("source-app".equals(this.lastTag)) {
						this.inContent = false;
						return;
					}
					else if ("pub-dates".equals(this.lastTag)) {
						this.inContent = false;
						return;
					}
					else if ("ref-type".equals(this.lastTag)) {
						this.inContent = startTag;
						if ((this.ref != null) && startTag) {
							TreeNodeAttributeSet tnas = TreeNodeAttributeSet.getTagAttributes(token, grammar);
							String refType = tnas.getAttribute("name");
							if (refType != null)
								this.ref.addAttribute(this.lastTag, refType);
						}
						return;
					}
					else if ("key".equals(this.lastTag)) {
						if (startTag){
							TreeNodeAttributeSet tnas = TreeNodeAttributeSet.getTagAttributes(token, grammar);
							this.dbId = tnas.getAttribute("db-id");
						}
						else this.dbId = null;
						return;
					}
					else {
						this.inContent = startTag;
						return;
					}
				}
				else if (this.ref != null) {
					if (token.trim().length() == 0)
						return;
					System.out.println("Text of type '" + this.lastTag + "': " + token.trim());
					this.refSourceTokens.addElement(this.inContent ? token : "...");
					if (this.inContent && (this.lastTag != null)) {
						if (this.dbId == null) {
							//	unescape data values
							token = grammar.unescape(token.trim());
							//	trim terminal commas and dots (dots from all but author names)
							while ((token.endsWith(".") && !this.lastTag.endsWith("author")) || token.endsWith(","))
								token = token.substring(0, (token.length() -1)).trim();
						}
						else token = (this.dbId + ":" + grammar.unescape(token.trim()));
						this.ref.addAttribute(((this.authorType == null) ? this.lastTag : this.authorType), token);
					}
				}
			}
			public void close() throws IOException {}
		};
		
		//	create and start parsing thread
		final Thread pt = new Thread() {
			public void run() {
				synchronized(lock) {
					lock.notify();
				}
				
				//	parse data
				try {
					parser.stream(br, refTr);
				}
				catch (IOException ioe) {
					theIoe[0] = ioe;
				}
				
				//	however we got here, parsing is over
				try {
					br.close();
				}
				catch (IOException ioe) {
					theIoe[0] = ioe;
				}
				
				//	wake up whoever still waits on the iterator
				synchronized(lock) {
					lock.notify();
				}
			}
		};
		synchronized(lock) {
			pt.start();
			try {
				lock.wait();
			} catch (InterruptedException ie) {}
		}
		
		//	return handshake-based iterator
		return new ParsedRefDataIterator() {
			private boolean inEnd = false;
			public int estimateRemaining() {
				return -1;
			}
			public ParsedRefData nextRefData() throws IOException {
				ParsedRefData rd = theNext[0];
				theNext[0] = null;
				return rd;
			}
			public boolean hasNextRefData() throws IOException {
				if (theNext[0] != null)
					return true;
				if (this.inEnd)
					return false;
				
				//	let parser read next reference
				if (pt.isAlive())
					synchronized(lock) {
						lock.notify();
						try {
							lock.wait();
						} catch (InterruptedException ie) {}
					}
				
				//	parser terminated, no more data to come
				else return false;
				
				//	pass on exception from parser
				if (theIoe[0] != null)
					throw theIoe[0];
				
				//	we have another reference
				if (theNext[0] != null) {
					EndNoteXmlParser.this.unifyAttributes(theNext[0]);
					return true;
				}
				
				//	nothing more to come
				this.inEnd = true;
				return false;
			}
		};
	}
	
	private void unifyAttributes(RefData ref) {
		
		//	get type (helps determining required fields)
		String type = ref.getAttribute("ref-type");
		System.out.println("Type is " + type);
		if (type != null) {
			if (type.matches("[0-9]+"))
				type = null;
			else type = typeMappings.getProperty(type.toLowerCase(), type);
		}
		System.out.println("Type mapped to " + type);
		
		//	author & editor
		boolean gotAuthor = ref.renameAttribute("author", AUTHOR_ANNOTATION_TYPE);
		if (!gotAuthor)
			gotAuthor = ref.renameAttribute("secondary-author", AUTHOR_ANNOTATION_TYPE);
		if (!gotAuthor)
			gotAuthor = ref.renameAttribute("tertiary-author", AUTHOR_ANNOTATION_TYPE);
		if (!gotAuthor)
			gotAuthor = ref.renameAttribute("subsidiary-author", AUTHOR_ANNOTATION_TYPE);
		if (!gotAuthor)
			gotAuthor = ref.renameAttribute("translated-author", AUTHOR_ANNOTATION_TYPE);
		boolean gotEditor = ref.renameAttribute("subsidiary-author", EDITOR_ANNOTATION_TYPE);
		if (!gotEditor)
			gotEditor = ref.renameAttribute("tertiary-author", EDITOR_ANNOTATION_TYPE);
		if (!gotEditor)
			gotEditor = ref.renameAttribute("secondary-author", EDITOR_ANNOTATION_TYPE);
		ref.renameAttribute("secondary-author", AUTHOR_ANNOTATION_TYPE);
		ref.renameAttribute("tertiary-author", AUTHOR_ANNOTATION_TYPE);
		
		//	year
		boolean gotYear = ref.renameAttribute("year", YEAR_ANNOTATION_TYPE);
		if (!gotYear)
			gotYear = ref.renameAttribute("date", YEAR_ANNOTATION_TYPE);
		
		//	volume, issue & numero
		ref.renameAttribute("number", NUMERO_DESIGNATOR_ANNOTATION_TYPE);
		ref.renameAttribute("issue", ISSUE_DESIGNATOR_ANNOTATION_TYPE);
		ref.renameAttribute("volume", VOLUME_DESIGNATOR_ANNOTATION_TYPE);
		if (ref.hasAttribute(VOLUME_DESIGNATOR_ANNOTATION_TYPE)) {
			String volumeString = ref.getAttribute(VOLUME_DESIGNATOR_ANNOTATION_TYPE);
			if (!numberPattern.matcher(volumeString).matches()) try {
				Matcher im = bracketNumberPattern.matcher(volumeString);
				if (im.find()) {
					int issue = Integer.parseInt(im.group(0).replaceAll("[^0-9]", ""));
					volumeString = im.replaceAll("");
					Matcher vm = numberPattern.matcher(volumeString);
					if (vm.find()) {
						int volume = Integer.parseInt(vm.group(0));
						ref.setAttribute(VOLUME_DESIGNATOR_ANNOTATION_TYPE, ("" + volume));
						ref.setAttribute(ISSUE_DESIGNATOR_ANNOTATION_TYPE, ("" + issue));
					}
				}
			} catch (NumberFormatException nfe) {}
		}
		
		//	publisher name & location
		boolean gotPublisher = (
				ref.renameAttribute("publisher", PUBLISHER_ANNOTATION_TYPE)
				|
				ref.renameAttribute("pub-location", LOCATION_ANNOTATION_TYPE)
			);
		
		//	title, volume title & journal name
		HashSet titles = new HashSet();
		boolean gotTitle = ref.renameAttribute("title", TITLE_ANNOTATION_TYPE);
		if (!gotTitle)
			gotTitle = ref.renameAttribute("secondary-title", TITLE_ANNOTATION_TYPE);
		if (!gotTitle)
			gotTitle = ref.renameAttribute("tertiary-title", TITLE_ANNOTATION_TYPE);
		if (!gotTitle)
			gotTitle = ref.renameAttribute("translated-title", TITLE_ANNOTATION_TYPE);
		if (gotTitle)
			titles.add(ref.getAttribute(TITLE_ANNOTATION_TYPE));
		boolean gotJournalName = false;
		if ((type == null) ? !gotPublisher : type.startsWith("journal")) {
			gotJournalName = ref.renameAttribute("full-title", JOURNAL_NAME_ANNOTATION_TYPE);
			if (!gotJournalName)
				gotJournalName = ref.renameAttribute("tertiary-title", JOURNAL_NAME_ANNOTATION_TYPE);
			if (!gotJournalName)
				gotJournalName = ref.renameAttribute("secondary-title", JOURNAL_NAME_ANNOTATION_TYPE);
			if (gotJournalName)
				titles.add(ref.getAttribute(JOURNAL_NAME_ANNOTATION_TYPE));
		}
		boolean gotVolumeTitle = false;
		if (ref.hasAttribute("secondary-title") && titles.add(ref.getAttribute("secondary-title")))
			gotVolumeTitle = (ref.renameAttribute("secondary-title", VOLUME_TITLE_ANNOTATION_TYPE) || gotVolumeTitle);
		if (ref.hasAttribute("tertiary-title") && titles.add(ref.getAttribute("tertiary-title")))
			gotVolumeTitle = (ref.renameAttribute("tertiary-title", VOLUME_TITLE_ANNOTATION_TYPE) || gotVolumeTitle);
		
		//	pagination
		boolean gotPagination = ref.renameAttribute("pages", PAGINATION_ANNOTATION_TYPE);
		if (!gotPagination)
			gotPagination = ref.renameAttribute("section", PAGINATION_ANNOTATION_TYPE);
		if (gotPagination)
			BibRefUtils.checkPaginationAndType(ref);
		
		//	URL, DOI & ISBN
		ref.renameAttribute("url", PUBLICATION_URL_ANNOTATION_TYPE);
		ref.renameAttribute("doi", PUBLICATION_DOI_ANNOTATION_TYPE);
		ref.renameAttribute("isbn", PUBLICATION_ISBN_ANNOTATION_TYPE);
		
		//	infere type if necessary
		if (type == null) {
			type = BibRefUtils.classify(ref);
			System.out.println("Type infered as " + type);
		}
		
		//	set type
		if (type != null)
			ref.setAttribute(PUBLICATION_TYPE_ATTRIBUTE, type);
	}
	
	private static Pattern numberPattern = Pattern.compile("[1-9][0-9]*+");
	private static Pattern bracketNumberPattern = Pattern.compile("\\([1-9][0-9]*+\\)");
	
	private static Properties typeMappings = new Properties();
	static {
		typeMappings.setProperty("Book", BOOK_REFERENCE_TYPE); // Whole book
		typeMappings.setProperty("Book Section", BOOK_CHAPTER_REFERENCE_TYPE); // Book chapter
		typeMappings.setProperty("Conference Proceedings", PROCEEDINGS_REFERENCE_TYPE); // Conference proceeding
		typeMappings.setProperty("Conference Paper", PROCEEDINGS_PAPER_REFERENCE_TYPE); // Conference paper
		typeMappings.setProperty("Edited Book", BOOK_REFERENCE_TYPE); // Edited Book
		typeMappings.setProperty("Electronic Article", JOURNAL_ARTICEL_REFERENCE_TYPE); // Electronic Article
		typeMappings.setProperty("Journal", JOURNAL_VOLUME_REFERENCE_TYPE); // Journal (full)
		typeMappings.setProperty("Journal Article", JOURNAL_ARTICEL_REFERENCE_TYPE); // Journal
		typeMappings.setProperty("Report", BOOK_REFERENCE_TYPE); // Report
		typeMappings.setProperty("Standard", BOOK_REFERENCE_TYPE); // Standard
		typeMappings.setProperty("Thesis", BOOK_REFERENCE_TYPE); // Thesis/Dissertation
	}
	
	private Grammar grammar = new StandardGrammar();
	private Parser parser = new Parser(grammar);
	
	public static void main(String[] args) throws Exception {
		EndNoteXmlParser exp = new EndNoteXmlParser();
		ParsedRefDataIterator prit = exp.streamParse(new InputStreamReader(new FileInputStream(new File("E:/Projektdaten/RefBank/DataImport/EndNote/ABLE-EndNote8.xml")), "UTF-8"));
		while (prit.hasNextRefData()) {
			ParsedRefData pr = prit.nextRefData();
			System.out.println(pr.toXML());
		}
	}
}