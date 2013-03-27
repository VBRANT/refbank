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
package de.uka.ipd.idaho.refBank;

import de.uka.ipd.idaho.onn.stringPool.StringPoolConstants;


/**
 * @author sautter
 *
 */
public interface RefBankConstants extends StringPoolConstants {
	
	public static final String RBK_XML_NAMESPACE = null;//"http://idaho.ipd.uka.de/sp/schema";
	public static final String RBK_XML_NAMESPACE_ATTRIBUTE = ((RBK_XML_NAMESPACE == null) ? "" : (" xmlns:rbk=\"" + RBK_XML_NAMESPACE + "\""));
	public static final String RBK_XML_NAMESPACE_PREFIX = ((RBK_XML_NAMESPACE == null) ? "" : "rbk:");
	
	public static final String REF_SET_NODE_TYPE = (RBK_XML_NAMESPACE_PREFIX + "refSet");
	public static final String REF_NODE_TYPE = (RBK_XML_NAMESPACE_PREFIX + "ref");
	public static final String REF_PLAIN_NODE_TYPE = (RBK_XML_NAMESPACE_PREFIX + "refString");
	public static final String REF_PARSED_NODE_TYPE = (RBK_XML_NAMESPACE_PREFIX + "refParsed");
	
	public static final String AUTHOR_PARAMETER = "author";
	public static final String DATE_PARAMETER = "date";
	public static final String TITLE_PARAMETER = "title";
	public static final String ORIGIN_PARAMETER = "origin";
	public static final String ID_TYPE_PARAMETER = "idType";
	public static final String ID_VALUE_PARAMETER = "idValue";
	
	public static final String STYLE_PARAMETER = "style";
}
