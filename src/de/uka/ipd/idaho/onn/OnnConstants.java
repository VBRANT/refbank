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
package de.uka.ipd.idaho.onn;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import de.uka.ipd.idaho.htmlXmlUtil.Parser;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.Grammar;
import de.uka.ipd.idaho.htmlXmlUtil.grammars.StandardGrammar;

/**
 * Constant bearer for Java implementation of the Open Node Network.
 * 
 * @author sautter
 */
public interface OnnConstants {
	
	//	TODO document this
	
	public static final String ACTION_PARAMETER = "action";
	
	/**
	 * Formatter for timestamps outputting UTC time in US locale.
	 * 
	 * @author sautter
	 */
	public static class UtcDateFormat extends SimpleDateFormat {
		UtcDateFormat(String pattern) {
			super(pattern, Locale.US);
			this.setTimeZone(TimeZone.getTimeZone("UTC")); 
		}
	}
	
	public static final Grammar xmlGrammar = new StandardGrammar();
	public static final Parser xmlParser = new Parser(xmlGrammar);
//	
//	public static final Grammar htmlGrammar = new Html();
//	public static final Parser htmlParser = new Parser(htmlGrammar);
	
	public static final String ENCODING = "UTF-8";
	public static final String ONN_XML_NAMESPACE = null;//"http://idaho.ipd.uka.de/onn/schema";
	public static final String ONN_XML_NAMESPACE_ATTRIBUTE = ((ONN_XML_NAMESPACE == null) ? "" : (" xmlns:onn=\"" + ONN_XML_NAMESPACE + "\""));
	public static final String ONN_XML_NAMESPACE_PREFIX = ((ONN_XML_NAMESPACE == null) ? "" : "onn:");
	
	public static final String TIMESTAMP_DATE_FORMAT_STRING = "EEE, dd MMM yyyy HH:mm:ss Z";
	public static final DateFormat TIMESTAMP_DATE_FORMAT = new UtcDateFormat(TIMESTAMP_DATE_FORMAT_STRING);
}
