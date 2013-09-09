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
package de.uka.ipd.idaho.refBank;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.Properties;

import de.uka.ipd.idaho.onn.stringPool.StringPoolRestClient;

/**
 * RefBank specific rest client, adding detail search for bibliographic
 * references.
 * 
 * @author sautter
 */
public class RefBankRestClient extends StringPoolRestClient implements RefBankConstants, RefBankClient {
	
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
	
	/**
	 * Constructor
	 * @param baseUrl the URL of the RefBank node to connect to
	 */
	public RefBankRestClient(String baseUrl) {
		super(baseUrl);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.RefBankClient#findReferences(java.lang.String[], boolean, java.lang.String, java.lang.String, java.lang.String, java.lang.String, int, java.lang.String, java.util.Properties, int, booleab)
	 */
	public PooledStringIterator findReferences(String[] textPredicates, boolean disjunctive, String type, String user, String author, String title, int year, String origin, Properties externalIDsByType, int limit, boolean selfCanonicalOnly) {
		return this.findReferences(textPredicates, disjunctive, type, user, author, title, year, origin, externalIDsByType, false, limit, selfCanonicalOnly);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.refBank.RefBankClient#findReferences(java.lang.String[], boolean, java.lang.String, java.lang.String, java.lang.String, java.lang.String, int, java.lang.String, java.util.Properties, boolean, int, boolean)
	 */
	public PooledStringIterator findReferences(String[] textPredicates, boolean disjunctive, String type, String user, String author, String title, int year, String origin, Properties externalIDsByType, boolean concise, int limit, boolean selfCanonicalOnly) {
		try {
			StringBuffer detailPredicates = new StringBuffer();
			if (author != null)
				detailPredicates.append("&" + AUTHOR_PARAMETER + "=" + URLEncoder.encode(author, ENCODING));
			if (title != null)
				detailPredicates.append("&" + TITLE_PARAMETER + "=" + URLEncoder.encode(title, ENCODING));
			if (year > 0)
				detailPredicates.append("&" + DATE_PARAMETER + "=" + year);
			if (origin != null)
				detailPredicates.append("&" + ORIGIN_PARAMETER + "=" + URLEncoder.encode(origin, ENCODING));
			if (externalIDsByType != null)
				for (Iterator eidit = externalIDsByType.keySet().iterator(); eidit.hasNext();) {
					String idType = ((String) eidit.next());
					String id = externalIDsByType.getProperty(idType);
					detailPredicates.append("&ID-" + idType + "=" + URLEncoder.encode(id, ENCODING));
				}
			return this.findStrings(textPredicates, disjunctive, type, user, concise, limit, selfCanonicalOnly, detailPredicates.toString());
		}
		catch (IOException ioe) {
			return new ExceptionPSI(ioe);
		}
	}
}
