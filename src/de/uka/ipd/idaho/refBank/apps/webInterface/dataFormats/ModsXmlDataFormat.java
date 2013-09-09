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
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;

import javax.xml.transform.Transformer;

import de.uka.ipd.idaho.gamta.util.SgmlDocumentReader;
import de.uka.ipd.idaho.htmlXmlUtil.Parser;
import de.uka.ipd.idaho.htmlXmlUtil.TokenReceiver;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.XsltUtils;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.Grammar;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.StandardGrammar;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient.UploadString;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils;
import de.uka.ipd.idaho.refBank.apps.webInterface.RefDataFormat;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * Data format adapter for the MODS XML reference data format.
 * 
 * @author sautter
 */
public class ModsXmlDataFormat extends RefDataFormat {
	
	/**
	 * @param name
	 */
	public ModsXmlDataFormat() {
		super("MODS");
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.apps.webInterface.RefDataFormat#getLabel()
	 */
	public String getLabel() {
		return "MODS XML";
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.apps.webInterface.RefDataFormat#init()
	 */
	protected void init() {
		try {
			this.refStringBuilder = XsltUtils.getTransformer("ModsXmlDataFormat:ModsToString.xslt", this.dataProvider.getInputStream("ModsToString.xslt"));
		}
		catch (IOException ioe) {
			ioe.printStackTrace(System.out);
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.apps.webInterface.RefDataFormat#isOutputFormat()
	 */
	public boolean isOutputFormat() {
		return false;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.apps.webInterface.RefDataFormat#getOutputDescription()
	 */
	public String getOutputDescription() {
		return null;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.apps.webInterface.RefDataFormat#format(java.io.Reader, java.io.Writer)
	 */
	public void format(Reader in, Writer out) throws IOException {}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.apps.webInterface.RefDataFormat#isInputFormat()
	 */
	public boolean isInputFormat() {
		return true;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.apps.webInterface.RefDataFormat#isInputParseUserApproved()
	 */
	public boolean isInputParseUserApproved() {
		return true;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.apps.webInterface.RefDataFormat#getInputDescription()
	 */
	public String getInputDescription() {
		return null; // for now, we're fine with the generated description
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.apps.webInterface.RefDataFormat#getInputFileExtensions()
	 */
	public String[] getInputFileExtensions() {
		String[] ifes = {"xml"};
		return ifes;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.plugins.bibRefs.BibRefDataFormat#streamParse(java.io.Reader)
	 */
	public ParsedRefDataIterator streamParse(Reader in) throws IOException {
		return null; // we create the UploadStringIterator directly to keep the MODS format
	}
	
	private Grammar grammar = new StandardGrammar();
	private Parser parser = new Parser(grammar);
	
	private Transformer refStringBuilder;
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.apps.webInterface.RefDataFormat#streamParseRefs(java.io.Reader)
	 */
	public UploadStringIterator streamParseRefs(Reader in) throws IOException {
		final BufferedReader br = ((in instanceof BufferedReader) ? ((BufferedReader) in) : new BufferedReader(in));
		final Object lock = new Object();
		final String[] theNext = {null};
		final IOException[] theIoe = {null};
		
		//	set up token receiver
		final TokenReceiver refTr = new TokenReceiver() {
			private StringVector refTokens = new StringVector();
			private boolean nextIsRefType = false;
			private String refType = null;
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
					String type = grammar.getType(token).toLowerCase();
					if ("mods".equals(type) || type.endsWith(":mods")) {
						if (grammar.isEndTag(token)) {
							if (this.refTokens.size() != 0) {
								if (this.refType == null) {
									this.refType = BibRefUtils.classify(BibRefUtils.modsXmlToRefData(SgmlDocumentReader.readDocument(new StringReader(this.refTokens.concatStrings("") + token))));
									this.refTokens.addElement("<mods:classification>");
									this.refTokens.addElement(this.refType);
									this.refTokens.addElement("</mods:classification>");
								}
								this.refTokens.addElement(token);
								theNext[0] = this.refTokens.concatStrings("");
							}
							this.refTokens.clear();
							this.nextIsRefType = false;
							this.refType = null;
						}
						else this.refTokens.addElement(token);
					}
					else if ("classification".equals(type) || type.endsWith(":classification")) {
						this.nextIsRefType = !grammar.isEndTag(token);
						this.refTokens.addElement(token);
					}
					else if (this.refTokens.size() != 0)
						this.refTokens.addElement(token);
				}
				else if (this.refTokens.size() != 0) {
					if (this.nextIsRefType) {
						this.refType = token.trim();
						this.refTokens.addElement(token.toLowerCase());
					}
					else this.refTokens.addElement(token);
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
		return new UploadStringIterator() {
			private boolean inEnd = false;
			int usTotal = 0;
			int usValid = 0;
			UploadString next;
			ArrayList errors = new ArrayList(4);
			public int getValidCount() {
				return this.usValid;
			}
			public int getErrorCount() {
				return this.errors.size();
			}
			public UploadStringError[] getErrors() {
				return ((UploadStringError[]) this.errors.toArray(new UploadStringError[this.errors.size()]));
			}
			public int getTotalCount() {
				return this.usTotal;
			}
			public int estimateRemaining() {
				return -1;
			}
			public UploadString nextUploadString() throws IOException {
				UploadString us = this.next;
				this.next = null;
				return us;
			}
			public boolean hasNextUploadString() throws IOException {
				if (this.next != null)
					return true;
				
				//	we have another reference
				if (theNext[0] != null) {
					this.usTotal++;
					String refString = BibRefUtils.transform(refStringBuilder, theNext[0]);
					if (refString == null) {
						this.errors.add(new UploadStringError("Could not generate reference string", theNext[0]));
						theNext[0] = null;
						return this.hasNextUploadString();
					}
					else if (refString.startsWith("ERROR:")) {
						this.errors.add(new UploadStringError(refString.substring("ERROR:".length()), theNext[0]));
						theNext[0] = null;
						return this.hasNextUploadString();
					}
					else {
						this.next = new UploadString(grammar.unescape(refString), theNext[0]);
						theNext[0] = null;
						this.usValid++;
						return true;
					}
				}
				
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
				
				//	we have another reference
				if (theNext[0] != null)
					return this.hasNextUploadString();
				
				//	pass on exception from parser
				if (theIoe[0] != null)
					throw theIoe[0];
				
				//	nothing more to come
				this.inEnd = true;
				return false;
			}
		};
	}
}
