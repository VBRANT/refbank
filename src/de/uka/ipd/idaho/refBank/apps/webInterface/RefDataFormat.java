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
package de.uka.ipd.idaho.refBank.apps.webInterface;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;

import de.uka.ipd.idaho.gamta.util.AnalyzerDataProvider;
import de.uka.ipd.idaho.gamta.util.AnalyzerDataProviderFileBased;
import de.uka.ipd.idaho.gamta.util.GamtaClassLoader;
import de.uka.ipd.idaho.gamta.util.GamtaClassLoader.ComponentInitializer;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.Grammar;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.StandardGrammar;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient.UploadString;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefConstants;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefDataParser;
import de.uka.ipd.idaho.plugins.bibRefs.BibRefUtils;

/**
 * This class implements different data formats for bibliographic references,
 * both for output and upload. Its main use is in the RefBank web interface.
 * Centralized methods for creating plain string and parsed representations of
 * uploaded references are given. They are based on XSLT to maximize
 * customizability. The produceRefString() method uses the stylesheet
 * 'refString.xstl' from the reference format's data folder, the
 * produceRefParsed() method uses the stylesheet 'refParsed.xstl' from the same
 * folder.
 * 
 * @author sautter
 */
public abstract class RefDataFormat extends BibRefDataParser implements BibRefConstants {
	
	/**
	 * Load concrete implementations of this class from the JAR files a specific
	 * folder. For the same folder, the data formats are loaded only once, then
	 * they are shared between all code that invokes this method on the same
	 * folder from within the same JVM. The returned array itself is not shared,
	 * however, so invoking code can manipulate it as needed.
	 * @param folder the folder to load the data fromats from
	 * @return an array holding the data formats
	 */
	public static synchronized RefDataFormat[] getDataFormats(final File folder) {
		String cacheId = folder.getAbsolutePath();
		cacheId = cacheId.replaceAll("\\\\", "/");
		cacheId = cacheId.replaceAll("\\/\\.\\/", "/");
		cacheId = cacheId.replaceAll("\\/{2,}", "/");
		
		ArrayList dataFormats = ((ArrayList) dataFormatCache.get(cacheId));
		if (dataFormats != null)
			return ((RefDataFormat[]) dataFormats.toArray(new RefDataFormat[dataFormats.size()]));
		
		GamtaClassLoader.setThreadLocal(true);
		Object[] rawDataFormats = GamtaClassLoader.loadComponents(folder, RefDataFormat.class, new ComponentInitializer() {
			public void initialize(Object component, String componentJarName) throws Throwable {
				RefDataFormat rdf = ((RefDataFormat) component);
				if (rdf.isInputFormat() || rdf.isOutputFormat()) {
					File rdfData = new File(folder, (componentJarName.substring(0, (componentJarName.length() - ".jar".length())) + "Data"));
					if (!rdfData.exists())
						rdfData.mkdir();
					rdf.setDataProvider(new AnalyzerDataProviderFileBased(rdfData));
				}
				else throw new RuntimeException("Useless format, neither input nor output available.");
			}
		});
		
		dataFormats = new ArrayList();
		for (int d = 0; d < rawDataFormats.length; d++) {
			if (rawDataFormats[d] instanceof RefDataFormat)
				dataFormats.add((RefDataFormat) rawDataFormats[d]);
		}
		dataFormatCache.put(cacheId, dataFormats);
		
		return ((RefDataFormat[]) dataFormats.toArray(new RefDataFormat[dataFormats.size()]));
	}
	private static HashMap dataFormatCache = new HashMap();
	
	/**
	 * Convenience class for wrapping a reference data parser so it can act as a
	 * reference data format. This class mainly exists so reference data parsers
	 * do not have to be extended individually to fill that role. This class is
	 * strictly for input, i.e., for reading reference data.
	 * 
	 * @author sautter
	 */
	public static abstract class RefDataParserWrapper extends RefDataFormat {
		private BibRefDataParser parser;
		private String fileExtension;
		private boolean inputIsUserApproved;
		
		/**
		 * Constructor
		 * @param name a custom name for the data format
		 * @param parser the reference data parser to wrap
		 * @param fileExtension the file extensions of input files (separate
		 *            with spaces if multiple)
		 * @param inputIsUserApproved is the parse approved by a user?
		 */
		protected RefDataParserWrapper(String name, BibRefDataParser parser, String fileExtension, boolean inputIsUserApproved) {
			super(name);
			this.parser = parser;
			this.fileExtension = ((fileExtension == null) ? null : fileExtension.trim());
			this.inputIsUserApproved = inputIsUserApproved;
		}

		/**
		 * Constructor
		 * @param varName a custom name for the data format
		 * @param parser the reference data parser to wrap
		 * @param fileExtension the file extensions of input files (separate
		 *            with spaces if multiple)
		 * @param inputIsUserApproved is the parse approved by a user?
		 */
		protected RefDataParserWrapper(BibRefDataParser parser, String fileExtension, boolean inputIsUserApproved) {
			this(parser.name, parser, fileExtension, inputIsUserApproved);
		}

		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.plugins.bibRefs.BibRefDataParser#setDataProvider(de.uka.ipd.idaho.gamta.util.AnalyzerDataProvider)
		 */
		public void setDataProvider(AnalyzerDataProvider adp) {
			super.setDataProvider(adp);
			this.parser.setDataProvider(adp);
		}
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.plugins.bibRefs.BibRefDataParser#exit()
		 */
		public void exit() {
			this.parser.exit();
		}
		
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.plugins.bibRefs.BibRefDataParser#getLabel()
		 */
		public String getLabel() {
			return this.parser.getLabel();
		}
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.plugins.bibRefs.BibRefDataParser#getDescription()
		 */
		public String getDescription() {
			return this.parser.getDescription();
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
		public void format(Reader in, Writer out) throws IOException {
			// we don't do output for now, as the current XSLT handles that better
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
			return ((this.fileExtension == null) ? null : this.fileExtension.split("\\s+"));
		}
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.refBank.apps.webInterface.RefDataFormat#isInputParseUserApproved()
		 */
		public boolean isInputParseUserApproved() {
			return this.inputIsUserApproved;
		}
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.plugins.bibRefs.BibRefDataParser#parse(java.io.Reader)
		 */
		public ParsedRefData[] parse(Reader in) throws IOException {
			return this.parser.parse(in);
		}
		/* (non-Javadoc)
		 * @see de.uka.ipd.idaho.plugins.bibRefs.BibRefDataParser#streamParse(java.io.Reader)
		 */
		public ParsedRefDataIterator streamParse(Reader in) throws IOException {
			return this.parser.streamParse(in);
		}
	}
	
	
	/**
	 * Constructor
	 * @param name the data format name
	 */
	protected RefDataFormat(String name) {
		super(name);
	}
	
	/**
	 * Retrieve a brief description of the data format in the output role, e.g.
	 * for use in hover titles in web pages. The returned string should not
	 * contain any line breaks or HTML formatting.
	 * @return a description of the data format in the output role
	 */
	public abstract String getOutputDescription();
	
	/**
	 * Indicate whether or not to use this data format for output. If this
	 * method returns false, the format() method will never be invoked.
	 * @return true if the data format wants to be available for output
	 *         formatting, false otherwise
	 */
	public abstract boolean isOutputFormat();
	
	/**
	 * Read a parsed reference string (in MODS data format) from a reader and
	 * write it to a writer in the class specific format. This method should not
	 * make any unsynchronized accesses to instance fields, as multiple
	 * invokations may occur in parallel in web deploymant under heavy traffic.
	 * @param in the input reader
	 * @param out the output writer
	 * @throws IOException
	 */
	public abstract void format(Reader in, Writer out) throws IOException;
	
	/**
	 * Indicate whether or not to use this data format for input, i.e., for
	 * interpreting upload streams of parsed reference strings. If this method
	 * returns false, the parse() method will never be invoked.
	 * @return true if the data format wants to be available for input parsing,
	 *         false otherwise
	 */
	public abstract boolean isInputFormat();
	
	/**
	 * Indicate whether or not parsed versions of references provided by the
	 * data format in the input role are approved by a user.
	 * @return true is parsed references are approved by a user
	 */
	public abstract boolean isInputParseUserApproved();
	
	/**
	 * Retrieve a brief description of the data format in the input role, e.g.
	 * for use in the instruction text of an upload form. The returned string
	 * should not contain any line breaks, but may include HTML formatting.
	 * @return a description of the data format in the input role
	 */
	public abstract String getInputDescription();
	
	/**
	 * Retrieve an array holding the file extensions identifying files the data
	 * format can decode in the input role, e.g. for use in a file filter. If
	 * this method returns null, all file types are permitted. This default
	 * implementation does return null, sub classes are welcome to overwrite it
	 * as needed. In fact, overwriting this method is encouraged, so to prevent
	 * uploading files that cannot be decoded, which vainly causes network
	 * traffic.
	 * @return an array of file extensions the data format can parse
	 */
	public String[] getInputFileExtensions() {
		return null;
		//	would love to use MIME types, but the ACCEPT attribute of HTML file inputs does not work in many browsers
	}
	
	/**
	 * Parse one or more reference strings from the character stream provided by
	 * an input reader.
	 * @param in the reader to read from
	 * @return an array of upload string objects holding the pairs of plain and
	 *         parsed versions of reference strings
	 * @throws IOException
	 */
	public UploadString[] parseRefs(Reader in) throws IOException {
		ArrayList uRefs = new ArrayList();
		for (UploadStringIterator usit = this.streamParseRefs(in); usit.hasNextUploadString();)
			uRefs.add(usit.nextUploadString());
		return ((UploadString[]) uRefs.toArray(new UploadString[uRefs.size()]));
	}
	
	/**
	 * Parse reference strings one by one from the character stream provided by
	 * an input reader.
	 * @param in the reader to read from
	 * @return an iterator over upload string objects holding the pairs of plain
	 *         and parsed versions of reference strings
	 * @throws IOException
	 */
	public UploadStringIterator streamParseRefs(Reader in) throws IOException {
		final ParsedRefDataIterator refs = this.streamParse(in);
		return new UploadStringIterator() {
			int usTotal = 0;
			int usValid = 0;
			UploadString next = null;
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
				return refs.estimateRemaining();
			}
			public UploadString nextUploadString() throws IOException {
				UploadString us = this.next;
				this.next = null;
				return us;
			}
			public boolean hasNextUploadString() throws IOException {
				if (this.next != null)
					return true;
				
				if (!refs.hasNextRefData())
					return false;
				
				//	get next data set
				ParsedRefData ref = refs.nextRefData();
				this.usTotal++;
				
				//	check error
				if (ref.hasError()) {
					this.errors.add(new UploadStringError(ref.getError(), ref.getSource()));
					return this.hasNextUploadString();
				}
				
				//	produce and store plain and parsed representations
				String refString = ref.getAttribute("refString");
				if (refString == null)
					refString = BibRefUtils.toRefString(ref);
				
				//	could not generate string, proceed
				if (refString == null)
					this.errors.add(new UploadStringError("Could not generate reference string", ref.getSource()));
				else {
					refString = escaper.unescape(refString.trim());
					if (refString.startsWith("ERROR:")) {
						ref.setError(refString.substring("ERROR:".length()));
						this.errors.add(new UploadStringError(ref.getError(), ref.getSource()));
					}
					else {
						this.usValid++;
						this.next = new UploadString(refString, BibRefUtils.toModsXML(ref));
					}
				}
				
				//	recurse
				return this.hasNextUploadString();
			}
		};
	}
	
	/**
	 * Iterator over upload strings returned by the parseRefs() method, keeping
	 * track of both good and erroneous strings.
	 * 
	 * @author sautter
	 */
	public static interface UploadStringIterator {
		
		/**
		 * @return the number of valid upload strings obtained from the
		 *         underlying source
		 */
		public abstract int getValidCount();
		
		/**
		 * @return the number of erroneous upload strings obtained from the
		 *         underlying source
		 */
		public abstract int getErrorCount();
		
		/**
		 * @return the erroneous upload strings
		 */
		public abstract UploadStringError[] getErrors();
		
		/**
		 * @return the total number of upload strings obtained from the
		 *         underlying source
		 */
		public abstract int getTotalCount();
		
		/**
		 * Retrieve an estimate of the number of upload strings yet to come.
		 * This method is to at least vaguely gauge progress with data formats
		 * that read the entire input data before returning any reference data
		 * sets. If an estimate is not available, e.g. in data formats that
		 * really stream their input, this method should return -1.
		 * @return an estimate of the number of upload strings yet to come.
		 */
		public abstract int estimateRemaining();
		
		/**
		 * @return the next upload string
		 */
		public abstract UploadString nextUploadString() throws IOException;
		
		/**
		 * @return true if there are more upload strings to retrieve, false otherwise
		 */
		public abstract boolean hasNextUploadString() throws IOException;
	}
	
	/**
	 * Container for erroneous input data and associated error message.
	 * 
	 * @author sautter
	 */
	public static class UploadStringError {
		
		/** the error message */
		public final String error;
		
		/** the erroneous reference data */
		public final String source;
		
		public UploadStringError(String error, String source) {
			this.error = error;
			this.source = source;
		}
	}
	
	private static final Grammar escaper = new StandardGrammar();
}