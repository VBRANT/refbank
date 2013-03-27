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
package de.uka.ipd.idaho.onn.stringPool;

/**
 * Object providing XML namespace information and node types / element names.
 * 
 * @author sautter
 */
public interface StringPoolXmlSchemaProvider {
	
	/**
	 * @return the XML namespace attribute for the provided name types
	 */
	public abstract String getNamespaceAttribute();
	
	/**
	 * @return the XML element name for a set of pooled strings
	 */
	public abstract String getStringSetNodeType();
	
	/**
	 * @return the XML element name for a pooled strings
	 */
	public abstract String getStringNodeType();
	
	/**
	 * @return the XML element name for the plain, unparsed version of a pooled
	 *         strings
	 */
	public abstract String getStringPlainNodeType();
	
	/**
	 * @return the XML element name for the parsed version of a pooled strings
	 */
	public abstract String getStringParsedNodeType();
}
