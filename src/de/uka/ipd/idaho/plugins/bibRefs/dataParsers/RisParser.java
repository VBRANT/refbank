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
package de.uka.ipd.idaho.plugins.bibRefs.dataParsers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Properties;

import de.uka.ipd.idaho.plugins.bibRefs.BibRefDataParser;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils.RefData;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * Parser for (files of) references represented in the EndNote RIS data format.
 * 
 * @author sautter
 */
public class RisParser extends BibRefDataParser {
	public RisParser() {
		super("RIS");
	}
	public String getLabel() {
		return "EndNote / RIS2";
	}
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.plugins.bibRefs.BibRefDataFormat#streamParse(java.io.Reader)
	 */
	public ParsedRefDataIterator streamParse(Reader in) throws IOException {
		final BufferedReader br = ((in instanceof BufferedReader) ? ((BufferedReader) in) : new BufferedReader(in));
		return new ParsedRefDataIterator() {
			private ParsedRefData next = null;
			private boolean inEnd = false;
			public int estimateRemaining() {
				return -1;
			}
			public ParsedRefData nextRefData() throws IOException {
				ParsedRefData rd = this.next;
				this.next = null;
				return rd;
			}
			public boolean hasNextRefData() throws IOException {
				if (this.next != null)
					return true;
				if (this.inEnd)
					return false;
				return this.readNext();
			}
			private boolean readNext() throws IOException {
				ParsedRefData ref = null;
				StringVector refSource = new StringVector();
				String attribute = null;
				StringBuffer value = null;
				String line;
				while ((line = br.readLine()) != null) {
					
					//	keep record source unchanged
					if (ref != null)
						refSource.addElement(line);
					
					//	adjust line
					line = line.trim();
//					System.out.println("Line is " + line);
					
					//	check if line continues open attribute
					if (line.length() == 0) {
//						System.out.println(" ==> skipping empty line");
						continue;
					}
					if (!line.startsWith("  -", 2) || !tagMappings.containsKey(line.substring(0, 2))) {
						if (value != null) {
							value.append(' ');
							value.append(line);
						}
//						System.out.println(" ==> no tag, appended to open attribute");
						continue;
					}
					
					//	store old attribute (if any)
					if (value != null) {
						if (ref != null)
							ref.addAttribute(attribute, value.toString());
						attribute = null;
						value = null;
					}
					
					//	get line tag
					String tag = line.substring(0, 2);
					
					//	start of new record
					if ("TY".equals(tag)) {
						ref = new ParsedRefData();
						ref.addAttribute(PUBLICATION_TYPE_ATTRIBUTE, line.substring(6).trim());
						refSource.addElement(line);
//						System.out.println(" ==> starting reference of type " + line.substring(6).trim());
						continue;
					}
					
					//	end of record
					if ("ER".equals(tag)) {
						
						//	nothing found, clean up and keep trying
						if (ref == null) {
							refSource.clear();
//							System.out.println(" ==> end of empty reference");
							continue;
						}
						
						//	store reference
						ref.setSource(refSource.concatStrings("\n"));
						this.unifyAttributes(ref);
						this.next = ref;
						
						//	indicate success
						return true;
					}
					
					//	start of attribute
					attribute = tag;
//					value = new StringBuffer(line.substring(6));
					value = new StringBuffer(line.substring(2 + "  -".length()).trim());
				}
				
				//	if we get here, there is no more data
				this.inEnd = true;
				br.close();
				return false;
			}
			private void unifyAttributes(RefData ref) {
				
				//	get type (helps determining required fields)
				String type = (ref.hasAttribute(PUBLICATION_TYPE_ATTRIBUTE) ? ref.getAttribute(PUBLICATION_TYPE_ATTRIBUTE) : "");
				type = typeMappings.getProperty(type.toUpperCase(), type);
				ref.setAttribute(PUBLICATION_TYPE_ATTRIBUTE, type);
				
				//	author & editor
				boolean gotAuthor = ref.renameAttribute("AU", AUTHOR_ANNOTATION_TYPE);
				if (!gotAuthor)
					gotAuthor = ref.renameAttribute("A1", AUTHOR_ANNOTATION_TYPE);
				if (!gotAuthor)
					gotAuthor = ref.renameAttribute("A2", AUTHOR_ANNOTATION_TYPE);
				if (!gotAuthor)
					gotAuthor = ref.renameAttribute("A3", AUTHOR_ANNOTATION_TYPE);
				if (!gotAuthor)
					gotAuthor = ref.renameAttribute("A4", AUTHOR_ANNOTATION_TYPE);
				boolean gotEditor = ref.renameAttribute("A4", EDITOR_ANNOTATION_TYPE);
				if (!gotEditor)
					gotEditor = ref.renameAttribute("A3", EDITOR_ANNOTATION_TYPE);
				if (!gotEditor)
					gotEditor = ref.renameAttribute("A2", EDITOR_ANNOTATION_TYPE);
				ref.renameAttribute("A2", AUTHOR_ANNOTATION_TYPE);
				ref.renameAttribute("A3", AUTHOR_ANNOTATION_TYPE);
				
				//	year
				boolean gotYear = ref.renameAttribute("PY", YEAR_ANNOTATION_TYPE);
				if (!gotYear)
					gotYear = ref.renameAttribute("Y1", YEAR_ANNOTATION_TYPE);
				if (!gotYear)
					gotYear = ref.renameAttribute("Y2", YEAR_ANNOTATION_TYPE);
				if (!gotYear)
					gotYear = ref.renameAttribute("DA", YEAR_ANNOTATION_TYPE);
				
				//	volume, issue & numero
				ref.renameAttribute("VL", VOLUME_DESIGNATOR_ANNOTATION_TYPE);
				ref.renameAttribute("M1", NUMERO_DESIGNATOR_ANNOTATION_TYPE);
				ref.renameAttribute("IS", ISSUE_DESIGNATOR_ANNOTATION_TYPE);
				
				//	title, volume title & journal name
				HashSet titles = new HashSet();
				boolean gotTitle = ref.renameAttribute("TI", TITLE_ANNOTATION_TYPE);
				if (!gotTitle)
					gotTitle = ref.renameAttribute("T1", TITLE_ANNOTATION_TYPE);
				if (!gotTitle)
					gotTitle = ref.renameAttribute("T2", TITLE_ANNOTATION_TYPE);
				if (!gotTitle)
					gotTitle = ref.renameAttribute("T3", TITLE_ANNOTATION_TYPE);
				if (!gotTitle)
					gotTitle = ref.renameAttribute("TT", TITLE_ANNOTATION_TYPE);
				if (gotTitle)
					titles.add(ref.getAttribute(TITLE_ANNOTATION_TYPE));
				boolean gotJournalName = false;
				if (type.startsWith("journal")) {
					gotJournalName = ref.renameAttribute("JO", JOURNAL_NAME_ANNOTATION_TYPE);
					if (!gotJournalName)
						gotJournalName = ref.renameAttribute("JF", JOURNAL_NAME_ANNOTATION_TYPE);
					if (!gotJournalName)
						gotJournalName = ref.renameAttribute("J2", JOURNAL_NAME_ANNOTATION_TYPE);
					if (!gotJournalName)
						gotJournalName = ref.renameAttribute("JA", JOURNAL_NAME_ANNOTATION_TYPE);
					if (!gotJournalName)
						gotJournalName = ref.renameAttribute("T3", JOURNAL_NAME_ANNOTATION_TYPE);
					if (!gotJournalName)
						gotJournalName = ref.renameAttribute("T2", JOURNAL_NAME_ANNOTATION_TYPE);
					if (gotJournalName)
						titles.add(ref.getAttribute(JOURNAL_NAME_ANNOTATION_TYPE));
				}
				boolean gotVolumeTitle = false;
				if (ref.hasAttribute("T2") && titles.add(ref.getAttribute("T2")))
					gotVolumeTitle = (ref.renameAttribute("T2", VOLUME_TITLE_ANNOTATION_TYPE) || gotVolumeTitle);
				if (ref.hasAttribute("T3") && titles.add(ref.getAttribute("T3")))
					gotVolumeTitle = (ref.renameAttribute("T3", VOLUME_TITLE_ANNOTATION_TYPE) || gotVolumeTitle);
				
				//	pagination TODO use 'section' fallback
				String fp = ref.getAttribute("SP");
				String lp = ref.getAttribute("EP");
				if (fp != null) {
					String pages = (fp + (((lp == null) || fp.equals(lp)) ? "" : ("-" + lp)));
					ref.setAttribute(PAGINATION_ANNOTATION_TYPE, pages);
					BibRefUtils.checkPaginationAndType(ref);
				}
				
				//	publisher name & location
				ref.renameAttribute("PB", PUBLISHER_ANNOTATION_TYPE);
				ref.renameAttribute("CY", LOCATION_ANNOTATION_TYPE);
				
				//	URL, DOI & ISBN
				ref.renameAttribute("UR", PUBLICATION_URL_ANNOTATION_TYPE);
				ref.renameAttribute("DO", PUBLICATION_DOI_ANNOTATION_TYPE);
				ref.renameAttribute("SN", PUBLICATION_ISBN_ANNOTATION_TYPE);
				
				//	clean up the rest
				String[] ans = ref.getAttributeNames();
				for (int n = 0; n < ans.length; n++) {
					if (ans[n].length() < 3)
						ref.removeAttribute(ans[n]);
				}
			}
		};
	}
//	
//	public static void main(String[] args) throws Exception {
//		StringReader sr = new StringReader("TY  - JOUR\nJO  - Novon\nPY  - 2008\nTI  - A new species of Parodia (Cactaceae, Notocacteae) from Rio Grande do Sul, Brasil\nAU  - Machado, M. C.\nAU  - Nyffeler, R.\nAU  - Eggli, U.\nAU  - Larocca e Silva, J. F.\nSP  - 214\nEP  - 218\nVL  - 18\nER  - ");
//		RisParser rp = new RisParser();
//		ParsedRefDataIterator rdit = rp.streamParse(sr);
//		while (rdit.hasNextRefData()) {
//			ParsedRefData rd = rdit.nextRefData();
//			System.out.println(rd.toXML());
//		}
//	}
	
	private static Properties tagMappings = new Properties();
	static {
		//	TODO extend this as further tags become known ... no chance without a comprehensive documentation, which does not seem to be available online
		tagMappings.setProperty("A1", "author");// Author (each author on its own line preceded by the tag)
		tagMappings.setProperty("A2", "secondary-author");// Secondary Author (each author on its own line preceded by the tag)
		tagMappings.setProperty("A3", "tertiary-author");// Tertiary Author (each author on its own line preceded by the tag)
		tagMappings.setProperty("A4", "subsidiary-author");// Subsidiary Author (each author on its own line preceded by the tag)
		tagMappings.setProperty("AB", "abstract");// Abstract
		tagMappings.setProperty("AD", "authorAddress");// Author Address
		tagMappings.setProperty("AN", "accessionNr");// Accession Number
		tagMappings.setProperty("AU", "author");// Author (each author on its own line preceded by the tag)
		tagMappings.setProperty("C1", "custom");// Custom 1
		tagMappings.setProperty("C2", "custom");// Custom 2
		tagMappings.setProperty("C3", "custom");// Custom 3
		tagMappings.setProperty("C4", "custom");// Custom 4
		tagMappings.setProperty("C5", "custom");// Custom 5
		tagMappings.setProperty("C6", "custom");// Custom 6
		tagMappings.setProperty("C7", "custom");// Custom 7
		tagMappings.setProperty("C8", "custom");// Custom 8
		tagMappings.setProperty("CA", "caption");// Caption
		tagMappings.setProperty("CN", "callNr");// Call Number
		tagMappings.setProperty("CY", "pub-location");// Place Published
		tagMappings.setProperty("DA", "date");// Date
		tagMappings.setProperty("DB", "dbName");// Name of Database
		tagMappings.setProperty("DO", "doi");// DOI
		tagMappings.setProperty("DP", "dbProvider");// Database Provider
		tagMappings.setProperty("EP", "lastPage");// End Page
		tagMappings.setProperty("ET", "edition");// Edition
		tagMappings.setProperty("ER", "end-record");
		tagMappings.setProperty("IS", "number");// Number 
		tagMappings.setProperty("JA", "journal");// Journal Name
		tagMappings.setProperty("JF", "journal");// Journal Name
		tagMappings.setProperty("JO", "journal");// Journal Name
		tagMappings.setProperty("J2", "titleAlt");// Alternate Title (this field is used for the abbreviated title of a book or journal name)
		tagMappings.setProperty("KW", "keywords");// Keywords (keywords should be entered each on its own line preceded by the tag)
		tagMappings.setProperty("L1", "file");// File Attachments (this is a link to a local file on the users system not a URL link)
		tagMappings.setProperty("L4", "figure");// Figure (this is also meant to be a link to a local file on the users's system and not a URL link)
		tagMappings.setProperty("LA", "language");// Language
		tagMappings.setProperty("LB", "label");// Label
		tagMappings.setProperty("M1", "number");// Number
		tagMappings.setProperty("M3", "type");// Type of Work
		tagMappings.setProperty("N1", "notes");// Notes
		tagMappings.setProperty("NV", "volumeCount");// Number of Volumes
		tagMappings.setProperty("OP", "isOriginalPublication");// Original Publication
		tagMappings.setProperty("PB", "publisher");// Publisher
		tagMappings.setProperty("PY", "year");// Year
		tagMappings.setProperty("RI", "reviewedItem");// Reviewed Item
		tagMappings.setProperty("RN", "researchNotes");// Research Notes
		tagMappings.setProperty("RP", "reprintEdition");// Reprint Edition
		tagMappings.setProperty("SE", "section");// Section
		tagMappings.setProperty("SN", "isbnIssn");// ISBN/ISSN
		tagMappings.setProperty("SP", "firstPage");// Start Page
		tagMappings.setProperty("ST", "short-title");// Short Title
		tagMappings.setProperty("T1", "title");// Title
		tagMappings.setProperty("T2", "secondary-title");// Secondary Title
		tagMappings.setProperty("T3", "tertiary-title");// Tertiary Title
		tagMappings.setProperty("TA", "translated-author");// Translated Author
		tagMappings.setProperty("TI", "title");// Title
		tagMappings.setProperty("TT", "translated-title");// Translated Title
		tagMappings.setProperty("TY", "start-record");
		tagMappings.setProperty("U1", "userDef");// URL
		tagMappings.setProperty("U2", "userDef");// URL
		tagMappings.setProperty("U3", "userDef");// URL
		tagMappings.setProperty("U4", "userDef");// URL
		tagMappings.setProperty("U5", "userDef");// URL
		tagMappings.setProperty("U6", "userDef");// URL
		tagMappings.setProperty("U7", "userDef");// URL
		tagMappings.setProperty("U8", "userDef");// URL
		tagMappings.setProperty("U9", "userDef");// URL
		tagMappings.setProperty("UR", "url");// URL
		tagMappings.setProperty("VL", "volume");// Volume
		tagMappings.setProperty("Y1", "year");// Access Date
		tagMappings.setProperty("Y2", "accessDate");// Access Date
	}
	
	private static Properties typeMappings = new Properties();
	static {
		typeMappings.setProperty("ABST", PROCEEDINGS_PAPER_REFERENCE_TYPE); // Abstract
//		typeMappings.setProperty("ADVS", "Audiovisualmaterial"); // Audiovisualmaterial
//		typeMappings.setProperty("AGGR", "AggregatedDatabase"); // AggregatedDatabase
//		typeMappings.setProperty("ANCIENT", "AncientText"); // AncientText
//		typeMappings.setProperty("ART", "ArtWork"); // ArtWork
//		typeMappings.setProperty("BILL", "Bill"); // Bill
//		typeMappings.setProperty("BLOG", "Blog"); // Blog
		typeMappings.setProperty("BOOK", BOOK_REFERENCE_TYPE); // Wholebook
//		typeMappings.setProperty("CASE", "Case"); // Case
		typeMappings.setProperty("CHAP", BOOK_CHAPTER_REFERENCE_TYPE); // Bookchapter
//		typeMappings.setProperty("CHART", "Chart"); // Chart
//		typeMappings.setProperty("CLSWK", "ClassicalWork"); // ClassicalWork
//		typeMappings.setProperty("COMP", "Computerprogram"); // Computerprogram
		typeMappings.setProperty("CONF", PROCEEDINGS_REFERENCE_TYPE); // Conferenceproceeding
		typeMappings.setProperty("CPAPER", PROCEEDINGS_PAPER_REFERENCE_TYPE); // Conferencepaper
		typeMappings.setProperty("CTLG", BOOK_REFERENCE_TYPE); // Catalog
//		typeMappings.setProperty("DATA", "Datafile"); // Datafile
//		typeMappings.setProperty("DBASE", "OnlineDatabase"); // OnlineDatabase
		typeMappings.setProperty("DICT", BOOK_REFERENCE_TYPE); // Dictionary
		typeMappings.setProperty("EBOOK", BOOK_REFERENCE_TYPE); // ElectronicBook
		typeMappings.setProperty("ECHAP", BOOK_CHAPTER_REFERENCE_TYPE); // ElectronicBookSection
		typeMappings.setProperty("EDBOOK", BOOK_REFERENCE_TYPE); // EditedBook
		typeMappings.setProperty("EJOUR", JOURNAL_ARTICEL_REFERENCE_TYPE); // ElectronicArticle
//		typeMappings.setProperty("ELEC", "WebPage"); // WebPage
		typeMappings.setProperty("ENCYC", BOOK_REFERENCE_TYPE); // Encyclopedia
//		typeMappings.setProperty("EQUA", "Equation"); // Equation
//		typeMappings.setProperty("FIGURE", "Figure"); // Figure
//		typeMappings.setProperty("GEN", "Generic"); // Generic
//		typeMappings.setProperty("GOVDOC", "GovernmentDocument"); // GovernmentDocument
//		typeMappings.setProperty("GRANT", "Grant"); // Grant
//		typeMappings.setProperty("HEAR", "Hearing"); // Hearing
//		typeMappings.setProperty("ICOMM", "InternetCommunication"); // InternetCommunication
//		typeMappings.setProperty("INPR", "InPress"); // InPress
		typeMappings.setProperty("JFULL", JOURNAL_VOLUME_REFERENCE_TYPE); // Journal(full)
		typeMappings.setProperty("JOUR", JOURNAL_ARTICEL_REFERENCE_TYPE); // Journal
//		typeMappings.setProperty("LEGAL", "LegalRuleorRegulation"); // LegalRuleorRegulation
		typeMappings.setProperty("MANSCPT", BOOK_REFERENCE_TYPE); // Manuscript
//		typeMappings.setProperty("MAP", "Map"); // Map
		typeMappings.setProperty("MGZN", JOURNAL_ARTICEL_REFERENCE_TYPE); // Magazinearticle
//		typeMappings.setProperty("MPCT", "Motionpicture"); // Motionpicture
//		typeMappings.setProperty("MULTI", "OnlineMultimedia"); // OnlineMultimedia
//		typeMappings.setProperty("MUSIC", "Musicscore"); // Musicscore
//		typeMappings.setProperty("NEWS", "Newspaper"); // Newspaper
//		typeMappings.setProperty("PAMP", "Pamphlet"); // Pamphlet
//		typeMappings.setProperty("PAT", "Patent"); // Patent
//		typeMappings.setProperty("PCOMM", "Personalcommunication"); // Personalcommunication
		typeMappings.setProperty("RPRT", BOOK_REFERENCE_TYPE); // Report
		typeMappings.setProperty("SER", BOOK_REFERENCE_TYPE); // Serialpublication
//		typeMappings.setProperty("SLIDE", "Slide"); // Slide
//		typeMappings.setProperty("SOUND", "Soundrecording"); // Soundrecording
		typeMappings.setProperty("STAND", BOOK_REFERENCE_TYPE); // Standard
//		typeMappings.setProperty("STAT", "Statute"); // Statute
		typeMappings.setProperty("THES", BOOK_REFERENCE_TYPE); // Thesis/Dissertation
//		typeMappings.setProperty("UNPB", "Unpublishedwork"); // Unpublishedwork
//		typeMappings.setProperty("VIDEO", "Videorecording"); // Videorecording
	}
}