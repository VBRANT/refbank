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
 * Parser for (files of) references represented in the EndNote data format.
 * 
 * @author sautter
 */
public class EndNoteParser  extends BibRefDataParser {
	public EndNoteParser() {
		super("EndNote");
	}
	public String getLabel() {
		return "EndNote";
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
					System.out.println(line);
					
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
					if (!line.startsWith("%") || !tagMappings.containsKey(line.substring(1, 2))) {
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
					String tag = line.substring(1, 2);
					
					//	start of new record
					if ("0".equals(tag)) {
						ref = new ParsedRefData();
						ref.addAttribute(PUBLICATION_TYPE_ATTRIBUTE, line.substring(3).trim());
						refSource.addElement(line);
//						System.out.println(" ==> starting reference of type " + line.substring(3).trim());
						continue;
					}
					
					//	end of record
					if ("~".equals(tag)) {
						
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
					value = new StringBuffer((line.length() < 3) ? "" : line.substring(3));
				}
				
				//	if we get here, there is no more data
				this.inEnd = true;
				br.close();
				return false;
			}
			private void unifyAttributes(RefData ref) {
				
				//	get type (helps determining required fields)
				String type = (ref.hasAttribute(PUBLICATION_TYPE_ATTRIBUTE) ? ref.getAttribute(PUBLICATION_TYPE_ATTRIBUTE) : "");
				type = type.trim();
				type = typeMappings.getProperty(type.toUpperCase(), type);
				ref.setAttribute(PUBLICATION_TYPE_ATTRIBUTE, type);
				
				//	author & editor
				boolean gotAuthor = ref.renameAttribute("A", AUTHOR_ANNOTATION_TYPE);
				if (!gotAuthor)
					gotAuthor = ref.renameAttribute("E", AUTHOR_ANNOTATION_TYPE);
				if (!gotAuthor)
					gotAuthor = ref.renameAttribute("Y", AUTHOR_ANNOTATION_TYPE);
				if (!gotAuthor)
					gotAuthor = ref.renameAttribute("?", AUTHOR_ANNOTATION_TYPE);
				boolean gotEditor = ref.renameAttribute("?", EDITOR_ANNOTATION_TYPE);
				if (!gotEditor)
					gotEditor = ref.renameAttribute("Y", EDITOR_ANNOTATION_TYPE);
				if (!gotEditor)
					gotEditor = ref.renameAttribute("E", EDITOR_ANNOTATION_TYPE);
				ref.renameAttribute("E", AUTHOR_ANNOTATION_TYPE);
				ref.renameAttribute("Y", AUTHOR_ANNOTATION_TYPE);
				
				//	year
				boolean gotYear = ref.renameAttribute("D", YEAR_ANNOTATION_TYPE);
				if (!gotYear)
					gotYear = ref.renameAttribute("8", YEAR_ANNOTATION_TYPE);
				
				//	volume, issue & numero
				boolean gotVolume = ref.renameAttribute("V", VOLUME_DESIGNATOR_ANNOTATION_TYPE);
				if (!gotVolume)
					gotVolume = ref.renameAttribute("N", VOLUME_DESIGNATOR_ANNOTATION_TYPE);
				else ref.renameAttribute("N", ISSUE_DESIGNATOR_ANNOTATION_TYPE);
				if (ref.hasAttribute(ISSUE_DESIGNATOR_ANNOTATION_TYPE)) {
					String pd = ref.getAttribute(ISSUE_DESIGNATOR_ANNOTATION_TYPE);
					if (pd.matches("[0-9]+\\s*\\([0-9]+\\)")) {
						String[] pds = pd.split("[^0-9]+");
						ref.setAttribute(ISSUE_DESIGNATOR_ANNOTATION_TYPE, pds[0]);
						ref.setAttribute(NUMERO_DESIGNATOR_ANNOTATION_TYPE, pds[1]);
					}
				}
				if (ref.hasAttribute(VOLUME_DESIGNATOR_ANNOTATION_TYPE)) {
					String pd = ref.getAttribute(VOLUME_DESIGNATOR_ANNOTATION_TYPE);
					if (pd.matches("[0-9]+\\s*\\([0-9]+\\)")) {
						String[] pds = pd.split("[^0-9]+");
						ref.setAttribute(VOLUME_DESIGNATOR_ANNOTATION_TYPE, pds[0]);
						ref.setAttribute(ISSUE_DESIGNATOR_ANNOTATION_TYPE, pds[1]);
					}
				}
				
				//	title, volume title & journal name // T J S Q B !
				HashSet titles = new HashSet();
				boolean gotTitle = ref.renameAttribute("T", TITLE_ANNOTATION_TYPE);
				if (!gotTitle)
					gotTitle = ref.renameAttribute("S", TITLE_ANNOTATION_TYPE);
				if (!gotTitle)
					gotTitle = ref.renameAttribute("Q", TITLE_ANNOTATION_TYPE);
				if (!gotTitle)
					gotTitle = ref.renameAttribute("B", TITLE_ANNOTATION_TYPE);
				if (!gotTitle)
					gotTitle = ref.renameAttribute("!", TITLE_ANNOTATION_TYPE);
				if (!gotTitle)
					gotTitle = ref.renameAttribute("J", TITLE_ANNOTATION_TYPE);
				if (gotTitle)
					titles.add(ref.getAttribute(TITLE_ANNOTATION_TYPE));
				boolean gotJournalName = false;
				if (type.startsWith("journal")) {
					gotJournalName = ref.renameAttribute("J", JOURNAL_NAME_ANNOTATION_TYPE);
					if (!gotJournalName)
						gotJournalName = ref.renameAttribute("S", JOURNAL_NAME_ANNOTATION_TYPE);
					if (!gotJournalName)
						gotJournalName = ref.renameAttribute("Q", JOURNAL_NAME_ANNOTATION_TYPE);
					if (!gotJournalName)
						gotJournalName = ref.renameAttribute("B", JOURNAL_NAME_ANNOTATION_TYPE);
					if (gotJournalName)
						titles.add(ref.getAttribute(JOURNAL_NAME_ANNOTATION_TYPE));
				}
				boolean gotVolumeTitle = false;
				if (ref.hasAttribute("B") && titles.add(ref.getAttribute("B")))
					gotVolumeTitle = (ref.renameAttribute("B", VOLUME_TITLE_ANNOTATION_TYPE) || gotVolumeTitle);
				if (ref.hasAttribute("S") && titles.add(ref.getAttribute("S")))
					gotVolumeTitle = (ref.renameAttribute("S", VOLUME_TITLE_ANNOTATION_TYPE) || gotVolumeTitle);
				
				//	pagination
				if (ref.renameAttribute("P", PAGINATION_ANNOTATION_TYPE))
					BibRefUtils.checkPaginationAndType(ref);
				
				//	publisher name & location
				ref.renameAttribute("I", PUBLISHER_ANNOTATION_TYPE);
				ref.renameAttribute("C", LOCATION_ANNOTATION_TYPE);
				
				//	URL
				ref.renameAttribute("U", PUBLICATION_URL_ANNOTATION_TYPE);
				ref.renameAttribute(">", PUBLICATION_URL_ANNOTATION_TYPE);
				
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
//		StringReader sr = new StringReader("%0 Journal Article\n%J Novon\n%D 2008\n%T A new species of Parodia (Cactaceae, Notocacteae) from Rio Grande do Sul, Brasil\n%A Machado, M. C.\n%A Nyffeler, R.\n%A Eggli, U.\n%A Larocca e Silva, J. F.\n%K Brasil\n%K Cactaceae\n%K campos\n%K IUCN Red List\n%K Notocacteae\n%K pampas\n%K Parodia\n%K Rio Grande do Sul\n%P 214-218\n%V 18\n%8 22 May 2008\n%~");
//		EndNoteParser rp = new EndNoteParser();
//		ParsedRefDataIterator rdit = rp.streamParse(sr);
//		while (rdit.hasNextRefData()) {
//			ParsedRefData rd = rdit.nextRefData();
//			System.out.println(rd.toXML());
//		}
//	}
	
	private static Properties tagMappings = new Properties();
	static {
		//	TODOne extend this as further tags become known ... no chance without a comprehensive documentation, which does not seem to be available online
		tagMappings.setProperty("A", "author"); // Author
		tagMappings.setProperty("B", "secondaryTitle"); // Secondary Title (of a Book or Conference Name)
		tagMappings.setProperty("C", "placePublished"); // Place Published
		tagMappings.setProperty("D", "year"); // Year
		tagMappings.setProperty("E", "editor"); // Editor / Secondary Author
		tagMappings.setProperty("F", "label"); // Label
		tagMappings.setProperty("G", "language"); // Language
		tagMappings.setProperty("H", "translatedAuthor"); // Translated Author
		tagMappings.setProperty("I", "publisher"); // Publisher
		tagMappings.setProperty("J", "journalName"); // Secondary Title (Journal Name)
		tagMappings.setProperty("K", "keywords"); // Keywords
		tagMappings.setProperty("L", "callNumber"); // Call Number
		tagMappings.setProperty("M", "accessionNumber"); // Accession Number
		tagMappings.setProperty("N", "numberOrIssue"); // Number (Issue)
		tagMappings.setProperty("P", "pages"); // Pages
		tagMappings.setProperty("Q", "translatedTitle"); // Translated Title
		tagMappings.setProperty("R", "electronicResourceNumber"); // Electronic Resource Number
		tagMappings.setProperty("S", "tertiaryTitle"); // Tertiary Title
		tagMappings.setProperty("T", "title"); // Title
		tagMappings.setProperty("U", "url"); // URL
		tagMappings.setProperty("V", "volume"); // Volume
		tagMappings.setProperty("W", "databaseProvider"); // Database Provider
		tagMappings.setProperty("X", "abstract"); // Abstract
		tagMappings.setProperty("Y", "tertiaryAuthor"); // Tertiary Author
		tagMappings.setProperty("Z", "notes"); // Notes
		tagMappings.setProperty("0", "startRecord"); // Reference Type
		tagMappings.setProperty("1", "custom1"); // Custom 1
		tagMappings.setProperty("2", "custom2"); // Custom 2
		tagMappings.setProperty("3", "custom3"); // Custom 3
		tagMappings.setProperty("4", "custom4"); // Custom 4
		tagMappings.setProperty("6", "numberOfVolumes"); // Number of Volumes
		tagMappings.setProperty("7", "edition"); // Edition
		tagMappings.setProperty("8", "date"); // Date
		tagMappings.setProperty("9", "typeOfWork"); // Type of Work
		tagMappings.setProperty("?", "subsidiaryAuthor"); // Subsidiary Author
		tagMappings.setProperty("@", "isbn/issn"); // ISBN/ISSN
		tagMappings.setProperty("!", "shortTitle"); // Short Title
		tagMappings.setProperty("#", "custom5"); // Custom 5
		tagMappings.setProperty("$", "custom6"); // Custom 6
		tagMappings.setProperty("]", "custom7"); // Custom 7
		tagMappings.setProperty("&", "section"); // Section
		tagMappings.setProperty("(", "originalPublication"); // Original Publication
		tagMappings.setProperty(")", "reprintEdition"); // Reprint Edition
		tagMappings.setProperty("*", "reviewedItem"); // Reviewed Item
		tagMappings.setProperty("+", "authorAddress"); // Author Address
		tagMappings.setProperty("^", "caption"); // Caption
		tagMappings.setProperty(">", "linkToPdf"); // Link to PDF
		tagMappings.setProperty("<", "researchNotes"); // Research Notes
		tagMappings.setProperty("[", "accessDate"); // Access Date
		tagMappings.setProperty("=", "lastModifiedDate"); // Last Modified Date
		tagMappings.setProperty("~", "endRecord"); // Name of Database
	}
	private static Properties typeMappings = new Properties();
	static {
//		typeMappings.setProperty("Generic", "Generic"); // Generic
//		typeMappings.setProperty("Artwork", "Artwork"); // Artwork
//		typeMappings.setProperty("Audiovisual Material", "Audiovisual Material"); // Audiovisual Material
//		typeMappings.setProperty("Bill", "Bill"); // Bill
		typeMappings.setProperty("Book", BOOK_REFERENCE_TYPE); // Book
		typeMappings.setProperty("Book Section", BOOK_CHAPTER_REFERENCE_TYPE); // Book Section
//		typeMappings.setProperty("Case", "Case"); // Case
//		typeMappings.setProperty("Chart or Table", "Chart or Table"); // Chart or Table
//		typeMappings.setProperty("Classical Work", "Classical Work"); // Classical Work
//		typeMappings.setProperty("Computer Program", "Computer Program"); // Computer Program
		typeMappings.setProperty("Conference Paper", PROCEEDINGS_PAPER_REFERENCE_TYPE); // Conference Paper
		typeMappings.setProperty("Conference Proceedings", PROCEEDINGS_REFERENCE_TYPE); // Conference Proceedings
		typeMappings.setProperty("Edited Book", BOOK_REFERENCE_TYPE); // Edited Book
//		typeMappings.setProperty("Equation", "Equation"); // Equation
		typeMappings.setProperty("Electronic Article", JOURNAL_ARTICEL_REFERENCE_TYPE); // Electronic Article
		typeMappings.setProperty("Electronic Book", BOOK_REFERENCE_TYPE); // Electronic Book
//		typeMappings.setProperty("Electronic Source", "Electronic Source"); // Electronic Source
//		typeMappings.setProperty("Figure", "Figure"); // Figure
//		typeMappings.setProperty("Film or Broadcast", "Film or Broadcast"); // Film or Broadcast
//		typeMappings.setProperty("Government Document", "Government Document"); // Government Document
//		typeMappings.setProperty("Hearing", "Hearing"); // Hearing
		typeMappings.setProperty("Journal Article", JOURNAL_ARTICEL_REFERENCE_TYPE); // Journal Article
//		typeMappings.setProperty("Legal Rule/Regulation", "Legal Rule/Regulation"); // Legal Rule/Regulation
		typeMappings.setProperty("Magazine Article", JOURNAL_ARTICEL_REFERENCE_TYPE); // Magazine Article
		typeMappings.setProperty("Manuscript", BOOK_REFERENCE_TYPE); // Manuscript
//		typeMappings.setProperty("Map", "Map"); // Map
		typeMappings.setProperty("Newspaper Article", JOURNAL_ARTICEL_REFERENCE_TYPE); // Newspaper Article
		typeMappings.setProperty("Online Database", URL_REFERENCE_TYPE); // Online Database
		typeMappings.setProperty("Online Multimedia", URL_REFERENCE_TYPE); // Online Multimedia
//		typeMappings.setProperty("Patent", "Patent"); // Patent
//		typeMappings.setProperty("Personal Communication", "Personal Communication"); // Personal Communication
		typeMappings.setProperty("Report", BOOK_REFERENCE_TYPE); // Report
//		typeMappings.setProperty("Statute", "Statute"); // Statute
		typeMappings.setProperty("Thesis", BOOK_REFERENCE_TYPE); // Thesis
//		typeMappings.setProperty("Unpublished Work", "Unpublished Work"); // Unpublished Work
//		typeMappings.setProperty("Unused 1", "Unused 1"); // Unused 1
//		typeMappings.setProperty("Unused 2", "Unused 2"); // Unused 2
//		typeMappings.setProperty("Unused 3", "Unused 3"); // Unused 3
	}
}