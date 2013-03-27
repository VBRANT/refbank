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

import de.uka.ipd.idaho.onn.OnnConstants;


/**
 * 
 * @author sautter
 */
public interface StringPoolConstants extends OnnConstants {
	
	public static final String SP_XML_NAMESPACE = null;//"http://idaho.ipd.uka.de/sp/schema";
	public static final String SP_XML_NAMESPACE_ATTRIBUTE = ((SP_XML_NAMESPACE == null) ? "" : (" xmlns:sp=\"" + SP_XML_NAMESPACE + "\""));
	public static final String SP_XML_NAMESPACE_PREFIX = ((SP_XML_NAMESPACE == null) ? "" : "sp:");
	
	public static final String STRING_SET_NODE_TYPE = (SP_XML_NAMESPACE_PREFIX + "stringSet");
	public static final String UPDATED_SINCE_ATTRIBUTE = "updatedSince";
	
	public static final String STRING_NODE_TYPE = (SP_XML_NAMESPACE_PREFIX + "string");
	public static final String STRING_ID_ATTRIBUTE = "id";
	public static final String CANONICAL_STRING_ID_ATTRIBUTE = "canonicalId";
	public static final String CREATE_TIME_ATTRIBUTE = "createTime"; // when was the string first entered into the system
	public static final String CREATE_DOMAIN_ATTRIBUTE = "createDomain"; // on which domain was the string first entered into the system
	public static final String CREATE_USER_ATTRIBUTE = "createUser"; // by whom was the string first entered into the system
	public static final String LOCAL_CREATE_DOMAIN_ATTRIBUTE = "localCreateDomain"; // from which domain did the local node first receive the string
	public static final String UPDATE_TIME_ATTRIBUTE = "updateTime"; // when was the string last updated (relevant for parsed version only due to idempotent inserts of plain strings)
	public static final String UPDATE_DOMAIN_ATTRIBUTE = "updateDomain"; // on which domain was the string last updated (relevant for parsed version only due to idempotent inserts of plain strings)
	public static final String UPDATE_USER_ATTRIBUTE = "updateUser"; // by whom was the string last updated (relevant for parsed version only due to idempotent inserts of plain strings)
	public static final String LOCAL_UPDATE_TIME_ATTRIBUTE = "localUpdateTime"; // when was the string last updated on the local node (relevant for parsed version only due to idempotent inserts of plain strings)
	public static final String LOCAL_UPDATE_DOMAIN_ATTRIBUTE = "localUpdateDomain"; // from which domain did the local node first receive the latest update (relevant for parsed version only due to idempotent inserts of plain strings)
	public static final String PARSE_CHECKSUM_ATTRIBUTE = "parseChecksum";
	public static final String PARSE_ERROR_ATTRIBUTE = "parseError";
	
	
	public static final String CREATED_ATTRIBUTE = "created";
	public static final String UPDATED_ATTRIBUTE = "updated";
	
	public static final String DELETED_ATTRIBUTE = "deleted";
	
	public static final String STRING_PLAIN_NODE_TYPE = (SP_XML_NAMESPACE_PREFIX + "stringPlain");
	public static final String STRING_PARSED_NODE_TYPE = (SP_XML_NAMESPACE_PREFIX + "stringParsed");
	
	public static final String FEED_ACTION_NAME = "feed";
	public static final String GET_ACTION_NAME = "get";
	public static final String FIND_ACTION_NAME = "find";
	public static final String UPDATE_ACTION_NAME = "update";
	
	public static final String ID_PARAMETER = "id";
	public static final String QUERY_PARAMETER = "query";
	public static final String COMBINE_PARAMETER = "combine";
	public static final String TYPE_PARAMETER = "type";
	public static final String USER_PARAMETER = "user";
	public static final String FORMAT_PARAMETER = "format";
	public static final String RESPONSE_FORMAT_PARAMETER = "responseFormat";
	public static final String STRINGS_PARAMETER = "strings";
	
	public static final String AND_COMBINE = "and";
	public static final String OR_COMBINE = "or";
	
	public static final String CONCISE_FORMAT = "concise";
	public static final String FULL_FORMAT = "full";
}
