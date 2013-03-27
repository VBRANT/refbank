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
		 * @param name a custom name for the data format
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
//		RefData[] refs = this.parse(in);
		ArrayList uRefs = new ArrayList();
		for (UploadStringIterator usit = this.streamParseRefs(in); usit.hasNextUploadString();)
			uRefs.add(usit.nextUploadString());
//		for (int r = 0; r < refs.length; r++) {
////			
////			//	produce and store plain and parsed representations
////			String refString = this.produceRefString(refs[r]);
////			if (refString != null)
////				uRefs.add(new UploadString(refString, this.produceRefParsed(refs[r])));
//			
//			//	get type, induce if neccessary
//			String type = refs[r].getAttribute(PUBLICATION_TYPE);
//			if ((type == null) || (type.length() == 0))
//				type = classify(refs[r]);
//			
//			//	put reference attributes in string
//			StringBuffer refXml = new StringBuffer("<reference");
//			if (type != null)
//				refXml.append(" " + PUBLICATION_TYPE + "=\"" + type + "\"");
//			refXml.append(">");
//			for (int a = 0; a < attributeNames.length; a++)
//				appendAttribute(refXml, refs[r], attributeNames[a]);
//			refXml.append("</reference>");
//			
//			//	produce and store plain and parsed representations
//			String refString = buildString(this.refStringBuilder, refXml);
//			if (refString != null) {
//				refString = escaper.unescape(refString.trim());
////				System.out.println("REF STRING: " + refString);
//				uRefs.add(new UploadString(refString, buildString(this.refParsedBuilder, refXml)));
//			}
//		}
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
//				
//				//	get type, induce if neccessary
//				String type = ref.getAttribute(PUBLICATION_TYPE_ATTRIBUTE);
//				if ((type == null) || (type.length() == 0))
//					type = BibRefUtils.classify(ref);
//				
//				//	put reference attributes in string
//				StringBuffer refXml = new StringBuffer("<reference");
//				if (type != null)
//					refXml.append(" " + PUBLICATION_TYPE_ATTRIBUTE + "=\"" + type + "\"");
//				refXml.append(">");
//				for (int a = 0; a < attributeNames.length; a++)
//					appendAttribute(refXml, ref, attributeNames[a]);
//				refXml.append("</reference>");
//				
//				//	produce and store plain and parsed representations
//				String refString = ref.getAttribute("refString");
//				if (refString == null)
//					refString = transform(refStringBuilder, refXml);
				
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
//						this.next = new UploadString(refString, transform(refParsedBuilder, refXml));
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
	
//	/**
//	 * Parse one or more reference strings from the character stream provided by
//	 * an input reader.
//	 * @param in the reader to read from
//	 * @return an array of reference data objects
//	 * @throws IOException
//	 */
//	protected RefData[] parse(Reader in) throws IOException {
//		ArrayList refs = new ArrayList();
//		for (UploadRefDataIterator rit = this.streamParse(in); rit.hasNextRefData();)
//			refs.add(rit.nextRefData());
//		return ((RefData[]) refs.toArray(new RefData[refs.size()]));
//	}
//	
//	/**
//	 * Parse reference strings one by one from the character stream provided by
//	 * an input reader.
//	 * @param in the reader to read from
//	 * @return an iterator over reference data objects
//	 * @throws IOException
//	 */
//	protected abstract UploadRefDataIterator streamParse(Reader in) throws IOException;
//	
//	/**
//	 * Iterator over reference data sets extracted from a source during parsing.
//	 * 
//	 * @author sautter
//	 */
//	public static interface UploadRefDataIterator {
//		/**
//		 * Retrieve an estimate of the number of reference data sets yet to
//		 * come. This method is to at least vaguely gauge progress with data
//		 * formats that read the entire input data before returning any
//		 * reference data sets. If an estimate is not available, e.g. in data
//		 * formats that really stream their input, this method should return -1.
//		 * @return an estimate of the number of reference data sets yet to come.
//		 */
//		public abstract int estimateRemaining();
//		/**
//		 * @return the next reference data set
//		 */
//		public abstract UploadRefData nextRefData() throws IOException;
//		/**
//		 * @return true if there are more reference data sets to retrieve, false
//		 *         otherwise
//		 */
//		public abstract boolean hasNextRefData() throws IOException;
//	}
//	
//	/**
//	 * Transform the content of some char sequence through some XLS transformer.
//	 * If an error occurs, this method returns null.
//	 * @param xslt the transformer to use
//	 * @param refXml the char sequence to transform
//	 * @return the transformation result
//	 */
//	public static final String transform(Transformer xslt, CharSequence refXml) {
//		
//		//	we don't have a transformer, cannot do it
//		if (xslt == null)
//			return null;
//		
//		//	transform it
//		StringWriter refString = new StringWriter();
//		CharSequenceReader csr = new CharSequenceReader(refXml);
//		try {
//			xslt.transform(new StreamSource(csr), new StreamResult(refString));
//			return refString.toString();
//		}
//		catch (TransformerException te) {
//			te.printStackTrace(System.out);
//			return null;
//		}
////		TODO_elsewher find out if transformer hangups still occur with new thread safe transformers
////		TODO_ if so, switch back to using extra thread
////		TODO_ maybe even use a background worker or a pool thereof to reduce number of threads created
////		final IOException[] ioe = {null};
////		Thread tt = new Thread() {
////			public void run() {
////				synchronized (csr) {
////					csr.notify();
////				}
////				try {
////					xslt.transform(new StreamSource(csr), new StreamResult(refString));
////				}
////				catch (TransformerException te) {
////					ioe[0] = new IOException(te.getMessage());
////				}
////			}
////		};
////		synchronized (csr) {
////			tt.start();
////			try {
////				csr.wait();
////			} catch (InterruptedException ie) {}
////		}
////		while (tt.isAlive()) {
////			try {
////				tt.join(250);
////			} catch (InterruptedException ie) {}
////			if (ioe[0] != null)
////				throw ioe[0];
////			if ((csr.lastRead + 2500) < System.currentTimeMillis())
////				throw new IOException("XSLT timeout");
////		}
////		return refString.toString();
//	}
//	
//	private static final void appendAttribute(StringBuffer refXml, RefData ref, String attributeName) {
//		String[] values = ref.getAttributeValues(attributeName);
//		if (values == null)
//			return;
//		for (int v = 0; v < values.length; v++) {
//			refXml.append("<" + attributeName + ">");
//			refXml.append(escaper.escape(values[v]));
//			refXml.append("</" + attributeName + ">");
//		}
//	}
//	
//	private Transformer refStringBuilder;
//	private Transformer refParsedBuilder;
//	
	private static final Grammar escaper = new StandardGrammar();
//	private static final String[] attributeNames = {
//		AUTHOR,
//		YEAR,
//		TITLE,
//		JOURNAL_NAME,
//		PUBLISHER,
//		LOCATION,
//		PAGINATION,
//		VOLUME,
//		ISSUE,
//		NUMERO,
//		VOLUME_TITLE,
//		EDITOR,
//		PUBLICATION_URL,
//		PUBLICATION_DOI,
//		PUBLICATION_ISBN,
//	};
//	
//	/**
//	 * Check if the pagination of a reference is actually that of a part of a
//	 * larger volume, or if it consists of information about a whole volume,
//	 * e.g. the total number of pages it contains. If this method finds the
//	 * latter to be true, it removes the pagination and adjusts the reference
//	 * type to match the reference to a whole volume. If the former is true,
//	 * this method makes sure the pagination is a single page number or page
//	 * range, so to simplify parsing later on.
//	 * @param ref the reference data to check
//	 */
//	protected void checkPaginationAndType(RefData ref) {
//		String pageString = ref.getAttribute(PAGINATION).trim();
//		if (pageString == null)
//			return;
//		else if (pageCountPattern.matcher(pageString).find())
//			ref.removeAttribute(PAGINATION);
//		else {
//			String type = ref.getAttribute(PUBLICATION_TYPE);
//			String pages = null;
//			if (pages == null) {
//				Matcher prm = pageRangePattern.matcher(pageString);
//				while (prm.find()) {
//					String[] pnrs = prm.group().split("\\s*\\-+\\s*");
//					if (pnrs.length == 2) try {
//						int fpn = Integer.parseInt(pnrs[0]);
//						int lpn = Integer.parseInt(pnrs[1]);
//						if ((0 < fpn) && (fpn < lpn)) {
//							pages = (fpn + "-" + lpn);
//							break;
//						}
//					} catch (NumberFormatException nfe) {}
//				}
//			}
//			if (pages == null) {
//				Matcher pnm = pageNumberPattern.matcher(pageString);
//				while (pnm.find()) {
//					String pnr = pnm.group();
//					try {
//						int pn = Integer.parseInt(pnr);
//						if (0 < pn) {
//							pages = ("" + pn);
//							break;
//						}
//					} catch (NumberFormatException nfe) {}
//				}
//			}
//			
//			//	no pages found
//			if (pages == null)
//				ref.removeAttribute(PAGINATION);
//			
//			//	likely simply the whole book or journal
//			else if ((BOOK_TYPE.equals(type) || JOURNAL_VOLUME_TYPE.equals(type)) && pages.matches("[1]\\-[2-9][0-9]{2,}"))
//				ref.removeAttribute(PAGINATION);
//			
//			//	found pages, adjust publication type if secure
//			else {
//				ref.setAttribute(PAGINATION, pages);
//				if (pages.indexOf('-') != -1) {
//					if (BOOK_TYPE.equals(type) && ref.hasAttribute(VOLUME_TITLE))
//						type = BOOK_CHAPTER_TYPE;
//					else if (JOURNAL_VOLUME_TYPE.equals(type) && ref.hasAttribute(JOURNAL_NAME))
//						type = JOURNAL_ARTICEL_TYPE;
//				}
//			}
//			
//			//	set back type
//			ref.setAttribute(PUBLICATION_TYPE, type);
//		}
//	}
//	
//	private static Pattern pageRangePattern = Pattern.compile("[1-9][0-9]*+\\s*\\-+\\s*[1-9][0-9]*+");
//	private static Pattern pageNumberPattern = Pattern.compile("[1-9][0-9]*+");
//	private static Pattern pageCountPattern = Pattern.compile("[1-9][0-9]*\\s*pp", Pattern.CASE_INSENSITIVE);
//	
//	private static String classify(RefData ref) {
//		
//		//	got pagination ==> part of something
//		boolean gotPagination = ref.hasAttribute(PAGINATION);
//		
//		//	got volume, issue, or number designator
//		boolean gotPartDesignator = (ref.hasAttribute(VOLUME) || ref.hasAttribute(ISSUE) || ref.hasAttribute(NUMERO));
//		
//		//	check journal name
//		boolean gotJournalName = ref.hasAttribute(JOURNAL_NAME);
//		
//		//	clearly a journal or part of one
//		if (gotJournalName && gotPartDesignator)
//			return (gotPagination ? JOURNAL_ARTICEL_TYPE : JOURNAL_VOLUME_TYPE);
//		
////		//	check publisher name
////		boolean gotPublisher = ref.hasAttribute(PUBLISHER);
////		
//		//	check title
//		boolean gotTitle = ref.hasAttribute(TITLE);
//		
//		//	check volume title
//		boolean gotVolumeTitle = ref.hasAttribute(VOLUME_TITLE);
//		
//		//	check for proceedings
//		boolean isProceedings = false;
//		if (!isProceedings && gotJournalName && !gotPartDesignator && ref.getAttribute(JOURNAL_NAME).toLowerCase().startsWith("proc"))
//			isProceedings = true;
//		if (!isProceedings && gotVolumeTitle && !gotPartDesignator && ref.getAttribute(VOLUME_TITLE).toLowerCase().startsWith("proc"))
//			isProceedings = true;
//		if (!isProceedings && gotTitle && !gotPagination && ref.getAttribute(TITLE).toLowerCase().startsWith("proc"))
//			isProceedings = true;
//		
//		//	part of book or proceedings
//		if (gotPagination)
//			return (isProceedings ? PROCEEDINGS_PAPER_TYPE : BOOK_CHAPTER_TYPE);
//		
//		//	part of proceedings with missing page data
//		if (isProceedings && gotVolumeTitle)
//			return PROCEEDINGS_PAPER_TYPE;
//		
//		//	book or proceedings
//		else return (isProceedings ? PROCEEDINGS_VOLUME_TYPE : BOOK_TYPE); 
//	}
}
