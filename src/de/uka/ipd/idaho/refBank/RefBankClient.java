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

import java.util.Properties;

import de.uka.ipd.idaho.onn.stringPool.StringPoolClient;

/**
 * RefBank specific client object, adding detail search for bibliographic
 * references.
 * 
 * @author sautter
 */
public interface RefBankClient extends StringPoolClient {
	
	/**
	 * Search for references, using both full text and detail predicates.
	 * @param textPredicates the full text predicates
	 * @param disjunctive combine the predicates with 'or'?
	 * @param type the type of references to search
	 * @param user the name of the user to contribute or last update the
	 *            references
	 * @param author the author to search for
	 * @param title the title to search for
	 * @param year the year of publication to search for
	 * @param origin the document origin (journal name or publisher) to search
	 *            for
	 * @param externalIDsByType a properties object containing external
	 *            identifiers (like an ISBN or DOI) indexed by their type
	 * @param limit the maximum number of references to include in the result
	 *            (0 means no limit)
	 * @return an iterator over the references matching the query
	 */
	public abstract PooledStringIterator findReferences(String[] textPredicates, boolean disjunctive, String type, String user, String author, String title, int year, String origin, Properties externalIDsByType, int limit);
	
	/**
	 * Search for references, using both full text and detail predicates.
	 * @param textPredicates the full text predicates
	 * @param disjunctive combine the predicates with 'or'?
	 * @param type the type of references to search
	 * @param user the name of the user to contribute or last update the
	 *            references
	 * @param author the author to search for
	 * @param title the title to search for
	 * @param year the year of publication to search for
	 * @param origin the document origin (journal name or publisher) to search
	 *            for
	 * @param externalIDsByType a properties object containing external
	 *            identifiers (like an ISBN or DOI) indexed by their type
	 * @param concise obtain a concise result, i.e., without parses?
	 * @param limit the maximum number of references to include in the result
	 *            (0 means no limit)
	 * @return an iterator over the references matching the query
	 */
	public abstract PooledStringIterator findReferences(String[] textPredicates, boolean disjunctive, String type, String user, String author, String title, int year, String origin, Properties externalIDsByType, boolean concise, int limit);
}