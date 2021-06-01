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
package de.uka.ipd.idaho.refBank.apps.webInterface.dataFormats;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Properties;

import de.uka.ipd.idaho.gamta.Annotation;
import de.uka.ipd.idaho.gamta.DocumentRoot;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.Token;
import de.uka.ipd.idaho.gamta.TokenSequence;
import de.uka.ipd.idaho.gamta.util.Analyzer;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils.RefData;
import de.uka.ipd.idaho.plugins.bibRefs.refParse.RefParse;
import de.uka.ipd.idaho.plugins.bibRefs.refParse.RefParseAutomatic;
import de.uka.ipd.idaho.refBank.apps.webInterface.RefDataFormat;

/**
 * @author sautter
 *
 */
public class ParsingPlainTextDataFormat extends RefDataFormat {
	public ParsingPlainTextDataFormat() {
		super("ListTxt");
	}
	public String getLabel() {
		return "Plain Text Reference List";
	}
	public String getOutputDescription() {
		return null;
	}
	public boolean isOutputFormat() {
		return false;
	}
	public void format(Reader in, Writer out) throws IOException {
		// we don't do output for now, as the current XSLT handles that better
	}
	
	private Analyzer refParse;
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.apps.webInterface.RefDataFormat#init()
	 */
	protected void init() {
		this.refParse = new RefParseAutomatic();
		this.refParse.setDataProvider(this.dataProvider);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.apps.webInterface.RefDataFormat#isInputFormat()
	 */
	public boolean isInputFormat() {
		return true;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.apps.webInterface.RefDataFormat#getInputFileExtensions()
	 */
	public String[] getInputFileExtensions() {
		String[] ifes = {"txt"};
		return ifes;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.apps.webInterface.RefDataFormat#isInputParseUserApproved()
	 */
	public boolean isInputParseUserApproved() {
		return false;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.apps.webInterface.RefDataFormat#getInputDescription()
	 */
	public String getInputDescription() {
		//	TODO make sure this text is sufficiently generic for all possible use cases
		return ("Enter a bibliographic reference list in <b>plain text</b> format <b>(one reference per line)</b> in the text area below to upload them to <b>RefBank</b>");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.plugins.bibRefs.BibRefDataFormat#streamParse(java.io.Reader)
	 */
	public ParsedRefDataIterator streamParse(Reader in) throws IOException {
		BufferedReader br = ((in instanceof BufferedReader) ? ((BufferedReader) in) : new BufferedReader(in));
		
		//	read text line wise, annotating every line as paragraph and bibRef
		DocumentRoot refDoc = Gamta.newDocument(Gamta.newTokenSequence("", Gamta.INNER_PUNCTUATION_TOKENIZER));
		String ref;
		while ((ref = br.readLine()) != null) {
			ref = ref.trim();
			if (ref.length() == 0)
				continue;
			int refStart = refDoc.size();
			refDoc.addTokens(ref);
			if (refDoc.size() <= refStart)
				continue;
			refDoc.lastToken().setAttribute(Token.PARAGRAPH_END_ATTRIBUTE, Token.PARAGRAPH_END_ATTRIBUTE);
			refDoc.addAnnotation(MutableAnnotation.PARAGRAPH_TYPE, refStart, (refDoc.size() - refStart));
			refDoc.addAnnotation(RefParse.BIBLIOGRAPHIC_REFERENCE_TYPE, refStart, (refDoc.size() - refStart));
		}
		br.close();
		
		//	run RefParse
		if (this.refParse != null)
			this.refParse.process(refDoc, new Properties());
		
		//	return result
		final MutableAnnotation[] refs = refDoc.getMutableAnnotations(RefParse.BIBLIOGRAPHIC_REFERENCE_TYPE);
		return new ParsedRefDataIterator() {
			private int r = 0;
			private ParsedRefData next = null;
			public int estimateRemaining() {
				return (refs.length - this.r);
			}
			public ParsedRefData nextRefData() throws IOException {
				ParsedRefData rd = this.next;
				this.next = null;
				return rd;
			}
			public boolean hasNextRefData() throws IOException {
				if (this.next != null)
					return true;
				if (this.r >= refs.length)
					return false;
				MutableAnnotation refAnnot = refs[this.r++];
//				String refString = TokenSequenceUtils.concatTokens(refAnnot, true, true);
				String refString = this.concatTokens(refAnnot);
				ParsedRefData ref = new ParsedRefData();
				ref.setSource(refString);
				ref.addAttribute("refString", refString);
				this.setAttributes(ref, refAnnot, RefParse.AUTHOR_ANNOTATION_TYPE);
				this.setAttributes(ref, refAnnot, RefParse.EDITOR_ANNOTATION_TYPE);
				this.setAttributes(ref, refAnnot, RefParse.TITLE_ANNOTATION_TYPE);
				this.setAttributes(ref, refAnnot, RefParse.VOLUME_TITLE_ANNOTATION_TYPE);
				this.setAttributes(ref, refAnnot, RefParse.JOURNAL_NAME_OR_PUBLISHER_ANNOTATION_TYPE);
				this.setAttributes(ref, refAnnot, RefParse.SERIES_IN_JOURNAL_ANNOTATION_TYPE);
				this.setAttributes(ref, refAnnot, RefParse.YEAR_ANNOTATION_TYPE);
				this.setAttributes(ref, refAnnot, RefParse.PAGINATION_ANNOTATION_TYPE);
				this.setAttributes(ref, refAnnot, RefParse.PART_DESIGNATOR_ANNOTATION_TYPE);
				this.setAttributes(ref, refAnnot, RefParse.PUBLICATION_DOI_ANNOTATION_TYPE);
				this.setAttributes(ref, refAnnot, RefParse.PUBLICATION_URL_ANNOTATION_TYPE);
				BibRefUtils.checkPaginationAndType(ref);
				String type = ((String) refAnnot.getAttribute(RefParse.TYPE_ATTRIBUTE));
				if (type != null) {
					ref.setAttribute(PUBLICATION_TYPE_ATTRIBUTE, type);
					if (type.toLowerCase().startsWith("journal"))
						ref.renameAttribute(RefParse.JOURNAL_NAME_OR_PUBLISHER_ANNOTATION_TYPE, JOURNAL_NAME_ANNOTATION_TYPE);
					else ref.renameAttribute(RefParse.JOURNAL_NAME_OR_PUBLISHER_ANNOTATION_TYPE, PUBLISHER_ANNOTATION_TYPE);
				}
				ref.renameAttribute(RefParse.PUBLICATION_DOI_ANNOTATION_TYPE, PUBLICATION_DOI_ANNOTATION_TYPE);
				ref.renameAttribute(RefParse.PUBLICATION_URL_ANNOTATION_TYPE, PUBLICATION_URL_ANNOTATION_TYPE);
				this.next = ref;
				return this.hasNextRefData();
			}
			private void setAttributes(RefData refData, MutableAnnotation refAnnot, String attributeName) {
				Annotation[] annots = refAnnot.getAnnotations(attributeName);
				for (int a = 0; a < annots.length; a++)
					refData.addAttribute(attributeName, this.concatTokens(annots[a]));
			}
			private String concatTokens(TokenSequence tokens) {
				if (tokens.size() == 0) return "";
				
				String lastValue = tokens.valueAt(0);
				StringBuffer result = new StringBuffer(lastValue);
				
				for (int t = 1; t < tokens.size(); t++) {
					String value = tokens.valueAt(t);
					if (Gamta.insertSpace(lastValue, value) && (tokens.getWhitespaceAfter(t - 1).length() != 0))
						result.append(" ");
					result.append(value);
					lastValue = value;
				}

				return result.toString();
			}
		};
	}
}
