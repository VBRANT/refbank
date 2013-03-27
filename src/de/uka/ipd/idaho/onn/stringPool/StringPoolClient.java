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

import java.io.IOException;

/**
 * Client for a string pool node.
 * 
 * @author sautter
 */
public interface StringPoolClient {
	
	/**
	 * Representation of a string retrieved from the backing string pool node.
	 * For the availability of attributes in instances of this class retrieved
	 * from the different methods, please refer to the documentation of the
	 * respective getters for the individual attributes or to the specification.
	 * 
	 * @author sautter
	 */
	public static abstract class PooledString {
		
		/** the ID of the string, i.e., the MD5 hash of the normalized
		 * string */
		public final String id;
		
		/** Constructor
		 * @param id the ID of the string
		 */
		protected PooledString(String id) {
			this.id = id;
		}
		
		/**
		 * Retrieve the raw unparsed version of the string represented by
		 * this object. This method never returns null, except if the object
		 * originates from an invokation of the getStringsUpdatedSince()
		 * method.
		 * @return the raw string version of the string
		 */
		public abstract String getStringPlain();
		
		/**
		 * Retrieve the parsed version of the string represented by this
		 * object, in the form of an XML string. This method returns a non-null
		 * result if (a) the network of string pool nodes has a parsed version of the
		 * string represented by this object, (b) the object was obtained via
		 * an invocation of any one of the getStrings() and findStrings()
		 * methods.
		 * @return the parsed version of the string
		 */
		public abstract String getStringParsed();
		
		//	TODO consider using lazy loading here
		
		/**
		 * Retrieve the checksum (MD5 hash) of the parsed version of the string.
		 * This method returns a non-null result only if (a) the network of
		 * string pool nodes has a parsed version of the string represented by
		 * this object, (b) the object was obtained via an invocation of any of
		 * the following methods: getStringsUpdatedSince(), updateString(), and
		 * updateStrings(), so basically the methods whose results do not
		 * include the parsed strings even if they exist.
		 * @return the checksum of the parsed string
		 */
		public abstract String getParseChecksum();
		
		/**
		 * Retrieve the ID of the canonical representation of the string
		 * represented by this object, e.g. the ID of some normalized form. This
		 * method never returns null. If the ID of the canonical representation
		 * has not been set, it returns the ID, indicating the string
		 * represented by this object as the canonical representation of itself
		 * @return the ID of the canonical representation of the string
		 */
		public abstract String getCanonicalStringID();
		
		/**
		 * Retrieve a possible consistency error of the parsed version of the
		 * string. This method can return a non-null result only if the
		 * object originates from an invokation of either one of the
		 * updateString() and updateStrings() methods.
		 * @return the consistency error in the parsed string, if any
		 */
		public abstract String getParseError();
		
		/**
		 * Retrieve the time the string represented by this object was
		 * created, i.e., the time the string was originally was inserted
		 * into the network of string pool nodes.
		 * @return the create time of the string
		 */
		public abstract long getCreateTime();
		
		/**
		 * Retrieve the domain the string represented by this object was created
		 * on, i.e., the domain on which the string was originally was inserted
		 * into the network of string pool nodes. This method returns null if
		 * the object was retrieved via the getStringsUpdatedSince() method.
		 * @return the create domain of the string
		 */
		public abstract String getCreateDomain();
		
		/**
		 * Retrieve the user who created the string represented by this object,
		 * i.e., the user who originally inserted the string into the network of
		 * string pool nodes. This method returns null if the object was
		 * retrieved via the getStringsUpdatedSince() method.
		 * @return the create user of the string
		 */
		public abstract String getCreateUser();
		
		/**
		 * Retrieve the time the string represented by this object was last
		 * updated, i.e., the time the last update to the string was inserted
		 * into the network of string pool nodes.
		 * @return the update time of the string
		 */
		public abstract long getUpdateTime();
		
		/**
		 * Retrieve the domain the string represented by this object was last
		 * updated on, i.e., the domain on which the last update to the string
		 * was inserted into the network of string pool nodes. This method
		 * returns null if the object was retrieved via the
		 * getStringsUpdatedSince() method.
		 * @return the update domain of the string
		 */
		public abstract String getUpdateDomain();
		
		/**
		 * Retrieve the user who last updated the string represented by this
		 * object, i.e., the user who inserted the last update to the string
		 * into the network of string pool nodes. This method returns null if
		 * the object was retrieved via the getStringsUpdatedSince() method.
		 * @return the update user of the string
		 */
		public abstract String getUpdateUser();
		
		/**
		 * Retrieve the time the string represented by this object was last
		 * updated in the backing string pool node. This method returns a meaningful
		 * timestamp only if the object was retrieved via the
		 * getStringsUpdatedSince() method; otherwise, it always returns -1.
		 * @return the update time of the string in the backing node
		 */
		public abstract long getNodeUpdateTime();
		
		/**
		 * Check whether the string represented by this object was newly
		 * created. This method can only return true in the object results from
		 * an invokation of one of the updateString() and updateStrings()
		 * methods; otherwise, it always returns false.
		 * @return true if the string was created, false otherwise
		 */
		public abstract boolean wasCreated();
		
		/**
		 * Check whether the string represented by this object was updated.
		 * This method can only return true in the object results from an
		 * invokation of one of the updateString() and updateStrings()
		 * methods; otherwise, it always returns false.
		 * @return true if the string was updated, false otherwise
		 */
		public abstract boolean wasUpdated();
		
		/**
		 * Check whether or not the string represented by this object is flagged
		 * as deleted. This method can only return true in the object results
		 * from an invokation of one of the getString(), getStrings(),
		 * findStrings(), and setDeleted() methods; otherwise, it always returns
		 * false.
		 * @return true if the string is flagged as deleted, false otherwise
		 */
		public abstract boolean isDeleted();
	}
	
	/**
	 * Container class for the plain and parsed versions of a string, intended
	 * for simplifying bulk uploads.
	 * 
	 * @author sautter
	 */
	public static class UploadString {
		
		/** the plain string (never null) */
		public final String stringPlain;
		
		/** the parsed string (may be null) */
		public final String stringParsed;
		
		/**
		 * Constructor
		 * @param stringPlain (must not be null)
		 */
		public UploadString(String stringPlain) {
			this(stringPlain, null);
		}
		
		/**
		 * Constructor
		 * @param stringPlain (must not be null)
		 * @param stringParsed (may be null)
		 */
		public UploadString(String stringPlain, String stringParsed) {
			this.stringPlain = stringPlain;
			this.stringParsed = stringParsed;
		}
	}
	
	/**
	 * Iterator object specific to pooled strings.
	 * 
	 * @author sautter
	 */
	public static interface PooledStringIterator {
		
		/**
		 * Check whether the iterator has a next string.
		 * @return true iof there is a next string, false otherwise
		 */
		public abstract boolean hasNextString();
		
		/**
		 * Retrieve the next string. This method only returns a string
		 * after a preceeding invokation of the hasNextString() method. If
		 * the latter method returns false, the behavior of this method is
		 * arbitrary. The attributes set with the string objects this method
		 * returns depend on the method the iterator was obtained from; please
		 * stringer to the documentation of the getters for the individual
		 * attributes for details.
		 * @return the next string
		 */
		public abstract PooledString getNextString();
		
		/**
		 * Retrieve the IO exception that possibly occurred in the interaction
		 * with the backing node that returned the iterator. If no exception
		 * occurred, this method returns null. If string objects have been
		 * retrieved from the iterator and there is no next string object as
		 * a result of an exception, this exception can also be obtained from
		 * this method.
		 * @return the exception that occurred in the interaction with the
		 *         backing node, if any.
		 */
		public abstract IOException getException();
	}
	
	/**
	 * Search result for encapsulating an exception
	 * 
	 * @author sautter
	 */
	public static class ExceptionPSI implements PooledStringIterator {
		private IOException ioe;
		public ExceptionPSI(IOException ioe) {
			this.ioe = ioe;
		}
		public boolean hasNextString() {
			return false;
		}
		public PooledString getNextString() {
			return null;
		}
		public IOException getException() {
			return this.ioe;
		}
	}
	
	/**
	 * Retrieve a string by its ID. This method is also good for resolving IDs.
	 * @param stringId the ID to resolve
	 * @return the string with the specified ID
	 * @throws IOException
	 */
	public abstract PooledString getString(String stringId) throws IOException;
	
	/**
	 * Retrieve the strings linked to a given canonical representation.
	 * @param canonicalStringId the ID of the canonical string whose
	 *            alternatives to retrieve
	 * @return an iterator over the strings linked to the one with the specified
	 *         ID
	 * @throws IOException
	 */
	public abstract PooledStringIterator getLinkedStrings(String canonicalStringId) throws IOException;
	
	/**
	 * Retrieve strings by their IDs. This method is also good for resolving
	 * IDs.
	 * @param stringIds an array holding the IDs to resolve
	 * @return an iterator over the strings with the specified IDs
	 * @throws IOException
	 */
	public abstract PooledStringIterator getStrings(String[] stringIds);
	
	/**
	 * Find strings using full text search.
	 * @param textPredicates the full text predicates
	 * @param disjunctive combine the predicates with 'or'?
	 * @param type the type of strings to search
	 * @param user the name of the user to contribute or last update the strings
	 * @return an iterator over the strings matching the query
	 */
	public abstract PooledStringIterator findStrings(String[] textPredicates, boolean disjunctive, String type, String user);
	
	/**
	 * Find strings using full text search.
	 * @param textPredicates the full text predicates
	 * @param disjunctive combine the predicates with 'or'?
	 * @param type the type of strings to search
	 * @param user the name of the user to contribute or last update the strings
	 * @param concise obtain a concise result, i.e., without parses?
	 * @return an iterator over the strings matching the query
	 */
	public abstract PooledStringIterator findStrings(String[] textPredicates, boolean disjunctive, String type, String user, boolean concise);
	
	/**
	 * Retrieve the strings updated since a given UTC timestamp.
	 * @param updatedSince the timestamp
	 * @return an iterator over the strings updates since the specified
	 *         timestamp
	 */
	public abstract PooledStringIterator getStringsUpdatedSince(long updatedSince);
	
	/**
	 * Upload a plain string without a parsed version.
	 * @param stringPlain the string to upload
	 * @param user the name of the user contributing the string
	 * @return the uploaded string
	 * @throws IOException
	 */
	public abstract PooledString updateString(String stringPlain, String user) throws IOException;
	
	/**
	 * Upload a plain string together with a parsed version.
	 * @param stringPlain the string to upload
	 * @param stringParsed the parse of the string to upload
	 * @param user the name of the user contributing the string
	 * @return the uploaded string
	 * @throws IOException
	 */
	public abstract PooledString updateString(String stringPlain, String stringParsed, String user) throws IOException;
	
	/**
	 * Upload a string, with or without a parsed version.
	 * @param string the string to upload
	 * @param user the name of the user contributing the string
	 * @return the uploaded string
	 * @throws IOException
	 */
	public abstract PooledString updateString(UploadString string, String user) throws IOException;

	/**
	 * Upload a series of plain string without a parsed versions.
	 * @param stringsPlain an array holding the string to upload
	 * @param user the name of the user contributing the strings
	 * @return an iterator over the uploaded strings
	 */
	public abstract PooledStringIterator updateStrings(String[] stringsPlain, String user);

	/**
	 * Upload a series of plain string with or without a parsed versions.
	 * @param strings an array holding the strings to upload
	 * @param user the name of the user contributing the strings
	 * @return an iterator over the uploaded strings
	 */
	public abstract PooledStringIterator updateStrings(UploadString[] strings, String user);
	
	/**
	 * Set the &quot;deleted&quot; flag of a string.
	 * @param stringId the ID of the string to set the flag for
	 * @param deleted the value to set the flag to
	 * @param user the name of the user setting the flag
	 * @return the updated string
	 * @throws IOException
	 */
	public abstract PooledString setDeleted(String stringId, boolean deleted, String user) throws IOException;
	
	/**
	 * Set the ID of the canonical representation of a string.
	 * @param stringId the ID of the string to link to its canonical
	 *            representation
	 * @param canonicalStringID the ID of the canonical representation
	 * @param user the name of the user setting the cononical representation ID
	 * @return the updated string
	 * @throws IOException
	 */
	public abstract PooledString setCanonicalStringId(String stringId, String canonicalStringId, String user) throws IOException;
}
