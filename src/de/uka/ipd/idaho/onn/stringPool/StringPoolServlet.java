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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Transformer;

import de.uka.ipd.idaho.easyIO.EasyIO;
import de.uka.ipd.idaho.easyIO.IoProvider;
import de.uka.ipd.idaho.easyIO.SqlQueryResult;
import de.uka.ipd.idaho.easyIO.sql.TableColumnDefinition;
import de.uka.ipd.idaho.easyIO.sql.TableDefinition;
import de.uka.ipd.idaho.easyIO.util.RandomByteSource;
import de.uka.ipd.idaho.easyIO.web.WebAppHost;
import de.uka.ipd.idaho.gamta.AnnotationUtils;
import de.uka.ipd.idaho.gamta.Gamta;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.MutableTokenSequence;
import de.uka.ipd.idaho.gamta.QueriableAnnotation;
import de.uka.ipd.idaho.gamta.util.SgmlDocumentReader;
import de.uka.ipd.idaho.htmlXmlUtil.TokenReceiver;
import de.uka.ipd.idaho.htmlXmlUtil.TreeNodeAttributeSet;
import de.uka.ipd.idaho.htmlXmlUtil.accessories.XsltUtils;
import de.uka.ipd.idaho.onn.OnnServlet;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * Java & JDBC implementation of the open node network string pool
 * specification. Sub classes willing to use their own XML namespace instead of
 * the default one should overwrite all respective methods.
 * 
 * @author sautter
 */
public class StringPoolServlet extends OnnServlet implements StringPoolClient, StringPoolConstants, StringPoolXmlSchemaProvider {
	
	/*
	 * TODO extend API to expose history
	 */
	
	//	main table
	private static final String PARSED_STRING_TABLE_NAME_SUFFIX = "Data";
	private static final String STRING_ID_COLUMN_NAME = "StringId";
	private static final String STRING_ID_HASH_COLUMN_NAME = "IdHash"; // int hash of the ID string, speeding up joins with index table
	private static final String STRING_TYPE_COLUMN_NAME = "StringType";
	private static final int STRING_TYPE_COLUMN_LENGTH = 32;
	private static final String PARSE_CHECKSUM_COLUMN_NAME = "ParseChecksum";
	private static final String CANONICAL_STRING_ID_COLUMN_NAME = "CanStringId";
	private static final String CANONICAL_STRING_ID_HASH_COLUMN_NAME = "CanIdHash"; // int hash of the ID string, speeding up joins with index table
	
	private static final String CREATE_TIME_COLUMN_NAME = "CreateTime";
	private static final String CREATE_DOMAIN_COLUMN_NAME = "CreateDomain";
	private static final String CREATE_USER_COLUMN_NAME = "CreateUser";
	private static final String LOCAL_CREATE_DOMAIN_COLUMN_NAME = "LocalCreateDomain";
	private static final String UPDATE_TIME_COLUMN_NAME = "UpdateTime";
	private static final String UPDATE_DOMAIN_COLUMN_NAME = "UpdateDomain";
	private static final String UPDATE_USER_COLUMN_NAME = "UpdateUser";
	private static final String LOCAL_UPDATE_TIME_COLUMN_NAME = "LocalUpdateTime";
	private static final String LOCAL_UPDATE_DOMAIN_COLUMN_NAME = "LocalUpdateDomain";
	private static final String DELETED_COLUMN_NAME = "IsDeleted";
	
	private static final int DOMAIN_COLUMN_LENGTH = 32;
	private static final int USER_COLUMN_LENGTH = 64;
	
	private static final String ID_TYPE_COLUMN_NAME = "IdType";
	private static final int ID_TYPE_COLUMN_LENGHT = 32;
	private static final String ID_VALUE_COLUMN_NAME = "IdValue";
	private static final int ID_VALUE_COLUMN_LENGTH = 188; // fills up records to 256 bytes
	
	private static final String STRING_TEXT_COLUMN_NAME = "String";
//	private static final int STRING_TEXT_COLUMN_LENGTH = 648;
	private static final int STRING_TEXT_COLUMN_LENGTH = 1672; // fills up records to 2048 bytes
	
	//	index table
	protected static final String PARSED_STRING_INDEX_TABLE_NAME_SUFFIX = "Index";
	
	//	update history table
	private static final String PARSED_STRING_HISTORY_TABLE_NAME_SUFFIX = "History";
	private static final String UPDATE_SOURCE_COLUMN_NAME = "UpdateSource";
	private static final int UPDATE_SOURCE_COLUMN_LENGTH = 256;
	
	//	external identifier table
	protected static final String PARSED_STRING_IDENTIFIER_TABLE_NAME_SUFFIX = "ExternalIDs";
	
	private IoProvider io;
	private boolean isUsingIndexTable = false;
	
	private String parsedStringTableName = (this.getExternalDataName() + PARSED_STRING_TABLE_NAME_SUFFIX);
	private String parsedStringIndexTableName = (this.getExternalDataName() + PARSED_STRING_INDEX_TABLE_NAME_SUFFIX);
	private String parsedStringHistoryTableName = (this.getExternalDataName() + PARSED_STRING_HISTORY_TABLE_NAME_SUFFIX);
	private String parsedStringIdentifierTableName = (this.getExternalDataName() + PARSED_STRING_IDENTIFIER_TABLE_NAME_SUFFIX);
	
	/**
	 * Specify the name to use for the contained data in outside resources like
	 * databases (table name prefix) or the file system. This default
	 * implementation simply returns 'PooledString', sub classes are welcome to
	 * overwrite this method to specify a less generic name. In fact, sub
	 * classes are highly recommended to provide a more specific name to prevent
	 * name collisions, e.g. if multiple sub classes of this class are together
	 * in a web application or share a database. If an mplementations of this
	 * method depends on external parameters, these parameters have to be read
	 * before making the super call to the init() method of this class.
	 * @return the name to use for the contained data in outside resources
	 */
	protected String getExternalDataName() {
		return "PooledString";
	}
	
	private String xmlNamespaceAttribute = this.getNamespaceAttribute();
	private String stringSetNodeType = this.getStringSetNodeType();
	private String stringNodeType = this.getStringNodeType();
	private String stringPlainNodeType = this.getStringPlainNodeType();
	private String stringParsedNodeType = this.getStringParsedNodeType();
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolXmlSchemaProvider#getNamespaceAttribute()
	 */
	public String getNamespaceAttribute() {
		return SP_XML_NAMESPACE_ATTRIBUTE;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolXmlSchemaProvider#getStringNodeType()
	 */
	public String getStringNodeType() {
		return STRING_NODE_TYPE;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolXmlSchemaProvider#getStringParsedNodeType()
	 */
	public String getStringParsedNodeType() {
		return STRING_PARSED_NODE_TYPE;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolXmlSchemaProvider#getStringPlainNodeType()
	 */
	public String getStringPlainNodeType() {
		return STRING_PLAIN_NODE_TYPE;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolXmlSchemaProvider#getStringSetNodeType()
	 */
	public String getStringSetNodeType() {
		return STRING_SET_NODE_TYPE;
	}
	
	/**
	 * This implementation loads basic settings for the ONN String Pool node, so
	 * sub classes overwriting this method have to make the super call.
	 * @see de.uka.ipd.idaho.onn.OnnServlet#doInit()
	 */
	protected void doInit() throws ServletException {
		super.doInit();
		
		//	get external data name
		String externalDataName = this.getSetting("externalDataName");
		if (externalDataName == null)
			externalDataName = this.getInitParameter("externalDataName");
		if (externalDataName == null)
			externalDataName = this.getExternalDataName();
		
		//	make sure folder for parses exists
		String parsedStringFolderName = externalDataName;
		if (!parsedStringFolderName.endsWith("s")) {
			if (parsedStringFolderName.endsWith("y") && (parsedStringFolderName.length() > 1)) {
				if (Gamta.VOWELS.indexOf(parsedStringFolderName.charAt(parsedStringFolderName.length() - 2)) == -1)
					parsedStringFolderName = (parsedStringFolderName.substring(0, (parsedStringFolderName.length() - 1)) + "ies");
				else parsedStringFolderName = (parsedStringFolderName + "s");
			}
			else parsedStringFolderName = (parsedStringFolderName + "s");
		}
		this.parsedStringsFolder = new File(this.dataFolder, parsedStringFolderName);
		this.parsedStringsFolder.mkdirs();
		
		//	update table names
		this.parsedStringTableName = (externalDataName + PARSED_STRING_TABLE_NAME_SUFFIX);
		this.parsedStringIndexTableName = (externalDataName + PARSED_STRING_INDEX_TABLE_NAME_SUFFIX);
		this.parsedStringHistoryTableName = (externalDataName + PARSED_STRING_HISTORY_TABLE_NAME_SUFFIX);
		
		// get and check database connection
		this.io = WebAppHost.getInstance(this.getServletContext()).getIoProvider();
		if (!this.io.isJdbcAvailable())
			throw new RuntimeException("ParsedStringPool: Cannot work without database access.");
		
		// produce data table
		TableDefinition dtd = new TableDefinition(this.parsedStringTableName);
		dtd.addColumn(STRING_ID_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 32);
		dtd.addColumn(STRING_ID_HASH_COLUMN_NAME, TableDefinition.INT_DATATYPE, 0);
		dtd.addColumn(CANONICAL_STRING_ID_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 32);
		dtd.addColumn(CANONICAL_STRING_ID_HASH_COLUMN_NAME, TableDefinition.INT_DATATYPE, 0);
		dtd.addColumn(STRING_TYPE_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, STRING_TYPE_COLUMN_LENGTH);
		dtd.addColumn(PARSE_CHECKSUM_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 32);
		dtd.addColumn(CREATE_TIME_COLUMN_NAME, TableDefinition.BIGINT_DATATYPE, 0);
		dtd.addColumn(CREATE_DOMAIN_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, DOMAIN_COLUMN_LENGTH);
		dtd.addColumn(CREATE_USER_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, USER_COLUMN_LENGTH);
		dtd.addColumn(LOCAL_CREATE_DOMAIN_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, DOMAIN_COLUMN_LENGTH);
		dtd.addColumn(UPDATE_TIME_COLUMN_NAME, TableDefinition.BIGINT_DATATYPE, 0);
		dtd.addColumn(UPDATE_DOMAIN_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, DOMAIN_COLUMN_LENGTH);
		dtd.addColumn(UPDATE_USER_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, USER_COLUMN_LENGTH);
		dtd.addColumn(LOCAL_UPDATE_TIME_COLUMN_NAME, TableDefinition.BIGINT_DATATYPE, 0);
		dtd.addColumn(LOCAL_UPDATE_DOMAIN_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, DOMAIN_COLUMN_LENGTH);
		dtd.addColumn(DELETED_COLUMN_NAME, TableDefinition.CHAR_DATATYPE, 1);
		dtd.addColumn(STRING_TEXT_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, STRING_TEXT_COLUMN_LENGTH);
		if (!this.io.ensureTable(dtd, true))
			throw new RuntimeException("ParsedStringPool: Cannot work without database access.");
		
		//	index main table
		this.io.indexColumn(this.parsedStringTableName, STRING_ID_COLUMN_NAME);
		this.io.indexColumn(this.parsedStringTableName, STRING_ID_HASH_COLUMN_NAME);
		this.io.indexColumn(this.parsedStringTableName, CANONICAL_STRING_ID_COLUMN_NAME);
		this.io.indexColumn(this.parsedStringTableName, CANONICAL_STRING_ID_HASH_COLUMN_NAME);
		this.io.indexColumn(this.parsedStringTableName, LOCAL_UPDATE_TIME_COLUMN_NAME);
		
		// produce index table
		TableDefinition itd = new TableDefinition(this.parsedStringIndexTableName);
		itd.addColumn(STRING_ID_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 32);
		itd.addColumn(STRING_ID_HASH_COLUMN_NAME, TableDefinition.INT_DATATYPE, 0);
		if (this.extendIndexTableDefinition(itd)) {
			if (this.io.ensureTable(itd, true)) {
				this.isUsingIndexTable = true;
				//	index custom columns
				TableColumnDefinition[] itdcs = itd.getColumns();
				for (int c = 0; c < itdcs.length; c++)
					this.io.indexColumn(this.parsedStringIndexTableName, itdcs[c].getColumnName());
			}
			else throw new RuntimeException("ParsedStringPool: Cannot work without database access.");
		}
		
		// produce history table
		TableDefinition htd = new TableDefinition(this.parsedStringHistoryTableName);
		htd.addColumn(STRING_ID_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 32);
		htd.addColumn(STRING_ID_HASH_COLUMN_NAME, TableDefinition.INT_DATATYPE, 0);
		htd.addColumn(UPDATE_TIME_COLUMN_NAME, TableDefinition.BIGINT_DATATYPE, 0);
		htd.addColumn(UPDATE_DOMAIN_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, DOMAIN_COLUMN_LENGTH);
		htd.addColumn(UPDATE_USER_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, USER_COLUMN_LENGTH);
		htd.addColumn(LOCAL_UPDATE_TIME_COLUMN_NAME, TableDefinition.BIGINT_DATATYPE, 0);
		htd.addColumn(LOCAL_UPDATE_DOMAIN_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, DOMAIN_COLUMN_LENGTH);
		htd.addColumn(UPDATE_SOURCE_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, UPDATE_SOURCE_COLUMN_LENGTH);
		if (!this.io.ensureTable(htd, true))
			throw new RuntimeException("ParsedStringPool: Cannot work without database access.");
		
		//	produce external identifier table
		TableDefinition eitd = new TableDefinition(this.parsedStringIdentifierTableName);
		eitd.addColumn(STRING_ID_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 32);
		eitd.addColumn(STRING_ID_HASH_COLUMN_NAME, TableDefinition.INT_DATATYPE, 0);
		eitd.addColumn(ID_TYPE_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, ID_TYPE_COLUMN_LENGHT);
		eitd.addColumn(ID_VALUE_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, ID_VALUE_COLUMN_LENGTH);
		if (!this.io.ensureTable(eitd, true))
			throw new RuntimeException("ParsedStringPool: Cannot work without database access.");
		
		//	index external identifiers
		this.io.indexColumn(this.parsedStringIdentifierTableName, STRING_ID_COLUMN_NAME);
		this.io.indexColumn(this.parsedStringIdentifierTableName, STRING_ID_HASH_COLUMN_NAME);
		this.io.indexColumn(this.parsedStringIdentifierTableName, ID_TYPE_COLUMN_NAME);
		this.io.indexColumn(this.parsedStringIdentifierTableName, ID_VALUE_COLUMN_NAME);
		
		
		//	clean up legacy data
		String cleanupGetterQuery = "SELECT " + STRING_ID_COLUMN_NAME + ", min(" + CREATE_TIME_COLUMN_NAME + ")" +
				" FROM " + this.parsedStringTableName + 
				" GROUP BY " + STRING_ID_COLUMN_NAME + 
				" HAVING count(*) > 1" +
				";";
		SqlQueryResult cleanupGetterSqr = null;
		try {
			cleanupGetterSqr = this.io.executeSelectQuery(cleanupGetterQuery, true); // using copy, we release the lock before this method returns, so we can write to the table
			while (cleanupGetterSqr.next()) {
				String id = cleanupGetterSqr.getString(0);
				String firstCreateTime = cleanupGetterSqr.getString(1);
				String cleanupQuery = "DELETE FROM " + this.parsedStringTableName + 
						" WHERE " + STRING_ID_COLUMN_NAME + " = '" + id + "'" +
							" AND " + CREATE_TIME_COLUMN_NAME + " > " + firstCreateTime + 
						";";
				try {
					int deleted = this.io.executeUpdateQuery(cleanupQuery);
					System.out.println("ParsedStringPool: deleted " + deleted + " duplicates of string '" + id + "'");
				}
				catch (SQLException sqle) {
					System.out.println("ParsedStringPool: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while cleaning up duplicates.");
					System.out.println("  query was " + cleanupQuery);
				}
			}
		}
		catch (SQLException sqle) {
			System.out.println("ParsedStringPool: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while getting duplicates for cleanup.");
			System.out.println("  query was " + cleanupGetterQuery);
		}
		finally {
			if (cleanupGetterSqr != null)
				cleanupGetterSqr.close();
		}
		
		//	set primary key constraints
		this.io.setPrimaryKey(this.parsedStringTableName, STRING_ID_COLUMN_NAME);
		if (this.isUsingIndexTable)
			this.io.setPrimaryKey(this.parsedStringIndexTableName, STRING_ID_COLUMN_NAME);
		
		//	activate updates
		this.doUpdates = true;
	}
	
	/**
	 * Add sub class specific data columns to index table. If a sub class wants
	 * to use the index table, this method has to add the respective columns and
	 * return true. Respective sub classes should also overwrite the
	 * indexParsedString() method to populate the index. If this method returns
	 * false, using the index table is disabled. This default implementation
	 * does simply return false, sub classes are welcome to overwrite it as
	 * needed.
	 * @param itd the table definition to extend
	 * @return true if the index table should be used, false otherwise
	 */
	protected boolean extendIndexTableDefinition(TableDefinition itd) {
		return false;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.OnnServlet#exit()
	 */
	protected void exit() {
		super.exit();
		this.doUpdates = false;
		this.io.close();
	}
	
	private boolean doUpdates = false;
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.OnnServlet#doUpdateFrom(de.uka.ipd.idaho.onn.OnnServlet.OnnNode)
	 */
	protected void doUpdateFrom(OnnNode node) throws IOException {
		if (!this.doUpdates)
			return;
		
		//	retrieve new strings
		String lastReceivedString = this.getSetting((node.name.replaceAll("[^A-Za-z0-9\\-\\_]", "_") + ".lastReceived"), "0");
		//	as HTTP timestamp only resolves down to seconds, we have to subtract 999 milliseconds to make sure we're not missing anything
		URL feedUrl = new URL(node.accessUrl + "?" + ACTION_PARAMETER + "=" + FEED_ACTION_NAME + "&" + UPDATED_SINCE_ATTRIBUTE + "=" + URLEncoder.encode(TIMESTAMP_DATE_FORMAT.format(new Date(Math.max((Long.parseLong(lastReceivedString)-999), 1))), ENCODING));
		BufferedReader feedReader = new BufferedReader(new InputStreamReader(feedUrl.openStream(), ENCODING));
		boolean feedInterrupted = false;
		final HashMap feedStrings = new HashMap();
		final long[] lastReceived = {Long.parseLong(lastReceivedString)};
		try {
			xmlParser.stream(feedReader, new TokenReceiver() {
				public void close() throws IOException {}
				public void storeToken(String token, int treeDepth) throws IOException {
					if (xmlGrammar.isTag(token)) {
						String type = xmlGrammar.getType(token);
						type = type.substring(type.indexOf(':') + 1);
						if (!stringNodeType.equals(type))
							return;
						TreeNodeAttributeSet stringAttributes = TreeNodeAttributeSet.getTagAttributes(token, xmlGrammar);
						long createTime = parseTime(stringAttributes.getAttribute(CREATE_TIME_ATTRIBUTE));
						long updateTime = parseTime(stringAttributes.getAttribute(UPDATE_TIME_ATTRIBUTE));
						long remoteUpdateTime = parseTime(stringAttributes.getAttribute(LOCAL_UPDATE_TIME_ATTRIBUTE));
						boolean deleted = "true".equals(stringAttributes.getAttribute(DELETED_ATTRIBUTE, "false"));
						String stringId = stringAttributes.getAttribute(STRING_ID_ATTRIBUTE);
						String canonicalStringId = stringAttributes.getAttribute(CANONICAL_STRING_ID_ATTRIBUTE);
						String parseStringChecksum = stringAttributes.getAttribute(PARSE_CHECKSUM_ATTRIBUTE);
						feedStrings.put(stringId, new InternalPooledString(stringId, canonicalStringId, parseStringChecksum, createTime, updateTime, 0, deleted));
						lastReceived[0] = Math.max(lastReceived[0], remoteUpdateTime);
						if (feedStrings.size() >= maxUpdateCount)
							throw new MaxUpdateCountExceededException();
					}
				}
			});
		}
		catch (MaxUpdateCountExceededException mucee) {
			feedInterrupted = true;
		}
		feedReader.close();
		System.out.println("StringPoolServlet: got " + feedStrings.size() + " potential updates" + (feedInterrupted ? ", with more to come" : ""));
		
		//	update strings
		ArrayList updateBatch = new ArrayList(updateBatchSize);
		for (Iterator sit = feedStrings.values().iterator(); sit.hasNext() && this.doUpdates;) {
			InternalPooledString ps = ((InternalPooledString) sit.next());
			updateBatch.add(ps);
			
			//	process current batch
			if (updateBatch.size() == updateBatchSize) {
				this.updateStringsFromFeed(updateBatch, node.name, node.accessUrl);
				updateBatch.clear();
				try { // let's not knock ourselves out completely with the update workload ...
					Thread.sleep(1000);
				} catch (InterruptedException ie) {}
			}
		}
		
		//	process last (partially filled) batch (if any)
		if ((updateBatch.size() != 0) && this.doUpdates)
			this.updateStringsFromFeed(updateBatch, node.name, node.accessUrl);
		System.out.println("StringPoolServlet: updates done");
//		for (Iterator sit = feedStrings.values().iterator(); sit.hasNext() && this.doUpdates;) {
//			InternalPooledString ps = ((InternalPooledString) sit.next());
//			this.updateStringFromFeed(ps, node.name, node.accessUrl);
//		}
		
		//	remember what we received last
		this.setSetting((node.name.replaceAll("[^A-Za-z0-9\\-\\_]", "_") + ".lastReceived"), ("" + lastReceived[0]));
		
		//	recurse with new limit
		if (feedInterrupted && this.doUpdates)
			this.doUpdateFrom(node);
	}
	
	private static final long parseTime(String timeString) throws NumberFormatException {
		try {
			return TIMESTAMP_DATE_FORMAT.parse(timeString).getTime();
		}
		catch (ParseException pe) {
			return Long.parseLong(timeString);
		}
	}
	
	private static final int maxUpdateCount = 16384; // TODO optimize this value
	private static class MaxUpdateCountExceededException extends RuntimeException {}
	private static final int updateBatchSize = 32; // TODO optimize this value
	
	private void updateStringsFromFeed(ArrayList feedStrings, String dataNodeName, String dataUrl) throws IOException {
		System.out.println("  - doing batch of " + feedStrings.size() + " potential updates");
		
		//	index incoming strings
		HashMap toUpdateStrings = new HashMap(((int) ((feedStrings.size() + 1) / 0.9f)), 0.9f);
		for (int s = 0; s < feedStrings.size(); s++)
			toUpdateStrings.put(((InternalPooledString) feedStrings.get(s)).id, feedStrings.get(s));
		
		//	check if strings exist with current checksum, canonical string id, and deletion status
		InternalPooledStringIterator localStrings = this.getInternalStrings((String[]) toUpdateStrings.keySet().toArray(new String[toUpdateStrings.size()]));
		HashSet updateStringIds = new HashSet(((int) ((feedStrings.size() + 1) / 0.9f)), 0.9f);
		while (localStrings.hasNextString()) {
			InternalPooledString localString = localStrings.getNextString();
			InternalPooledString feedString = ((InternalPooledString) toUpdateStrings.get(localString.id));
			if (feedString == null)
				continue;
			
			//	local update more recent than remote one, ignore it
			if (feedString.updateTime < localString.updateTime)
				toUpdateStrings.remove(feedString.id);
			
			//	we know this one, and parse not given or unchanged ==> remove from update set
			else if ((feedString.parseChecksum == null) || (feedString.parseChecksum.length() == 0) || localString.parseChecksum.equals(feedString.parseChecksum)) {
				toUpdateStrings.remove(feedString.id);
				
				//	update databased attributes, however
				this.doPlainUpdate(feedString.id, feedString.canonicalId, feedString.deleted, feedString.updateDomain, feedString.updateUser, feedString.updateTime, ("FEED:" + dataNodeName), localString, dataNodeName);
			}
			
			//	we know this one, but parse has changed ==> just remember it's not totally new
			else updateStringIds.add(feedString.id);
		}
		System.out.println("    - got " + updateStringIds.size() + " updates and " + (toUpdateStrings.size() - updateStringIds.size()) + " inserts");
		if (toUpdateStrings.isEmpty()) {
			System.out.println("    - nothing to update, done");
			return;
		}
		
		//	fetch data
		StringBuffer stringIdParameter = new StringBuffer();
		for (Iterator usit = toUpdateStrings.keySet().iterator(); usit.hasNext();)
			stringIdParameter.append("&" + STRING_ID_ATTRIBUTE + "=" + ((String) usit.next()));
		URL stringsUrl = new URL(dataUrl + "?" + ACTION_PARAMETER + "=" + GET_ACTION_NAME + stringIdParameter.toString());
		BufferedReader stringsReader = new BufferedReader(new InputStreamReader(stringsUrl.openStream(), ENCODING));
		InternalPooledString[] strings = this.readStrings(stringsReader, "xml", System.currentTimeMillis(), null);
		System.out.println("    - update data loaded");
		
		//	do updates
		for (int s = 0; s < strings.length; s++) {
			InternalPooledString feedString = ((InternalPooledString) toUpdateStrings.get(strings[s].id));
			if (feedString == null)
				continue;
			
			//	create new or updated string object ...
			InternalPooledString updateString = new InternalPooledString(feedString.createTime, strings[s].createDomain, strings[s].createUser, feedString.updateTime, strings[s].updateDomain, strings[s].updateUser, System.currentTimeMillis(), feedString.canonicalId, feedString.deleted, strings[s].stringPlain, strings[s].stringParsed);
			
			//	... and store it
			this.storeString(updateString, dataNodeName, ("FEED:" + dataNodeName), updateStringIds.contains(feedString.id));
			
			//	let's not knock ourselves out completely with the update workload
			Thread.yield();
		}
		System.out.println("    - update done");
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String action = request.getParameter(ACTION_PARAMETER);
		
		//	request for string feed
		if (FEED_ACTION_NAME.equals(action))
			this.doFeedStrings(request, response);
		
		//	ID-based request for strings
		else if (GET_ACTION_NAME.equals(action))
			this.doGetStrings(request, response);
		
		//	search for strings
		else if (FIND_ACTION_NAME.equals(action))
			this.doFindStrings(request, response);
		
		//	other action, to be handled by super class
		else super.doGet(request, response);
	}
	
	private void doFeedStrings(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		long updatedSince = -1;
		String updatedSinceString = request.getParameter(UPDATED_SINCE_ATTRIBUTE);
		if (updatedSinceString == null)
			updatedSince = 0;
		else try {
			updatedSince = parseTime(updatedSinceString);
		}
		catch (NumberFormatException nfe) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid time limit.");
			return;
		}
		
		if (updatedSince < 0) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid time limit.");
			return;
		}
		
		InternalPooledStringIterator strings = this.getStringFeed(updatedSince);
		try {
			response.setCharacterEncoding(ENCODING);
			response.setContentType("text/xml");
			
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
			this.sendStrings(strings, bw, updatedSince, false);
			bw.flush();
		}
		finally {
			strings.close();
		}
	}
	
	private void doGetStrings(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String[] ids = request.getParameterValues(ID_PARAMETER);
		if ((ids == null) || (ids.length == 0)) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "String ID missing.");
			return;
		}
		
		InternalPooledStringIterator strings = this.getInternalStrings(ids);
		
		try {
			String format = request.getParameter(FORMAT_PARAMETER);
			Transformer formatter = null;
			if (format != null) try {
				formatter = XsltUtils.getTransformer(new File(this.dataFolder, format), !"force".equals(request.getParameter("formatCache")));
			}
			catch (IOException ioe) {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST, ("Invalid format: " + format));
				return;
			}
			response.setCharacterEncoding(ENCODING);
			response.setContentType("text/xml");
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
			if (formatter != null)
				bw = new BufferedWriter(XsltUtils.wrap(bw, formatter));
			this.sendStrings(strings, bw, -1, true);
			bw.flush();
			bw.close();
		}
		finally {
			strings.close();
		}
	}
	
	private void doFindStrings(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String[] fullTextQueryPredicates = request.getParameterValues(QUERY_PARAMETER);
		boolean disjunctive = OR_COMBINE.equals(request.getParameter(COMBINE_PARAMETER));
		
		Properties detailPredicates = new Properties();
		String typeQueryPredicate = request.getParameter(TYPE_PARAMETER);
		if (typeQueryPredicate != null)
			detailPredicates.setProperty(TYPE_PARAMETER, typeQueryPredicate);
		String userQueryPredicate = request.getParameter(USER_PARAMETER);
		if (userQueryPredicate != null)
			detailPredicates.setProperty(USER_PARAMETER, userQueryPredicate);
		
		if (this.isUsingIndexTable)
			this.addIndexPredicates(request, detailPredicates);
		
		if (((fullTextQueryPredicates == null) || (fullTextQueryPredicates.length == 0)) && detailPredicates.isEmpty()) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Empty query.");
			return;
		}
		
		InternalPooledStringIterator strings = this.findInternalStrings(fullTextQueryPredicates, disjunctive, detailPredicates);
		System.out.println("StringPoolServlet: REST search complete");
		
		try {
			String format = request.getParameter(FORMAT_PARAMETER);
			Transformer formatter = null;
			if ((format != null) && !CONCISE_FORMAT.equals(format)) try {
				formatter = XsltUtils.getTransformer(new File(this.dataFolder, format), !"force".equals(request.getParameter("formatCache")));
			}
			catch (IOException ioe) {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST, ("Invalid format: " + format));
				return;
			}
			response.setCharacterEncoding(ENCODING);
			response.setContentType("text/xml");
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
			if (formatter != null)
				bw = new BufferedWriter(XsltUtils.wrap(bw, formatter));
			this.sendStrings(strings, bw, -1, !CONCISE_FORMAT.equals(format));
			bw.flush();
			bw.close();
		}
		finally {
			strings.close();
		}
	}
	
	/**
	 * Extract sub class specific detail predicates from a search query, to be
	 * matched against the index table. This method is only relevant if the sub
	 * class uses the index table. This default implementation does nothing, sub
	 * classes are welcome to overwrite it as needed.
	 * @param request the HTTP request to extract the detail predicates from
	 * @param detailPredicates the Properties object to store the predicates in
	 */
	protected void addIndexPredicates(HttpServletRequest request, Properties detailPredicates) {}
	
	private void sendStrings(InternalPooledStringIterator strings, BufferedWriter bw, long updatedSince, boolean full) throws IOException {
		if (!strings.hasNextString()) {
			bw.write("<" + this.stringSetNodeType);
			bw.write(this.xmlNamespaceAttribute);
			if (updatedSince != -1)
				bw.write(" " + UPDATED_SINCE_ATTRIBUTE + "=\"" + TIMESTAMP_DATE_FORMAT.format(new Date(updatedSince)) + "\"");
			bw.write("/>");
			return;
		}
		
		bw.write("<" + this.stringSetNodeType);
		bw.write(this.xmlNamespaceAttribute);
		bw.write(" xmlns:mods=\"http://www.loc.gov/mods/v3\"");
		if (updatedSince != -1)
			bw.write(" " + UPDATED_SINCE_ATTRIBUTE + "=\"" + TIMESTAMP_DATE_FORMAT.format(new Date(updatedSince)) + "\"");
		bw.write(">");
		bw.newLine();
		while (strings.hasNextString())
			this.writeString(strings.getNextString(), bw, (updatedSince != -1), full);
		bw.write("</" + this.stringSetNodeType + ">");
		bw.newLine();
	}
	
	private void writeString(InternalPooledString string, BufferedWriter bw, boolean isFeed, boolean full) throws IOException {
		bw.write("<" + this.stringNodeType);
		bw.write(" " + STRING_ID_ATTRIBUTE + "=\"" + string.id + "\"");
		if ((string.canonicalId != null) && (string.canonicalId.length() != 0))
			bw.write(" " + CANONICAL_STRING_ID_ATTRIBUTE + "=\"" + string.canonicalId + "\"");
		bw.write(" " + CREATE_TIME_ATTRIBUTE + "=\"" + TIMESTAMP_DATE_FORMAT.format(new Date(string.createTime)) + "\"");
		if (!isFeed) {
			bw.write(" " + CREATE_DOMAIN_ATTRIBUTE + "=\"" + AnnotationUtils.escapeForXml(string.createDomain) + "\"");
			bw.write(" " + CREATE_USER_ATTRIBUTE + "=\"" + AnnotationUtils.escapeForXml(string.createUser) + "\"");
		}
		bw.write(" " + UPDATE_TIME_ATTRIBUTE + "=\"" + TIMESTAMP_DATE_FORMAT.format(new Date(string.updateTime)) + "\"");
		bw.write(" " + DELETED_ATTRIBUTE + "=\"" + (string.deleted ? "true" : "false") + "\"");
		
		//	we are writing the feed, omit content (both raw and parsed) to reduce data volume
		if (isFeed) {
			bw.write(" " + LOCAL_UPDATE_TIME_ATTRIBUTE + "=\"" + TIMESTAMP_DATE_FORMAT.format(new Date(string.localUpdateTime)) + "\"");
			if ((string.parseChecksum != null) && (string.parseChecksum.length() != 0))
				bw.write(" " + PARSE_CHECKSUM_ATTRIBUTE + "=\"" + string.parseChecksum + "\"");
			bw.write("/>");
		}
		
		//	we are delivering data, write content
		else {
			bw.write(" " + UPDATE_DOMAIN_ATTRIBUTE + "=\"" + AnnotationUtils.escapeForXml(string.updateDomain, true) + "\"");
			bw.write(" " + UPDATE_USER_ATTRIBUTE + "=\"" + AnnotationUtils.escapeForXml(string.updateUser, true) + "\"");
			
			if (!full && (string.parseChecksum.length() != 0))
				bw.write(" " + PARSE_CHECKSUM_ATTRIBUTE + "=\"" + string.parseChecksum + "\"");
			bw.write(">");
			bw.newLine();
			bw.write("<" + this.stringPlainNodeType + ">" + AnnotationUtils.escapeForXml(string.stringPlain) + "</" + this.stringPlainNodeType + ">");
			bw.newLine();
			if (full) {
				MutableAnnotation parsedString = ((string.stringParsed == null) ? this.getParsedString(string.id) : string.stringParsed);
				if (parsedString != null) {
					bw.write("<" + this.stringParsedNodeType + ">");
					bw.newLine();
					AnnotationUtils.writeXML(parsedString, bw);
					bw.newLine();
					bw.write("</" + this.stringParsedNodeType + ">");
					bw.newLine();
				}
			}
			bw.write("</" + this.stringNodeType + ">");
		}
		bw.newLine();
	}
	
//	/* (non-Javadoc)
//	 * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
//	 */
//	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//		String action = request.getParameter(ACTION_PARAMETER);
//		
//		//	upload of string(s)
//		if (UPDATE_ACTION_NAME.equals(action)) {
//			String strings = request.getParameter(STRINGS_PARAMETER);
//			if (strings == null) {
//				response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No strings specified");
//				return;
//			}
//			String encoding = request.getCharacterEncoding();
//			String contentType = request.getHeader("Content-Type");
//			if (encoding == null) {
//				if ((contentType != null) && (contentType.indexOf("charset=") != -1)) {
//					encoding = contentType.substring(contentType.indexOf("charset=") + "charset=".length());
//					if (encoding.indexOf(' ') != -1)
//						encoding = encoding.substring(0, encoding.indexOf(' ')).trim();
//				}
//			}
//			if ((encoding == null) || !encodingAliases.contains(encoding))
//				strings = URLDecoder.decode(URLEncoder.encode(strings, ((encoding == null) ? "ISO-8859-1" : encoding)), ENCODING);
//			
//			String format = request.getParameter(FORMAT_PARAMETER);
//			if (format == null) {
//				int formatPeekEnd = 0;
//				while ((formatPeekEnd < Math.min(strings.length(), 16)) && (strings.charAt(formatPeekEnd) > 32))
//					formatPeekEnd++;
//				format = (strings.substring(0, formatPeekEnd).matches("\\<([a-zA-Z]+\\:)?" + stringSetNodeType + ".*") ? "xml" : "txt");
//			}
//			
//			String user = request.getParameter(USER_PARAMETER);
//			if ((user == null) || (user.length() == 0))
//				user = "Anonymous";
//			
//			BufferedReader stringReader = new BufferedReader(new StringReader(strings));
//			
//			//	plain text or xml input, seems we can use it
//			if ("txt".equalsIgnoreCase(format) || "xml".equalsIgnoreCase(format))
//				this.doUpdateStrings(this.readStrings(stringReader, format, System.currentTimeMillis(), user), ("POST/" + format.toUpperCase() + ":" + request.getRemoteAddr()), response);
//			
//			//	invalid format
//			else response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid format");
//		}
//		
//		//	other action, to be handled by super class
//		else super.doPost(request, response);
//	}
//	private static HashSet encodingAliases = new HashSet();
//	static {
//		encodingAliases.add(ENCODING);
//		try {
//			Charset cs = Charset.forName(ENCODING);
//			encodingAliases.addAll(cs.aliases());
//		}
//		catch (Exception e) {
//			e.printStackTrace();
//		}
//	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.OnnServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPost(final HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//		String action = request.getParameter(ACTION_PARAMETER);
		String action = request.getPathInfo();
		if (action == null)
			action = request.getParameter(ACTION_PARAMETER);
		else {
			while (action.startsWith("/"))
				action = action.substring(1);
			if (action.indexOf('/') != -1)
				action = action.substring(0, action.indexOf('/'));
		}
		
		//	update of string (delete/undelete or update of canonical string ID)
		if (UPDATE_ACTION_NAME.equals(action)) {
			
			//	get user name
			String user = request.getHeader(USER_PARAMETER);
			if ((user == null) || (user.length() == 0))
				user = "Anonymous";
			final String updateUser = user;
			
			//	read data & do update
			Reader r = new BufferedReader(new InputStreamReader(request.getInputStream(), ENCODING));
			final ArrayList ipsList = new ArrayList();
			xmlParser.stream(r, new TokenReceiver() {
				public void close() throws IOException {}
				public void storeToken(String token, int treeDepth) throws IOException {
					if (xmlGrammar.isTag(token) && xmlGrammar.isSingularTag(token) && stringNodeType.equals(xmlGrammar.getType(token))) {
						TreeNodeAttributeSet tnas = TreeNodeAttributeSet.getTagAttributes(token, xmlGrammar);
						String stringId = tnas.getAttribute(STRING_ID_ATTRIBUTE);
						String canonicalStringId = tnas.getAttribute(CANONICAL_STRING_ID_ATTRIBUTE);
						boolean deleted = "true".equals(tnas.getAttribute(DELETED_ATTRIBUTE, "false"));
						if (stringId != null) {
							InternalPooledString ips = doPlainUpdate(stringId, canonicalStringId, deleted, domainName, updateUser, System.currentTimeMillis(), ("POST:" + request.getRemoteAddr()), null, domainName);
							if (ips != null)
								ipsList.add(ips);
						}
					}
				}
			});
			
			//	send result
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
			if (ipsList.isEmpty())
				bw.write("<" + this.stringSetNodeType + "/>");
			else {
				bw.write("<" + this.stringSetNodeType + ">");
				bw.newLine();
				for (int s = 0; s < ipsList.size(); s++)
					this.writeString(((InternalPooledString) ipsList.get(s)), bw, false, false);
				bw.write("</" + this.stringSetNodeType + ">");
			}
		}
		
		//	other action, to be handled by super class
		else super.doPost(request, response);
	}
	
	private InternalPooledString doPlainUpdate(String stringId, String canonicalStringId, boolean deleted, String domain, String user, long updateTime, String updateSource, InternalPooledString localString, String localUpdateSourceDomain) throws IOException {
		InternalPooledString existingString = localString;
		if (existingString == null)
			existingString = this.getInternalString(stringId);
		if (existingString == null)
			return null;
		if ((existingString.deleted == deleted) && ((canonicalStringId == null) || (canonicalStringId.length() == 0) || canonicalStringId.equals(existingString.canonicalId)))
			return existingString;
		
		long localUpdateTime = System.currentTimeMillis();
		String query = "UPDATE " + this.parsedStringTableName + " SET" + 
				" " + DELETED_COLUMN_NAME + " = '" + (deleted ? "D" : " ") + "'" + 
				(((canonicalStringId == null) || (canonicalStringId.length() == 0)) ? "" : (", " + CANONICAL_STRING_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(canonicalStringId) + "'") + ", " + CANONICAL_STRING_ID_HASH_COLUMN_NAME + " = " + canonicalStringId.hashCode()) +
				", " + UPDATE_USER_COLUMN_NAME + " = '" + EasyIO.sqlEscape(user) + "'" + 
				", " + UPDATE_DOMAIN_COLUMN_NAME + " = '" + EasyIO.sqlEscape(domain) + "'" + 
				", " + UPDATE_TIME_COLUMN_NAME + " = " + updateTime + 
				", " + LOCAL_UPDATE_TIME_COLUMN_NAME + " = " + localUpdateTime + 
				" WHERE " + STRING_ID_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(stringId) + "'" +
					" AND " + STRING_ID_HASH_COLUMN_NAME + " = " + stringId.hashCode() + 
				";";
		try {
			int updated = this.io.executeUpdateQuery(query);
			if (updated == 0) // we don't have this one at all
				return null;
			else {
				this.writeHistoryEntry(existingString.id, existingString.id.hashCode(), updateTime, domain, user, localUpdateTime, localUpdateSourceDomain, updateSource);
				return new InternalPooledString(existingString, canonicalStringId, deleted, domain, user, updateTime, localUpdateTime);
			}
		}
		catch (SQLException sqle) {
			System.out.println("ParsedStringPool: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while simple updating parsed string.");
			System.out.println("  query was " + query);
			return null;
		}
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPut(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		BufferedReader dataReader = new BufferedReader(new InputStreamReader(request.getInputStream(), ENCODING));
		char[] dataStringPeekBuffer = new char[16];
		dataReader.mark(dataStringPeekBuffer.length + 1);
		int rspLength = dataReader.read(dataStringPeekBuffer);
		dataReader.reset();
		int rspEnd = 0;
		while ((rspEnd < rspLength) && (dataStringPeekBuffer[rspEnd] > 32))
			rspEnd++;
		String dataStringPeek = new String(dataStringPeekBuffer, 0, rspEnd);
		
		String format = request.getHeader("Data-Format");
		if (format == null)
			format = (dataStringPeek.matches("\\<([a-zA-Z]+\\:)?" + this.stringSetNodeType + ".*") ? "xml" : "txt");
		
		String user = request.getHeader("User-Name");
		if ((user == null) || (user.length() == 0))
			user = "Anonymous";
		
//		this.doUpdateStrings(this.readStrings(dataReader, format, System.currentTimeMillis(), user), user, ("PUT/" + format.toUpperCase() + ":" + request.getRemoteAddr()), response);
		InternalPooledString[] strings = this.readStrings(dataReader, format, System.currentTimeMillis(), user);
		long requestTime = System.currentTimeMillis();
		String source = ("PUT/" + format.toUpperCase() + ":" + request.getRemoteAddr());
		int newStringCount = 0;
		int updateStringCount = 0;
		ArrayList stringList = new ArrayList();
		for (int s = 0; s < strings.length; s++) {
			if (strings[s].stringPlain == null) {
//				InternalPooledString string = this.unDeleteString(strings[s], updateSource);
//				if (string == null)
//					continue;
//				stringList.add(string);
//				updateStringCount++;
			}
			else {
				InternalPooledString string = this.doStringUpdate(strings[s], user, source);
				if (string == null)
					continue;
				stringList.add(string);
				if ((string.updateTime == string.localUpdateTime) && (requestTime <= string.updateTime)) {
					 if (string.createTime >= requestTime)
						 newStringCount++;
					 else updateStringCount++;
				}
			}
		}
		
		response.setCharacterEncoding(ENCODING);
		response.setContentType("text/xml");
		//	impossible_TODO include "unmodified" response in case nothing changes ==> impossible, "304 - Not Modified" has different semantics
		response.setStatus((newStringCount == 0) ? HttpServletResponse.SC_OK : HttpServletResponse.SC_CREATED);
		
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
		if (stringList.isEmpty()) {
			bw.write("<" + this.stringSetNodeType);
			bw.write(this.xmlNamespaceAttribute);
			bw.write(" " + CREATED_ATTRIBUTE + "=\"0\"");
			bw.write(" " + UPDATED_ATTRIBUTE + "=\"0\"");
			bw.write("/>");
		}
		else {
			bw.write("<" + this.stringSetNodeType);
			bw.write(this.xmlNamespaceAttribute);
			bw.write(" " + CREATED_ATTRIBUTE + "=\"" + newStringCount + "\"");
			bw.write(" " + UPDATED_ATTRIBUTE + "=\"" + updateStringCount + "\"");
			bw.write(">");
			bw.newLine();
			for (int r = 0; r < stringList.size(); r++) {
				InternalPooledString string = ((InternalPooledString) stringList.get(r));
				bw.write("<" + this.stringNodeType);
				bw.write(" " + STRING_ID_ATTRIBUTE + "=\"" + string.id + "\"");
				if (string.parseError.length() != 0)
					bw.write(" " + PARSE_ERROR_ATTRIBUTE + "=\"" + string.parseError + "\"");
				else if (string.parseChecksum.length() != 0)
					bw.write(" " + PARSE_CHECKSUM_ATTRIBUTE + "=\"" + string.parseChecksum + "\"");
				bw.write(" " + CREATE_TIME_ATTRIBUTE + "=\"" + TIMESTAMP_DATE_FORMAT.format(new Date(string.createTime)) + "\"");
				bw.write(" " + UPDATE_TIME_ATTRIBUTE + "=\"" + TIMESTAMP_DATE_FORMAT.format(new Date(string.updateTime)) + "\"");
				if ((string.updateTime == string.localUpdateTime) && (requestTime <= string.updateTime)) {
					 if (string.createTime >= requestTime)
						 bw.write(" " + CREATED_ATTRIBUTE + "=\"true\"");
					 else bw.write(" " + UPDATED_ATTRIBUTE + "=\"true\"");
				}
				bw.write(" " + DELETED_ATTRIBUTE + "=\"" + (string.deleted ? "true" : "false") + "\"");
				if (string.stringPlain == null)
					bw.write("/>");
				else {
					bw.write(">");
					bw.newLine();
					bw.write("<" + this.stringPlainNodeType + ">" + AnnotationUtils.escapeForXml(string.stringPlain) + "</" + this.stringPlainNodeType + ">");
					bw.newLine();
					bw.write("</" + this.stringNodeType + ">");
				}
				bw.newLine();
			}
			bw.write("</" + this.stringSetNodeType + ">");
			bw.newLine();
		}
		bw.flush();
	}
	
	private InternalPooledString[] readStrings(BufferedReader stringReader, String format, final long requestTime, final String userName) throws IOException {
		final ArrayList strings = new ArrayList();
		
		//	plain text input (PUT or POST only)
		if ("txt".equalsIgnoreCase(format)) {
			String stringString;
			while ((stringString = stringReader.readLine()) != null) {
				if (stringString.trim().length() != 0)
					strings.add(new InternalPooledString(requestTime, this.domainName, userName, stringString));
			}
		}
		
		//	xml input (PUT update or FEED resolver query - user is null in latter case only)
		else if ("xml".equalsIgnoreCase(format)) {
			xmlParser.stream(stringReader, new TokenReceiver() {
				private String canonicalStringId = null;
				private StringBuffer stringPlainBuffer = null;
				private String stringPlain = null;
				private String createDomain = null;
				private String createUser = null;
				private StringBuffer stringParsedBuffer = null;
				private MutableAnnotation stringParsed = null;
				private String updateDomain = null;
				private String updateUser = null;
				public void close() throws IOException {}
				public void storeToken(String token, int treeDepth) throws IOException {
//					System.out.println("SERVLET GOT TOKEN: " + token);
					if (xmlGrammar.isTag(token)) {
						String type = xmlGrammar.getType(token);
						type = type.substring(type.indexOf(':') + 1);
						boolean isEndTag = xmlGrammar.isEndTag(token);
						if (stringNodeType.equals(type)) {
							if (isEndTag) {
								if (this.stringPlain != null) {
									String createDomain = ((userName == null) ? this.createDomain : StringPoolServlet.this.domainName);
									String createUser = ((userName == null) ? this.createUser : userName);
									String updateDomain = ((userName == null) ? this.updateDomain : StringPoolServlet.this.domainName);
									String updateUser = ((userName == null) ? this.updateUser : userName);
									if ((updateDomain == null) || (updateUser == null)) {
										this.stringParsed = null;
										updateDomain = null;
										updateUser = null;
									}
									if ((createDomain != null) && (createUser != null))
										strings.add(new InternalPooledString(requestTime, createDomain, createUser, requestTime, updateDomain, updateUser, 0, this.canonicalStringId, false, this.stringPlain, this.stringParsed));
								}
								this.canonicalStringId = null;
								this.createDomain = null;
								this.createUser = null;
								this.updateDomain = null;
								this.updateUser = null;
							}
							else {
								if (!xmlGrammar.isSingularTag(token)) {
									TreeNodeAttributeSet tnas = TreeNodeAttributeSet.getTagAttributes(token, xmlGrammar);
									this.canonicalStringId = tnas.getAttribute(CANONICAL_STRING_ID_ATTRIBUTE);
									this.createDomain = tnas.getAttribute(CREATE_DOMAIN_ATTRIBUTE);
									this.createUser = tnas.getAttribute(CREATE_USER_ATTRIBUTE);
									this.updateDomain = tnas.getAttribute(UPDATE_DOMAIN_ATTRIBUTE);
									this.updateUser = tnas.getAttribute(UPDATE_USER_ATTRIBUTE);
								}
//								if (xmlGrammar.isSingularTag(token)) {
//									String stringId = tnas.getAttribute(STRING_ID_ATTRIBUTE);
//									String canonicalStringId = tnas.getAttribute(CANONICAL_STRING_ID_ATTRIBUTE);
//									boolean deleted = "true".equals(tnas.getAttribute(DELETED_ATTRIBUTE, "false"));
//									if (stringId != null)
//										strings.add(new InternalPooledString(stringId, canonicalStringId, requestTime, StringPoolServlet.this.domainName, userName, deleted));
//								}
//								else {
//									this.canonicalStringId = tnas.getAttribute(CANONICAL_STRING_ID_ATTRIBUTE);
//									this.createDomain = tnas.getAttribute(CREATE_DOMAIN_ATTRIBUTE);
//									this.createUser = tnas.getAttribute(CREATE_USER_ATTRIBUTE);
//									this.updateDomain = tnas.getAttribute(UPDATE_DOMAIN_ATTRIBUTE);
//									this.updateUser = tnas.getAttribute(UPDATE_USER_ATTRIBUTE);
//								}
							}
							this.stringPlainBuffer = null;
							this.stringPlain = null;
							this.stringParsedBuffer = null;
							this.stringParsed = null;
						}
						else if (stringPlainNodeType.equals(type)) {
							if (isEndTag) {
								if (this.stringPlainBuffer.length() != 0)
									this.stringPlain = this.stringPlainBuffer.toString();
								this.stringPlainBuffer = null;
							}
							else this.stringPlainBuffer = new StringBuffer();
						}
						else if (stringParsedNodeType.equals(type)) {
							if (isEndTag) {
								if (this.stringParsedBuffer.length() != 0) {
									this.stringParsed = Gamta.newDocument(Gamta.newTokenSequence(null, Gamta.INNER_PUNCTUATION_TOKENIZER));
									SgmlDocumentReader.readDocument(new StringReader(this.stringParsedBuffer.toString()), this.stringParsed);
								}
								this.stringParsedBuffer = null;
							}
							else this.stringParsedBuffer = new StringBuffer();
						}
						else if (this.stringParsedBuffer != null)
							this.stringParsedBuffer.append(token);
					}
					else if (this.stringPlainBuffer != null)
						this.stringPlainBuffer.append(xmlGrammar.unescape(token));
					else if (this.stringParsedBuffer != null)
						this.stringParsedBuffer.append(token.trim());
				}
			});
		}
		
		//	finally ...
		return ((InternalPooledString[]) strings.toArray(new InternalPooledString[strings.size()]));
	}
	
//	private void doUpdateStrings(InternalPooledString[] strings, String user, String updateSource, HttpServletResponse response) throws IOException {
//		long requestTime = System.currentTimeMillis();
//		int newStringCount = 0;
//		int updateStringCount = 0;
//		ArrayList stringList = new ArrayList();
//		for (int s = 0; s < strings.length; s++) {
//			if (strings[s].stringPlain == null) {
////				InternalPooledString string = this.unDeleteString(strings[s], updateSource);
////				if (string == null)
////					continue;
////				stringList.add(string);
////				updateStringCount++;
//			}
//			else {
//				InternalPooledString string = this.updateString(strings[s], user, updateSource);
//				if (string == null)
//					continue;
//				stringList.add(string);
//				if ((string.updateTime == string.localUpdateTime) && (requestTime <= string.updateTime)) {
//					 if (string.createTime >= requestTime)
//						 newStringCount++;
//					 else updateStringCount++;
//				}
//			}
//		}
//		
//		response.setCharacterEncoding(ENCODING);
//		response.setContentType("text/xml");
//		//	impossible_TODO include "unmodified" response in case nothing changes ==> impossible, "304 - Not Modified" has different semantics
//		response.setStatus((newStringCount == 0) ? HttpServletResponse.SC_OK : HttpServletResponse.SC_CREATED);
//		
//		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
//		if (stringList.isEmpty()) {
//			bw.write("<" + this.stringSetNodeType);
//			bw.write(this.xmlNamespaceAttribute);
//			bw.write(" " + CREATED_ATTRIBUTE + "=\"0\"");
//			bw.write(" " + UPDATED_ATTRIBUTE + "=\"0\"");
//			bw.write("/>");
//		}
//		else {
//			bw.write("<" + this.stringSetNodeType);
//			bw.write(this.xmlNamespaceAttribute);
//			bw.write(" " + CREATED_ATTRIBUTE + "=\"" + newStringCount + "\"");
//			bw.write(" " + UPDATED_ATTRIBUTE + "=\"" + updateStringCount + "\"");
//			bw.write(">");
//			bw.newLine();
//			for (int r = 0; r < stringList.size(); r++) {
//				InternalPooledString string = ((InternalPooledString) stringList.get(r));
//				bw.write("<" + this.stringNodeType);
//				bw.write(" " + STRING_ID_ATTRIBUTE + "=\"" + string.id + "\"");
//				if (string.parseError.length() != 0)
//					bw.write(" " + PARSE_ERROR_ATTRIBUTE + "=\"" + string.parseError + "\"");
//				else if (string.parseChecksum.length() != 0)
//					bw.write(" " + PARSE_CHECKSUM_ATTRIBUTE + "=\"" + string.parseChecksum + "\"");
//				bw.write(" " + CREATE_TIME_ATTRIBUTE + "=\"" + TIMESTAMP_DATE_FORMAT.format(new Date(string.createTime)) + "\"");
//				bw.write(" " + UPDATE_TIME_ATTRIBUTE + "=\"" + TIMESTAMP_DATE_FORMAT.format(new Date(string.updateTime)) + "\"");
//				if ((string.updateTime == string.localUpdateTime) && (requestTime <= string.updateTime)) {
//					 if (string.createTime >= requestTime)
//						 bw.write(" " + CREATED_ATTRIBUTE + "=\"true\"");
//					 else bw.write(" " + UPDATED_ATTRIBUTE + "=\"true\"");
//				}
//				bw.write(" " + DELETED_ATTRIBUTE + "=\"" + (string.deleted ? "true" : "false") + "\"");
//				if (string.stringPlain == null)
//					bw.write("/>");
//				else {
//					bw.write(">");
//					bw.newLine();
//					bw.write("<" + this.stringPlainNodeType + ">" + AnnotationUtils.escapeForXml(string.stringPlain) + "</" + this.stringPlainNodeType + ">");
//					bw.newLine();
//					bw.write("</" + this.stringNodeType + ">");
//				}
//				bw.newLine();
//			}
//			bw.write("</" + this.stringSetNodeType + ">");
//			bw.newLine();
//		}
//		bw.flush();
//	}
//	
//	private InternalPooledString unDeleteString(InternalPooledString updateString, String updateSource) throws IOException {
//		
//		//	check if string already exists
//		InternalPooledString existingString = this.getInternalString(updateString.id);
//		
//		//	does not exist, nothing to delete or un-delete
//		if (existingString == null)
//			return null;
//		
//		//	do update
//		return this.unDeleteString(updateString, existingString, updateSource);
//	}
//	
//	private InternalPooledString unDeleteString(InternalPooledString updateString, InternalPooledString existingString, String updateSource) throws IOException {
//		
//		//	no changes
//		if (updateString.deleted == existingString.deleted)
//			return null;
//		
//		//	update deletion flag
//		long updateTime = System.currentTimeMillis();
//		updateString = new InternalPooledString(updateString.id, existingString.parseChecksum, existingString.createTime, existingString.createDomain, existingString.createUser, updateString.updateTime, updateString.updateDomain, updateString.updateUser, updateTime, updateString.deleted, existingString.stringPlain);
//		
//		//	store string an return it
//		return (this.storeString(updateString, this.domainName, updateSource, true) ? updateString : null);
//	}
//	
	private InternalPooledString doStringUpdate(InternalPooledString updateString, String user, String updateSource) throws IOException {
		System.out.println("STRING: " + updateString.stringPlain);
		
		//	check if string already exists
		InternalPooledString existingString = this.getInternalString(updateString.id);
		
		//	update to existing string
		if (existingString != null) {
			
			//	parse given, but erroneous, can only be an implicit un-deletion
			if (updateString.parseError.length() != 0) {
				if (existingString.deleted)
//					return this.unDeleteString(updateString, existingString, updateSource);
					return this.doPlainUpdate(existingString.id, null, false, updateString.updateDomain, updateString.updateUser, updateString.updateTime, updateSource, existingString, this.domainName);
				else return new InternalPooledString(existingString.createTime, existingString.createDomain, existingString.createUser, existingString.updateTime, existingString.updateDomain, existingString.updateUser, existingString.localUpdateTime, false, existingString.stringPlain, updateString.parseError);
			}
			
			//	no parse given, we're done here safe for implicit un-deletions
			else if (updateString.stringParsed == null) {
				if (existingString.deleted)
//					return this.unDeleteString(updateString, existingString, updateSource);
					return this.doPlainUpdate(existingString.id, null, false, updateString.updateDomain, updateString.updateUser, updateString.updateTime, updateSource, existingString, this.domainName);
				else return existingString;
			}
			
			//	parse unchanged, we're done here safe for implicit un-deletions
			else if (updateString.parseChecksum.equals(existingString.parseChecksum)) {
				if (existingString.deleted)
//					return this.unDeleteString(updateString, existingString, updateSource);
					return this.doPlainUpdate(existingString.id, null, false, updateString.updateDomain, updateString.updateUser, updateString.updateTime, updateSource, existingString, this.domainName);
				else return existingString;
			}
			
			//	update (cannot be a deletion, only an implicit un-deletion, which works automatically with the update)
			else {
				long updateTime = System.currentTimeMillis();
				updateString = new InternalPooledString(existingString.createTime, existingString.createDomain, existingString.createUser, updateTime, updateString.updateDomain, updateString.updateUser, updateTime, ((updateString.canonicalId.length() == 0) ? existingString.canonicalId : updateString.canonicalId), false, existingString.stringPlain, updateString.stringParsed);
				
				//	store string an return it
				return (this.storeString(updateString, this.domainName, updateSource, true) ? updateString : null);
			}
		}
		
		//	new string
		else {
			
			//	get timestamp & create string object
			long createTime = System.currentTimeMillis();
			if (updateString.parseError.length() == 0)
				updateString = new InternalPooledString(createTime, updateString.createDomain, updateString.createUser, createTime, updateString.updateDomain, updateString.updateUser, createTime, null, false, updateString.stringPlain, updateString.stringParsed);
			else updateString = new InternalPooledString(createTime, updateString.createDomain, updateString.createUser, createTime, updateString.updateDomain, updateString.updateUser, createTime, false, updateString.stringPlain, updateString.parseError);
			
			//	store string an return it
			return (this.storeString(updateString, this.domainName, updateSource, false) ? updateString : null);
		}
	}
	
	private boolean storeString(InternalPooledString string, String updateSourceDomain, String updateSource, boolean isUpdate) {
		
		//	store parse if given
		if (string.stringParsed != null) try {
			this.storeParsedString(string.id, string.stringParsed);
			
			String query = null;
			
			ParsedStringIndexData psid = new ParsedStringIndexData(string.id);
			if (this.isUsingIndexTable)
				this.extendIndexData(psid, string.stringParsed);
			
			//	write index table entry
			if (psid.containsData())
				synchronized (this.parsedStringIndexTableName) {
					try {
						query = "UPDATE " + this.parsedStringIndexTableName + " SET" + 
								" " + psid.updates.toString() + 
								" WHERE " + STRING_ID_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(string.id) + "'" +
								" AND " + STRING_ID_HASH_COLUMN_NAME + " = " + string.id.hashCode() + 
								";";
						int updated = this.io.executeUpdateQuery(query);
						if (updated == 0) {
							query = ("INSERT INTO " + this.parsedStringIndexTableName + 
									" (" + psid.columns.toString() + ")" +
									" VALUES" +
									" (" + psid.values.toString() + ")" +
									";");
							this.io.executeUpdateQuery(query);
						}
					}
					catch (SQLException sqle) {
						System.out.println("ParsedStringPool: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while indexing parsed string.");
						System.out.println("  query was " + query);
					}
				}
			
			//	write identifier table entries
			ParsedStringIdentifierData psidd = new ParsedStringIdentifierData(string.id);
			this.extendIdentifierData(psidd, string.stringParsed);
			if (psidd.containsData())
				for (int i = 0; i < psidd.updateParts.size(); i++) {
					try {
						query = "UPDATE " + this.parsedStringIdentifierTableName + " " + 
								psidd.updateParts.get(i) +
								";";
						int updated = this.io.executeUpdateQuery(query);
						if (updated == 0) {
							query = ("INSERT INTO " + this.parsedStringIdentifierTableName + " " +
									psidd.insertParts.getProperty(psidd.updateParts.get(i)) +
									";");
							this.io.executeUpdateQuery(query);
						}
					}
					catch (SQLException sqle) {
						System.out.println("ParsedStringPool: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while indexing parsed string identifiers.");
						System.out.println("  query was " + query);
					}
				}
		}
		catch (IOException ioe) {
			System.out.println("ParsedStringPool: " + ioe.getClass().getName() + " (" + ioe.getMessage() + ") while storing string.");
			ioe.printStackTrace(System.out);
			return false;
		}
		
		String updateQuery = "UPDATE " + this.parsedStringTableName + " SET " + 
			STRING_TYPE_COLUMN_NAME + " = '" + ((string.type == null) ? "" : EasyIO.sqlEscape(string.type)) + "'" +
			", " +
			PARSE_CHECKSUM_COLUMN_NAME + " = '" + EasyIO.sqlEscape(string.parseChecksum) + "'" +
			", " +
			UPDATE_TIME_COLUMN_NAME + " = " + string.updateTime +
			", " +
			UPDATE_DOMAIN_COLUMN_NAME + " = '" + EasyIO.sqlEscape(string.updateDomain) + "'" +
			", " +
			UPDATE_USER_COLUMN_NAME + " = '" + EasyIO.sqlEscape(string.updateUser) + "'" +
			", " +
			LOCAL_UPDATE_TIME_COLUMN_NAME + " = " + string.localUpdateTime +
			", " +
			LOCAL_UPDATE_DOMAIN_COLUMN_NAME + " = '" + EasyIO.sqlEscape(updateSourceDomain) + "'" +
			((string.canonicalId.length() == 0) ? "" :
				(", " + 
				CANONICAL_STRING_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(string.canonicalId) + "'" +
				", " +
				CANONICAL_STRING_ID_HASH_COLUMN_NAME + " = " + string.canonicalId.hashCode())
			) +
			", " +
			DELETED_COLUMN_NAME + " = '" + (string.deleted ? "D" : " ") + "'" +
			" WHERE " + STRING_ID_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(string.id) + "'" +
				" AND " + STRING_ID_HASH_COLUMN_NAME + " = " + string.id.hashCode() +
			";";
		
		String insertQuery = "INSERT INTO " + this.parsedStringTableName + " (" + 
					STRING_ID_COLUMN_NAME + 
					", " + 
					STRING_ID_HASH_COLUMN_NAME + 
					", " + 
					CANONICAL_STRING_ID_COLUMN_NAME + 
					", " + 
					CANONICAL_STRING_ID_HASH_COLUMN_NAME + 
					", " + 
					STRING_TYPE_COLUMN_NAME + 
					", " + 
					PARSE_CHECKSUM_COLUMN_NAME + 
					", " + 
					CREATE_TIME_COLUMN_NAME + 
					", " + 
					CREATE_DOMAIN_COLUMN_NAME + 
					", " + 
					CREATE_USER_COLUMN_NAME + 
					", " + 
					LOCAL_CREATE_DOMAIN_COLUMN_NAME + 
					", " + 
					UPDATE_TIME_COLUMN_NAME + 
					", " + 
					UPDATE_DOMAIN_COLUMN_NAME + 
					", " + 
					UPDATE_USER_COLUMN_NAME + 
					", " + 
					LOCAL_UPDATE_TIME_COLUMN_NAME + 
					", " + 
					LOCAL_UPDATE_DOMAIN_COLUMN_NAME + 
					", " + 
					DELETED_COLUMN_NAME + 
					", " + 
					STRING_TEXT_COLUMN_NAME +
				") VALUES (" +
					"'" + EasyIO.sqlEscape(string.id) + "'" +
					", " + 
					string.id.hashCode() + 
					", " +
					"'" + EasyIO.sqlEscape(string.canonicalId) + "'" +
					", " + 
					string.canonicalId.hashCode() + 
					", " +
					"'" + ((string.type == null) ? "" : EasyIO.sqlEscape(string.type)) + "'" +
					", " +
					"'" + EasyIO.sqlEscape(string.parseChecksum) + "'" +
					", " + 
					string.createTime + 
					", " + 
					"'" + EasyIO.sqlEscape(string.createDomain) + "'" +
					", " + 
					"'" + EasyIO.sqlEscape(string.createUser) + "'" +
					", " + 
					"'" + EasyIO.sqlEscape(updateSourceDomain) + "'" +
					", " + 
					string.updateTime + 
					", " + 
					"'" + EasyIO.sqlEscape(string.updateDomain) + "'" +
					", " + 
					"'" + EasyIO.sqlEscape(string.updateUser) + "'" +
					", " + 
					System.currentTimeMillis() + 
					", " +
					"'" + EasyIO.sqlEscape(updateSourceDomain) + "'" +
					", " +
					"'" + (string.deleted ? "D" : " ") + "'" +
					", " + 
					"'" + EasyIO.sqlEscape(string.stringPlain) + "'" +
				");";
		
		/*
		 * Synchronized to prevent duplicate inserts even with DB systems that
		 * do not support PK constraints. Using UPSERT/MERGE would be better,
		 * but that is not part of SQL as yet. This approach cab still cause
		 * duplicate history entries, but that is a minor concern.
		 */
		synchronized (this.parsedStringTableName) {
			try {
				int updated = 0;
				
				//	try updating existing string
				try {
					updated = this.io.executeUpdateQuery(updateQuery);
					
					//	we did update a record, so we're done here
					if (updated != 0) {
						this.writeHistoryEntry(string.id, string.id.hashCode(), string.updateTime, string.updateDomain, string.updateUser, string.localUpdateTime, updateSourceDomain, updateSource);
						return true;
					}
				}
				catch (SQLException sqle) {
					System.out.println("ParsedStringPool: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while updating string.");
					System.out.println("  query was " + updateQuery);
				}
				
				//	we did not update any record, insert a new one
				updated = this.io.executeUpdateQuery(insertQuery);
				if (updated == 1) {
					this.writeHistoryEntry(string.id, string.id.hashCode(), string.updateTime, string.updateDomain, string.updateUser, string.localUpdateTime, updateSourceDomain, updateSource);
					return true;
				}
				else return false;
			}
			catch (SQLException sqle) {
				System.out.println("ParsedStringPool: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while storing string.");
				System.out.println("  query was " + insertQuery);
				return false;
			}
		}
//		String query;
//		if (isUpdate) {
//			query = "UPDATE " + this.parsedStringTableName + " SET " + 
//					STRING_TYPE_COLUMN_NAME + " = '" + ((string.type == null) ? "" : EasyIO.sqlEscape(string.type)) + "'" +
//					", " +
//					PARSE_CHECKSUM_COLUMN_NAME + " = '" + EasyIO.sqlEscape(string.parseChecksum) + "'" +
//					", " +
//					UPDATE_TIME_COLUMN_NAME + " = " + string.updateTime +
//					", " +
//					UPDATE_DOMAIN_COLUMN_NAME + " = '" + EasyIO.sqlEscape(string.updateDomain) + "'" +
//					", " +
//					UPDATE_USER_COLUMN_NAME + " = '" + EasyIO.sqlEscape(string.updateUser) + "'" +
//					", " +
//					LOCAL_UPDATE_TIME_COLUMN_NAME + " = " + string.localUpdateTime +
//					", " +
//					LOCAL_UPDATE_DOMAIN_COLUMN_NAME + " = '" + EasyIO.sqlEscape(updateSourceDomain) + "'" +
//					((string.canonicalId.length() == 0) ? "" :
//						(", " + 
//						CANONICAL_STRING_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(string.canonicalId) + "'" +
//						", " +
//						CANONICAL_STRING_ID_HASH_COLUMN_NAME + " = " + string.canonicalId.hashCode())
//					) +
//					", " +
//					DELETED_COLUMN_NAME + " = '" + (string.deleted ? "D" : " ") + "'" +
//					" WHERE " + STRING_ID_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(string.id) + "'" +
//						" AND " + STRING_ID_HASH_COLUMN_NAME + " = " + string.id.hashCode() +
//					";";
//		}
//		else {
//			query = "INSERT INTO " + this.parsedStringTableName + " (" + 
//						STRING_ID_COLUMN_NAME + 
//						", " + 
//						STRING_ID_HASH_COLUMN_NAME + 
//						", " + 
//						CANONICAL_STRING_ID_COLUMN_NAME + 
//						", " + 
//						CANONICAL_STRING_ID_HASH_COLUMN_NAME + 
//						", " + 
//						STRING_TYPE_COLUMN_NAME + 
//						", " + 
//						PARSE_CHECKSUM_COLUMN_NAME + 
//						", " + 
//						CREATE_TIME_COLUMN_NAME + 
//						", " + 
//						CREATE_DOMAIN_COLUMN_NAME + 
//						", " + 
//						CREATE_USER_COLUMN_NAME + 
//						", " + 
//						LOCAL_CREATE_DOMAIN_COLUMN_NAME + 
//						", " + 
//						UPDATE_TIME_COLUMN_NAME + 
//						", " + 
//						UPDATE_DOMAIN_COLUMN_NAME + 
//						", " + 
//						UPDATE_USER_COLUMN_NAME + 
//						", " + 
//						LOCAL_UPDATE_TIME_COLUMN_NAME + 
//						", " + 
//						LOCAL_UPDATE_DOMAIN_COLUMN_NAME + 
//						", " + 
//						DELETED_COLUMN_NAME + 
//						", " + 
//						STRING_TEXT_COLUMN_NAME +
//					") VALUES (" +
//						"'" + EasyIO.sqlEscape(string.id) + "'" +
//						", " + 
//						string.id.hashCode() + 
//						", " +
//						"'" + EasyIO.sqlEscape(string.canonicalId) + "'" +
//						", " + 
//						string.canonicalId.hashCode() + 
//						", " +
//						"'" + ((string.type == null) ? "" : EasyIO.sqlEscape(string.type)) + "'" +
//						", " +
//						"'" + EasyIO.sqlEscape(string.parseChecksum) + "'" +
//						", " + 
//						string.createTime + 
//						", " + 
//						"'" + EasyIO.sqlEscape(string.createDomain) + "'" +
//						", " + 
//						"'" + EasyIO.sqlEscape(string.createUser) + "'" +
//						", " + 
//						"'" + EasyIO.sqlEscape(updateSourceDomain) + "'" +
//						", " + 
//						string.updateTime + 
//						", " + 
//						"'" + EasyIO.sqlEscape(string.updateDomain) + "'" +
//						", " + 
//						"'" + EasyIO.sqlEscape(string.updateUser) + "'" +
//						", " + 
//						System.currentTimeMillis() + 
//						", " +
//						"'" + EasyIO.sqlEscape(updateSourceDomain) + "'" +
//						", " +
//						"'" + (string.deleted ? "D" : " ") + "'" +
//						", " + 
//						"'" + EasyIO.sqlEscape(string.stringPlain) + "'" +
//					");";
//		}
//		try {
//			int updated = this.io.executeUpdateQuery(query);
//			if (updated == 1) {
//				this.writeHistoryEntry(string.id, string.id.hashCode(), string.updateTime, string.updateDomain, string.updateUser, string.localUpdateTime, updateSourceDomain, updateSource);
//				return true;
//			}
//			else return false;
//		}
//		catch (SQLException sqle) {
//			System.out.println("ParsedStringPool: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while storing string.");
//			System.out.println("  query was " + query);
//			return false;
//		}
	}
	
	/**
	 * Extend the index entry of a parsed string with sub class specific
	 * attributes. This default implementation does nothing, sub classes are
	 * welcome to overwrite it as needed.
	 * @param indexData the index data object to extend
	 * @param stringParsed the parsed string to extend the index data from
	 */
	protected void extendIndexData(ParsedStringIndexData indexData, MutableAnnotation stringParsed) {}
	
	/**
	 * Object encapsulating index entries for a parsed string.
	 * 
	 * @author sautter
	 */
	protected static class ParsedStringIndexData {
		StringBuffer columns = new StringBuffer();
		StringBuffer values = new StringBuffer();
		StringBuffer updates = new StringBuffer();
		ParsedStringIndexData(String id) {
			
			//	start column strings
			this.columns.append(STRING_ID_COLUMN_NAME);
			this.values.append("'" + EasyIO.sqlEscape(id) + "'");
			
			this.columns.append(", " + STRING_ID_HASH_COLUMN_NAME);
			this.values.append(", " + id.hashCode());
		}
		
		/**
		 * Add an attribute to index. The name has to match the respective
		 * column in the index table, and the value has to fit into the column.
		 * @param name the name of the attribute to index
		 * @param value the value to index
		 */
		public void addIndexAttribute(String name, String value) {
			this.columns.append(", " + name);
			this.values.append(", '" + EasyIO.sqlEscape(value.toLowerCase()) + "'");
			if (this.updates.length() != 0)
				this.updates.append(", ");
			this.updates.append(name + " = '" + EasyIO.sqlEscape(value.toLowerCase()) + "'");
		}
		
		boolean containsData() {
			return (this.updates.length() != 0);
		}
	}
	
	/**
	 * Extend the external identifier index entry of a parsed string with sub
	 * class specific attributes. This default implementation does nothing, sub
	 * classes are welcome to overwrite it as needed.
	 * @param identifierData the identifier data object to extend
	 * @param stringParsed the parsed string to extend the identifier data from
	 */
	protected void extendIdentifierData(ParsedStringIdentifierData identifierData, MutableAnnotation stringParsed) {}
	
	/**
	 * Object encapsulating external identifiers for a parsed string.
	 * 
	 * @author sautter
	 */
	protected static class ParsedStringIdentifierData {
		StringVector updateParts = new StringVector();
		Properties insertParts = new Properties();
		String id;
		ParsedStringIdentifierData(String id) {
			this.id = id;
		}
		
		/**
		 * Add an external identifier to index.
		 * @param type the type of the external identifier to index
		 * @param value the value of the external identifier
		 */
		public void addIdentifier(String type, String value) {
			String updatePart = "SET " + ID_VALUE_COLUMN_NAME + " = '" + EasyIO.sqlEscape(value.toLowerCase()) + "'" +
					" WHERE " + STRING_ID_HASH_COLUMN_NAME + " = " + this.id.hashCode() +
					" AND " + STRING_ID_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(this.id) + "'" +
					" AND " + ID_TYPE_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(type.toLowerCase()) + "'" +
					"";
			this.updateParts.addElementIgnoreDuplicates(updatePart);
			this.insertParts.setProperty(updatePart, "(" +
					STRING_ID_COLUMN_NAME + 
					", " + 
					STRING_ID_HASH_COLUMN_NAME + 
					", " + 
					ID_TYPE_COLUMN_NAME + 
					", " + 
					ID_VALUE_COLUMN_NAME + 
					") VALUES (" +
					"'" + EasyIO.sqlEscape(this.id) + "'" + 
					", " + 
					this.id.hashCode() + 
					", " + 
					"'" + EasyIO.sqlEscape(type.toLowerCase()) + "'" + 
					", " + 
					"'" + EasyIO.sqlEscape(value.toLowerCase()) + "'" + 
					")" +
					"");
		}
		
		boolean containsData() {
			return (this.updateParts.size() != 0);
		}
	}
	
	private void writeHistoryEntry(String stringId, int stringIdHash, long updateTime, String updateDomain, String updateUser, long localUpdateTime, String updateSourceDomain, String updateSource) {
		String query = "INSERT INTO " + this.parsedStringHistoryTableName + " (" + 
					STRING_ID_COLUMN_NAME + 
					", " + 
					STRING_ID_HASH_COLUMN_NAME + 
					", " + 
					UPDATE_TIME_COLUMN_NAME + 
					", " + 
					UPDATE_DOMAIN_COLUMN_NAME + 
					", " + 
					UPDATE_USER_COLUMN_NAME + 
					", " + 
					LOCAL_UPDATE_TIME_COLUMN_NAME + 
					", " + 
					LOCAL_UPDATE_DOMAIN_COLUMN_NAME + 
					", " + 
					UPDATE_SOURCE_COLUMN_NAME +
				") VALUES (" +
					"'" + EasyIO.sqlEscape(stringId) + "'" +
					", " + 
					stringIdHash + 
					", " + 
					updateTime + 
					", " +
					"'" + EasyIO.sqlEscape(updateDomain) + "'" +
					", " +
					"'" + EasyIO.sqlEscape(updateUser) + "'" +
					", " + 
					localUpdateTime + 
					", " +
					"'" + EasyIO.sqlEscape(updateSourceDomain) + "'" +
					", " +
					"'" + EasyIO.sqlEscape(updateSource) + "'" +
				");";
		try {
			this.io.executeUpdateQuery(query);
		}
		catch (SQLException sqle) {
			System.out.println("ParsedStringPool: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while storing string history.");
			System.out.println("  query was " + query);
		}
	}
	
	private File parsedStringsFolder;
	private void storeParsedString(String id, MutableAnnotation modsString) throws IOException {
		
		//	create two-layer storage folder structure
		String primaryFolderName = id.substring(0, 2);
		File primaryFolder = new File(this.parsedStringsFolder, primaryFolderName);
		if (!primaryFolder.exists()) primaryFolder.mkdir();
		
		String secondaryFolderName = id.substring(2, 4);
		File secondaryFolder = new File(primaryFolder, secondaryFolderName);
		if (!secondaryFolder.exists()) secondaryFolder.mkdir();
		
		//	create actual string file
		File stringFile = new File(secondaryFolder, (id + ".xml"));
		
		//	file exists (we have an update), make way
		if (stringFile.exists()) {
			File oldStringFile = new File(secondaryFolder, (id + "." + System.currentTimeMillis() + ".xml"));
			stringFile.renameTo(oldStringFile);
			stringFile = new File(secondaryFolder, (id + ".xml"));
		}
		stringFile.createNewFile();
		
		//	write parse
		Writer out = new OutputStreamWriter(new FileOutputStream(stringFile), ENCODING);
		AnnotationUtils.writeXML(modsString, out);
		out.flush();
		out.close();
	}
	
	private MutableAnnotation getParsedString(String id) {
		String primaryFolderName = id.substring(0, 2);
		String secondaryFolderName = id.substring(2, 4);
		try {
			File stringFile = new File(this.parsedStringsFolder, (primaryFolderName + "/" + secondaryFolderName + "/" + id + ".xml"));
			Reader stringIn = new InputStreamReader(new FileInputStream(stringFile), ENCODING);
			MutableAnnotation stringParsed = Gamta.newDocument(Gamta.newTokenSequence(null, Gamta.INNER_PUNCTUATION_TOKENIZER));
			SgmlDocumentReader.readDocument(stringIn, stringParsed);
			stringIn.close();
			return stringParsed;
		}
		catch (FileNotFoundException fnfe) {
			return null;
		}
		catch (IOException ioe) {
			ioe.printStackTrace(System.out);
			return null;
		}
	}
	
	private InternalPooledString getInternalString(String id) throws IOException {
		String[] ids = {id};
		InternalPooledStringIterator stringi = this.getInternalStrings(ids);
		InternalPooledString string = (stringi.hasNextString() ? stringi.getNextString() : null);
		stringi.close();
		return string;
	}
	
	private InternalPooledStringIterator getInternalStrings(String[] ids) throws IOException {
		if (ids.length == 0)
			return new InternalPooledStringIterator() {
				public void close() {}
				public boolean hasNextString() {
					return false;
				}
				public InternalPooledString getNextString() {
					return null;
				}
			};
		
		StringBuffer idString = new StringBuffer();
		StringBuffer idHashString = new StringBuffer();
		for (int i = 0; i < ids.length; i++) {
			if (i != 0) {
				idString.append(", ");
				idHashString.append(", ");
			}
			idString.append("'" + EasyIO.sqlEscape(ids[i]) + "'");
			idHashString.append(ids[i].hashCode());
		}
		
		String fields = (
				STRING_ID_COLUMN_NAME + 
				", " + 
				CANONICAL_STRING_ID_COLUMN_NAME + 
				", " + 
				PARSE_CHECKSUM_COLUMN_NAME + 
				", " +
				CREATE_TIME_COLUMN_NAME + 
				", " +
				CREATE_DOMAIN_COLUMN_NAME + 
				", " +
				CREATE_USER_COLUMN_NAME + 
				", " +
				UPDATE_TIME_COLUMN_NAME + 
				", " +
				UPDATE_DOMAIN_COLUMN_NAME + 
				", " +
				UPDATE_USER_COLUMN_NAME + 
				", " +
				LOCAL_UPDATE_TIME_COLUMN_NAME + 
				", " +
				DELETED_COLUMN_NAME + 
				", " +
				STRING_TEXT_COLUMN_NAME
				);
		String query = "SELECT " + fields +
				" FROM " + this.parsedStringTableName +
				" WHERE " + STRING_ID_HASH_COLUMN_NAME + " IN (" + idHashString.toString() + ")" + 
					" AND " + STRING_ID_COLUMN_NAME + " IN (" + idString.toString() + ")" + 
				";";
		
		SqlQueryResult sqr = null;
		try {
			sqr = this.io.executeSelectQuery(query);
		}
		catch (SQLException sqle) {
			System.out.println("ParsedStringPool: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while getting strings.");
			System.out.println("  query was " + query);
		}
		return new SqlParsedStringIterator(sqr, false);
	}
	
	private InternalPooledStringIterator getInternalLinkedStrings(String canonicalId) throws IOException {
		if ((canonicalId == null) || (canonicalId.trim().length() == 0))
			return new InternalPooledStringIterator() {
				public void close() {}
				public boolean hasNextString() {
					return false;
				}
				public InternalPooledString getNextString() {
					return null;
				}
			};
		
		String fields = (
				STRING_ID_COLUMN_NAME + 
				", " + 
				CANONICAL_STRING_ID_COLUMN_NAME + 
				", " + 
				PARSE_CHECKSUM_COLUMN_NAME + 
				", " +
				CREATE_TIME_COLUMN_NAME + 
				", " +
				CREATE_DOMAIN_COLUMN_NAME + 
				", " +
				CREATE_USER_COLUMN_NAME + 
				", " +
				UPDATE_TIME_COLUMN_NAME + 
				", " +
				UPDATE_DOMAIN_COLUMN_NAME + 
				", " +
				UPDATE_USER_COLUMN_NAME + 
				", " +
				LOCAL_UPDATE_TIME_COLUMN_NAME + 
				", " +
				DELETED_COLUMN_NAME + 
				", " +
				STRING_TEXT_COLUMN_NAME
				);
		String query = "SELECT " + fields +
				" FROM " + this.parsedStringTableName +
//				" WHERE " + CANONICAL_STRING_ID_HASH_COLUMN_NAME + " = " + canonicalId.hashCode() + 
//					" AND " + CANONICAL_STRING_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(canonicalId) + "'" + 
				" WHERE (" +
					CANONICAL_STRING_ID_HASH_COLUMN_NAME + " = " + canonicalId.hashCode() + 
					" AND " + 
					CANONICAL_STRING_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(canonicalId) + "'" +
				") OR (" +
					STRING_ID_HASH_COLUMN_NAME + " = " + canonicalId.hashCode() + 
					" AND " + 
					STRING_ID_COLUMN_NAME + " = '" + EasyIO.sqlEscape(canonicalId) + "'" +
				");";
		
		SqlQueryResult sqr = null;
		try {
			sqr = this.io.executeSelectQuery(query);
		}
		catch (SQLException sqle) {
			System.out.println("ParsedStringPool: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while getting linked strings.");
			System.out.println("  query was " + query);
		}
		return new SqlParsedStringIterator(sqr, false);
	}
	
	private InternalPooledStringIterator findInternalStrings(String[] fullTextQueryPredicates, boolean disjunctive, Properties detailPredicates) throws IOException {
		StringBuffer where = new StringBuffer(disjunctive ? "(1=0" : "(1=1");
		if (fullTextQueryPredicates != null)
			for (int q = 0; q < fullTextQueryPredicates.length; q++) {
				if ((fullTextQueryPredicates[q].length() == 0) || fullTextQueryPredicates[q].matches("[\\s\\%]++"))
					continue;
				where.append(" " + (disjunctive ? "OR" : "AND") + " data." + STRING_TEXT_COLUMN_NAME + " LIKE '%" + EasyIO.prepareForLIKE(fullTextQueryPredicates[q]) + "%'");
			}
		where.append(")");
		if (disjunctive && (where.length() < 6))
			where = new StringBuffer("(1=1)");
		
		boolean indexPredicates = false;
		boolean identifierPredicates = false;
		String type = detailPredicates.getProperty(TYPE_PARAMETER);
		if (type != null)
			where.append(" AND (data." + STRING_TYPE_COLUMN_NAME + " LIKE '%" + EasyIO.prepareForLIKE(type) + "%')");
		String user = detailPredicates.getProperty(USER_PARAMETER);
		if (user != null)
			where.append(" AND ((data." + CREATE_USER_ATTRIBUTE + " LIKE '%" + EasyIO.prepareForLIKE(user) + "%') OR (data." + UPDATE_USER_ATTRIBUTE + " LIKE '%" + EasyIO.prepareForLIKE(user) + "%'))");
		for (Iterator dpit = detailPredicates.keySet().iterator(); dpit.hasNext();) {
			String detailName = ((String) dpit.next());
			if (STRING_TYPE_COLUMN_NAME.equals(detailName))
				continue;
			String detailValue = detailPredicates.getProperty(detailName);
			if ((detailValue == null) || (detailValue.length() == 0) || detailValue.matches("[\\s\\%]++"))
				continue;
			if (detailName.startsWith("ID-")) {
				where.append(" AND (ids." + ID_TYPE_COLUMN_NAME + " LIKE '%" + EasyIO.prepareForLIKE(detailName.substring("ID-".length()).toLowerCase()) + "%')");
				where.append(" AND (ids." + ID_VALUE_COLUMN_NAME + " LIKE '" + EasyIO.prepareForLIKE(detailValue.toLowerCase()) + "')");
				identifierPredicates = true;
			}
			else {
				where.append(" AND (idx." + detailName + " LIKE '%" + EasyIO.prepareForLIKE(detailValue.toLowerCase()) + "%')");
				indexPredicates = true;
			}
		}
		
		//	catch empty predicates
		if (where.length() < 6)
			throw new IOException("Invalid query");
		
		//	filter out strings that are not self-canonical
		where.append(" AND (data." + STRING_ID_HASH_COLUMN_NAME + " = data." + CANONICAL_STRING_ID_HASH_COLUMN_NAME + " OR data." + CANONICAL_STRING_ID_COLUMN_NAME + " = '')");
		
		//	assemble query
		String query;
		
		//	assemble fields
		String fields = (
				"data." + STRING_ID_COLUMN_NAME + 
				", " +
				"data." + CANONICAL_STRING_ID_COLUMN_NAME + 
				", " + 
				"data." + PARSE_CHECKSUM_COLUMN_NAME + 
				", " +
				"data." + CREATE_TIME_COLUMN_NAME + 
				", " +
				"data." + CREATE_DOMAIN_COLUMN_NAME + 
				", " +
				"data." + CREATE_USER_COLUMN_NAME + 
				", " +
				"data." + UPDATE_TIME_COLUMN_NAME + 
				", " +
				"data." + UPDATE_DOMAIN_COLUMN_NAME + 
				", " +
				"data." + UPDATE_USER_COLUMN_NAME + 
				", " +
				"data." + LOCAL_UPDATE_TIME_COLUMN_NAME + 
				", " +
				"data." + DELETED_COLUMN_NAME + 
				", " +
				"data." + STRING_TEXT_COLUMN_NAME
				);
		
		//	detail predicates, perform join
		if (indexPredicates || identifierPredicates)
			query = "SELECT " + fields +
				" FROM " + this.parsedStringTableName + " data" + (indexPredicates ? (", " + this.parsedStringIndexTableName + " idx") : "") + (identifierPredicates ? (", " + this.parsedStringIdentifierTableName + " ids") : "") +
				" WHERE 1=1" +
				(indexPredicates ? (
					" AND (data." + STRING_ID_HASH_COLUMN_NAME + " = idx." + STRING_ID_HASH_COLUMN_NAME + ")" +
					" AND (data." + STRING_ID_COLUMN_NAME + " = idx." + STRING_ID_COLUMN_NAME + ")"
				) : "") +
				(identifierPredicates ? (
						" AND (data." + STRING_ID_HASH_COLUMN_NAME + " = ids." + STRING_ID_HASH_COLUMN_NAME + ")" +
						" AND (data." + STRING_ID_COLUMN_NAME + " = ids." + STRING_ID_COLUMN_NAME + ")"
					) : "") +
				" AND " + where + 
				";";
		
		//	full text predicates only, no need for join
		else query = "SELECT " + fields +
				" FROM " + this.parsedStringTableName + " data" +
				" WHERE " + where + 
				";";
		
		System.out.println("Query is " + query);
		SqlQueryResult sqr = null;
		try {
			System.out.println("StringPoolServlet: searching ...");
			sqr = this.io.executeSelectQuery(query);
			System.out.println("StringPoolServlet: search complete");
		}
		catch (SQLException sqle) {
			System.out.println("ParsedStringPool: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while searching strings.");
			System.out.println("  query was " + query);
		}
		System.out.println("StringPoolServlet: search result wrapped");
		return new SqlParsedStringIterator(sqr, false);
	}
	
	/*
	 * leave the feed as is, as this is sufficient for update notification -
	 * actual data comes with ID resolution queries fetching the strings in
	 * update batches
	 */
	private InternalPooledStringIterator getStringFeed(long addedSince) throws IOException {
		String query = "SELECT " + STRING_ID_COLUMN_NAME + ", " + CANONICAL_STRING_ID_COLUMN_NAME + ", " + PARSE_CHECKSUM_COLUMN_NAME + ", " + CREATE_TIME_COLUMN_NAME + ", " + UPDATE_TIME_COLUMN_NAME + ", " + LOCAL_UPDATE_TIME_COLUMN_NAME + ", " + DELETED_COLUMN_NAME + 
				" FROM " + this.parsedStringTableName +
				" WHERE " + LOCAL_UPDATE_TIME_COLUMN_NAME + " > " + addedSince + 
//				" ORDER BY " + LOCAL_UPDATE_TIME_COLUMN_NAME + " DESC" + 
				" ORDER BY " + LOCAL_UPDATE_TIME_COLUMN_NAME + // we actually need oldest first, so if something fails during feed-based update, the last received is older than the first missing.
				";";
		
		SqlQueryResult sqr = null;
		try {
			sqr = this.io.executeSelectQuery(query);
		}
		catch (SQLException sqle) {
			System.out.println("ParsedStringPool: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while loading string feed.");
			System.out.println("  query was " + query);
		}
		return new SqlParsedStringIterator(sqr, true);
	}
	
	private static LinkedList checksumDigesters = new LinkedList();
	private static MessageDigest getMessageDigest() throws IOException {
		synchronized (checksumDigesters) {
			if (checksumDigesters.size() != 0)
				return ((MessageDigest) checksumDigesters.removeFirst());
			else try {
				return MessageDigest.getInstance("MD5");
			}
			catch (NoSuchAlgorithmException nsae) {
				System.out.println(nsae.getClass().getName() + " (" + nsae.getMessage() + ") while creating checksum digester.");
				nsae.printStackTrace(System.out); // should not happen, but Java don't know ...
				throw new IOException(nsae.getMessage());
			}
		}
	}
	private static void returnMessageDigest(MessageDigest md) {
		if (md == null)
			return;
		synchronized (checksumDigesters) {
			checksumDigesters.addLast(md);
		}
	}
	private static String getChecksum(String string) throws IOException {
		MessageDigest md = null;
		try {
			md = getMessageDigest();
			md.reset();
			
			md.update(string.getBytes("UTF-8"));
			byte[] checksumBytes = md.digest();
			
			return new String(RandomByteSource.getHexCode(checksumBytes));
		}
		finally {
			returnMessageDigest(md);
		}
	}
	private static String getChecksum(QueriableAnnotation string) throws IOException {
		MessageDigest md = null;
		try {
			md = getMessageDigest();
			md.reset();
			StringWriter stringReceiver = new StringWriter();
			BufferedWriter stringWriter = new BufferedWriter(stringReceiver) {
				public void newLine() throws IOException {
					//	ignore newlines to eliminate whitespace between tags
				}
			};
			AnnotationUtils.writeXML(string, stringWriter);
			stringWriter.flush();
			md.update(stringReceiver.toString().getBytes("UTF-8"));
			byte[] checksumBytes = md.digest();
			
			return new String(RandomByteSource.getHexCode(checksumBytes));
		}
		finally {
			returnMessageDigest(md);
		}
	}
	
	private static HashSet urlEtcPrefixes = new HashSet();
	static {
		urlEtcPrefixes.add("http");
		urlEtcPrefixes.add("ftp");
		urlEtcPrefixes.add("doi");
		urlEtcPrefixes.add("handle");
		urlEtcPrefixes.add("hdl");
		urlEtcPrefixes.add("urn");
		urlEtcPrefixes.add("uri");
		urlEtcPrefixes.add("svn");
	}
	private static HashSet urlEtcPrefixesSpaceAfter = new HashSet();
	static {
		urlEtcPrefixesSpaceAfter.add("doi");
		urlEtcPrefixesSpaceAfter.add("handle");
		urlEtcPrefixesSpaceAfter.add("hdl");
	}
	private static String getNormalizedString(String string) {
		
		//	normalize dashes, high commas, etc.
		string = string.replaceAll("[\\´\\`\\’\\‘\\‚]", "'");
		string = string.replaceAll("[\\—\\—]", "-");
		//	not_TODO maybe normalize letters as well
		
		//	catch empty strings
		if (string.trim().length() == 0)
			return "";
		
		//	normalize string
		MutableTokenSequence stringTokens = Gamta.newTokenSequence(string.trim(), Gamta.INNER_PUNCTUATION_TOKENIZER);
		MutableAnnotation stringDoc = Gamta.newDocument(stringTokens);
		
		//	deal with hyphenation
		Gamta.normalizeParagraphStructure(stringDoc);
		
		String lastValue = stringDoc.valueAt(0);
		boolean inUrlEtc = false;
		boolean inUrlEtcAcceptOneSpace = false;
		StringBuffer result = new StringBuffer(lastValue);
		
		for (int t = 1; t < stringDoc.size(); t++) {
			String value = stringDoc.valueAt(t);
			
			if (stringDoc.getWhitespaceAfter(t - 1).length() == 0) {
				if (":".equals(value) && urlEtcPrefixes.contains(lastValue)) {
					inUrlEtc = true;
					inUrlEtcAcceptOneSpace = urlEtcPrefixesSpaceAfter.contains(lastValue);
				}
			}
			else if (inUrlEtcAcceptOneSpace)
				inUrlEtcAcceptOneSpace = false;
			else inUrlEtc = false;
			
			if (Gamta.insertSpace(lastValue, value) && (stringDoc.getWhitespaceAfter(t - 1).length() != 0) && !inUrlEtc)
				result.append(" ");
			
			result.append(value);
			
			lastValue = value;
		}
		System.out.println("INPUT: " + string);
		System.out.println("NORMALIZED: " + result);
		return result.toString();
	}
	
	
	//	TODO_ne externalize this, or maybe not
	private class InternalPooledString {
		final String id;
		final String canonicalId;
		final long createTime;
		final String createDomain;
		final String createUser;
		final long updateTime;
		final String updateDomain;
		final String updateUser;
		final long localUpdateTime;
		final boolean deleted;
		final String stringPlain;
		final String parseChecksum;
		final String parseError;
		final MutableAnnotation stringParsed;
		final String type;
		
		//	copy constructor for return from doPlainUpdate
		InternalPooledString(InternalPooledString existingString, String canonicalStringId, boolean deleted, String updateDomain, String updateUser, long updateTime, long localUpdateTime) {
			this.id = existingString.id;
			this.canonicalId = (((canonicalStringId == null) || (canonicalStringId.length() == 0)) ? existingString.canonicalId : canonicalStringId);
			this.parseChecksum = existingString.parseChecksum;
			this.parseError = existingString.parseError;
			this.createTime = existingString.createTime;
			this.createDomain = existingString.createDomain;
			this.createUser = existingString.createUser;
			this.updateTime = updateTime;
			this.updateDomain = this.checkString(updateDomain, StringPoolServlet.this.domainName);
			this.updateUser = this.checkString(updateUser, "Anonymous");
			this.localUpdateTime = localUpdateTime;
			this.deleted = deleted;
			this.stringPlain = existingString.stringPlain;
			this.stringParsed = existingString.stringParsed;
			this.type = existingString.type;
		}
		
//		//	used for deletion/un-deletion and canonical ID setting via PUT updates
//		InternalPooledString(String id, String canonicalId, long updateTime, String updateDomain, String updateUser, boolean deleted) {
//			this(id, canonicalId, null, -1, null, null, updateTime, updateDomain, updateUser, updateTime, deleted, null);
//		}
//		
		//	used for feed input
		InternalPooledString(String id, String canonicalId, String parseChecksum, long createTime, long updateTime, long localUpdateTime, boolean deleted) {
			this(id, canonicalId, parseChecksum, createTime, null, null, updateTime, null, null, localUpdateTime, deleted, null);
		}
		
		//	used for search results and feed output, and for delete/undelete
		InternalPooledString(String id, String canonicalId, String parseChecksum, long createTime, String createDomain, String createUser, long updateTime, String updateDomain, String updateUser, long localUpdateTime, boolean deleted, String stringPlain) {
			this.id = id;
			this.canonicalId = ((canonicalId == null) ? "" : canonicalId);
			this.parseChecksum = ((parseChecksum == null) ? "" : parseChecksum);
			this.parseError = "";
			this.createTime = createTime;
			this.createDomain = this.checkString(createDomain, StringPoolServlet.this.domainName);
			this.createUser = this.checkString(createUser, "Anonymous");
			this.updateTime = updateTime;
			this.updateDomain = this.checkString(updateDomain, StringPoolServlet.this.domainName);
			this.updateUser = this.checkString(updateUser, "Anonymous");
			this.localUpdateTime = localUpdateTime;
			this.deleted = deleted;
			this.stringPlain = stringPlain;
			this.stringParsed = null;
			this.type = null;
		}
		
		//	used for parse errors in PUT upload
		InternalPooledString(long createTime, String createDomain, String createUser, long updateTime, String updateDomain, String updateUser, long localUpdateTime, boolean deleted, String stringPlain, String parseError) throws IOException {
			this.createTime = createTime;
			this.createDomain = this.checkString(createDomain, StringPoolServlet.this.domainName);
			this.createUser = this.checkString(createUser, "Anonymous");
			this.updateTime = updateTime;
			this.updateDomain = this.checkString(updateDomain, StringPoolServlet.this.domainName);
			this.updateUser = this.checkString(updateUser, "Anonymous");
			this.localUpdateTime = localUpdateTime;
			this.deleted = deleted;
			this.stringPlain = getNormalizedString(stringPlain);
			this.id = getChecksum(this.stringPlain);
			this.canonicalId = "";
			this.type = null;
			this.stringParsed = null;
			this.parseChecksum = "";
			this.parseError = parseError;
		}
		
		//	used for PUT upload in plain TXT format
		InternalPooledString(long time, String domain, String user, String stringPlain) throws IOException {
			this(time, domain, user, time, domain, user, time, null, false, stringPlain, ((MutableAnnotation) null));
		}
		
		//	used for PUT upload in XML format, and storage on FEED import
		InternalPooledString(long createTime, String createDomain, String createUser, long updateTime, String updateDomain, String updateUser, long localUpdateTime, String canonicalStringId, boolean deleted, String stringPlain, MutableAnnotation stringParsed) throws IOException {
			this.createTime = createTime;
			this.createDomain = this.checkString(createDomain, StringPoolServlet.this.domainName);
			this.createUser = this.checkString(createUser, "Anonymous");
			this.updateTime = updateTime;
			this.updateDomain = this.checkString(updateDomain, StringPoolServlet.this.domainName);
			this.updateUser = this.checkString(updateUser, "Anonymous");
			this.localUpdateTime = localUpdateTime;
			this.deleted = deleted;
			this.stringPlain = getNormalizedString(stringPlain);
			this.id = getChecksum(this.stringPlain);
			this.canonicalId = ((canonicalStringId == null) ? "" : canonicalStringId);
			
			//	only plain string, no clue regarding type
			if (stringParsed == null) {
				this.type = null;
				this.stringParsed = null;
				this.parseChecksum = "";
				this.parseError = "";
			}
			
			//	parse given, check consistency of parse and plain string
			else {
				this.type = getStringType(stringParsed);
				String parseError = checkParsedString(this.id, this.stringPlain, stringParsed);
				this.stringParsed = ((parseError == null) ? stringParsed : null);
				this.parseChecksum = ((parseError == null) ? getChecksum(this.stringParsed) : "");
				this.parseError = ((parseError == null) ? "" : parseError);
			}
		}
		
		private final String checkString(String str, String def) {
			if (str == null)
				return def;
			str = str.trim();
			if (str.length() == 0)
				return def;
			else return str;
		}
	}
	
	/**
	 * Check the consistency of a plain string and its parsed version, and
	 * return an error message in case of an inconsistency. Returning null
	 * indicates the parse is consistent with the plain string. This default
	 * implementation does return null, sub classes are welcome to overwrite it
	 * as needed.
	 * @param stringId the ID of the string to check
	 * @param stringPlain the plain string
	 * @param stringParsed the parsed string to check against the plain version
	 * @return an error message in case of inconsistency, null otherwise
	 */
	protected String checkParsedString(String stringId, String stringPlain, MutableAnnotation stringParsed) {
		return null;
	}
	
	/**
	 * Obtain the type of a string from its parsed representation. If the type
	 * cannot be determined, this method should return null. This default
	 * implementation simply does return null, sub calsses are welcome to
	 * overwrite it as needed.
	 * @param stringParsed the parse to retrieve the string type from
	 * @return the type of the argument string, or null if the type cannot be
	 *         determined
	 */
	protected String getStringType(MutableAnnotation stringParsed) {
		return null;
	}
	
	//	TODO_ne externalize this, or maybe not
	private static abstract class InternalPooledStringIterator {
		abstract boolean hasNextString();
		abstract InternalPooledString getNextString();
		abstract void close();
		protected void finalize() throws Throwable {
			this.close();
		}
	}
	
	private class SqlParsedStringIterator extends InternalPooledStringIterator {
		private SqlQueryResult sqr;
		private boolean forFeed;
		private InternalPooledString next;
		SqlParsedStringIterator(SqlQueryResult sqr, boolean forFeed) {
			this.sqr = sqr;
			this.forFeed = forFeed;
		}
		public boolean hasNextString() {
			if (this.next != null)
				return true;
			else if (this.sqr == null)
				return false;
			else if (this.sqr.next()) {
//				this.next = new InternalPooledString(sqr.getString(0), sqr.getString(1), Long.parseLong(sqr.getString(2)), Long.parseLong(sqr.getString(3)), Long.parseLong(sqr.getString(4)), (this.forFeed ? null : sqr.getString(5)));
				
				this.next = (this.forFeed ? 
					new InternalPooledString(
							sqr.getString(0), 
							sqr.getString(1), 
							sqr.getString(2), 
							Long.parseLong(sqr.getString(3)), 
							Long.parseLong(sqr.getString(4)), 
							Long.parseLong(sqr.getString(5)),
							"D".equals(sqr.getString(6))
						)
					:
					new InternalPooledString(
							sqr.getString(0), 
							sqr.getString(1), 
							sqr.getString(2), 
							Long.parseLong(sqr.getString(3)), 
							sqr.getString(4), 
							sqr.getString(5), 
							Long.parseLong(sqr.getString(6)), 
							sqr.getString(7), 
							sqr.getString(8), 
							Long.parseLong(sqr.getString(9)), 
							"D".equals(sqr.getString(10)),
							sqr.getString(11)
						)
				);
				return true;
			}
			else return false;
		}
		public InternalPooledString getNextString() {
			if (this.hasNextString()) {
				InternalPooledString next = this.next;
				this.next = null;
				return next;
			}
			else return null;
		}
		public void close() {
			if (this.sqr == null)
				return;
			this.sqr.close();
			this.sqr = null;
		}
	}
	
	private class PooledStringIteratorLC implements PooledStringIterator {
		private InternalPooledStringIterator ipsi;
		private boolean isFeed;
		private boolean isConcise;
		PooledStringIteratorLC(InternalPooledStringIterator ipsi, boolean isFeed, boolean isConcise) {
			this.ipsi = ipsi;
			this.isFeed = isFeed;
			this.isConcise = isConcise;
		}
		public boolean hasNextString() {
			if (this.ipsi == null)
				return false;
			boolean hasNext = this.ipsi.hasNextString();
			if (!hasNext) {
				this.ipsi.close();
				this.ipsi = null;
			}
			return hasNext;
		}
		public PooledString getNextString() {
			if (this.ipsi == null)
				return null;
			InternalPooledString ips = this.ipsi.getNextString();
			if (ips == null) {
				this.ipsi.close();
				this.ipsi = null;
				return null;
			}
			else return new PooledStringLC(ips, this.isFeed, !this.isConcise);
		}
		public IOException getException() {
			return null;
		}
	}
	
	private class PooledStringLC extends PooledString {
		private String canonicalId = null;
		private String stringPlain = null;
		private String stringParsed = null;
		private String parseChecksum = null;
		private String parseError = null;
		private long createTime = -1;
		private String createDomain = null;
		private String createUser = null;
		private long updateTime = -1;
		private String updateDomain = null;
		private String updateUser = null;
		private long nodeUpdateTime = -1;
		private boolean created = false;
		private boolean updated = false;
		private boolean deleted = false;
		private PooledStringLC(InternalPooledString ips, boolean isFeed, boolean includeParse) {
			super(ips.id);
			this.createTime = ips.createTime;
			this.updateTime = ips.updateTime;
			this.deleted = ips.deleted;
			this.canonicalId = ((ips.canonicalId.length() == 0) ? null : ips.canonicalId);
			
			//	we are writing the feed, omit content (both raw and parsed) to reduce data volume
			if (isFeed) {
				this.nodeUpdateTime = ips.localUpdateTime;
				this.parseChecksum = (((ips.parseChecksum != null) && (ips.parseChecksum.length() != 0)) ? ips.parseChecksum : null);
			}
			
			//	we are delivering data, write content
			else {
				this.createDomain = ips.createDomain;
				this.createUser = ips.createUser;
				this.updateDomain = ips.updateDomain;
				this.updateUser = ips.updateUser;
				this.stringPlain = ips.stringPlain;
				if (includeParse) {
					MutableAnnotation parsedString = ((ips.stringParsed == null) ? getParsedString(ips.id) : ips.stringParsed);
					if (parsedString != null) try {
						StringWriter sw = new StringWriter();
						BufferedWriter bw = new BufferedWriter(sw);
						AnnotationUtils.writeXML(parsedString, bw);
						bw.flush();
						this.stringParsed = sw.toString();
					} catch (IOException ioe) {}
				}
				this.parseChecksum = (((ips.parseChecksum != null) && (ips.parseChecksum.length() != 0)) ? ips.parseChecksum : null);
			}
		}
		public String getStringPlain() {
			return this.stringPlain;
		}
		public String getStringParsed() {
			return this.stringParsed;
		}
		public String getCanonicalStringID() {
			return ((this.canonicalId == null) ? this.id : this.canonicalId);
		}
		public String getParseChecksum() {
			return this.parseChecksum;
		}
		public String getParseError() {
			return this.parseError;
		}
		public long getCreateTime() {
			return this.createTime;
		}
		public String getCreateDomain() {
			return this.createDomain;
		}
		public String getCreateUser() {
			return this.createUser;
		}
		public long getUpdateTime() {
			return this.updateTime;
		}
		public String getUpdateDomain() {
			return this.updateDomain;
		}
		public String getUpdateUser() {
			return this.updateUser;
		}
		public long getNodeUpdateTime() {
			return this.nodeUpdateTime;
		}
		public boolean wasCreated() {
			return this.created;
		}
		public boolean wasUpdated() {
			return this.updated;
		}
		public boolean isDeleted() {
			return this.deleted;
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolClient#getString(java.lang.String)
	 */
	public PooledString getString(String stringId) throws IOException {
		InternalPooledString ips = this.getInternalString(stringId);
		return ((ips == null) ? null : new PooledStringLC(ips, false, true));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolClient#getStrings(java.lang.String[])
	 */
	public PooledStringIterator getStrings(String[] stringIds) {
		try {
			InternalPooledStringIterator ipsi = this.getInternalStrings(stringIds);
			return new PooledStringIteratorLC(ipsi, false, false);
		}
		catch (IOException ioe) {
			return new ExceptionPSI(ioe);
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolClient#getLinkedStrings(java.lang.String)
	 */
	public PooledStringIterator getLinkedStrings(String canonicalStringId) throws IOException {
		try {
			InternalPooledStringIterator ipsi = this.getInternalLinkedStrings(canonicalStringId);
			return new PooledStringIteratorLC(ipsi, false, false);
		}
		catch (IOException ioe) {
			return new ExceptionPSI(ioe);
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolClient#findStrings(java.lang.String[], boolean, java.lang.String, java.lang.String)
	 */
	public PooledStringIterator findStrings(String[] textPredicates, boolean disjunctive, String type, String user) {
		return this.findStrings(textPredicates, disjunctive, type, user, false);
	}

	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolClient#findStrings(java.lang.String[], boolean, java.lang.String, java.lang.String, boolean)
	 */
	public PooledStringIterator findStrings(String[] textPredicates, boolean disjunctive, String type, String user, boolean concise) {
		return this.findStrings(textPredicates, disjunctive, type, user, concise, null);
	}
	
	/**
	 * Find strings using full text search plus sub class specific detail
	 * queries. This method is meant to be called by sub classes only.
	 * @param textPredicates the full text predicates
	 * @param disjunctive combine the predicates with 'or'?
	 * @param type the type of strings to search
	 * @param user the name of the user to contribute or last update the strings
	 * @param concise obtain a concise result, i.e., without parses?
	 * @param detailPredicates the predicates to match against a sub class
	 *            specific index, given in a properties object
	 * @return an iterator over the strings matching the query
	 */
	protected PooledStringIterator findStrings(String[] textPredicates, boolean disjunctive, String type, String user, final boolean concise, Properties detailPredicates) {
		if (((textPredicates == null) || (textPredicates.length == 0)) && (type == null) && (user == null) && ((detailPredicates == null) || detailPredicates.isEmpty()))
			return new ExceptionPSI(new IOException("Empty query"));
		try {
			if (type != null) {
				if (detailPredicates == null)
					detailPredicates = new Properties();
				detailPredicates.setProperty(TYPE_PARAMETER, type);
			}
			if (user != null) {
				if (detailPredicates == null)
					detailPredicates = new Properties();
				detailPredicates.setProperty(USER_PARAMETER, user);
			}
			InternalPooledStringIterator ipsi = this.findInternalStrings(textPredicates, disjunctive, detailPredicates);
			System.out.println("StringPoolServlet: direct search complete");
			return new PooledStringIteratorLC(ipsi, false, concise);
		}
		catch (IOException ioe) {
			return new ExceptionPSI(ioe);
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolClient#getStringsUpdatedSince(long)
	 */
	public PooledStringIterator getStringsUpdatedSince(long updatedSince) {
		try {
			final InternalPooledStringIterator ipsi = this.getStringFeed(updatedSince);
			return new PooledStringIteratorLC(ipsi, true, false);
		}
		catch (IOException ioe) {
			return new ExceptionPSI(ioe);
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolClient#setCanonicalStringId(java.lang.String, java.lang.String, java.lang.String)
	 */
	public PooledString setCanonicalStringId(String stringId, String canonicalStringId, String user) throws IOException {
		if ((user == null) || (user.length() == 0))
			user = "Anonymous";
		InternalPooledString ips = this.doPlainUpdate(stringId, (((canonicalStringId == null) || (canonicalStringId.length() == 0)) ? stringId : canonicalStringId), false, this.domainName, user, System.currentTimeMillis(), "LOCAL", null, this.domainName);
		return ((ips == null) ? null : new PooledStringLC(ips, false, false));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolClient#setDeleted(java.lang.String, boolean, java.lang.String)
	 */
	public PooledString setDeleted(String stringId, boolean deleted, String user) throws IOException {
		if ((user == null) || (user.length() == 0))
			user = "Anonymous";
		InternalPooledString ips = this.doPlainUpdate(stringId, null, deleted, this.domainName, user, System.currentTimeMillis(), "LOCAL", null, this.domainName);
		return ((ips == null) ? null : new PooledStringLC(ips, false, false));
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolClient#updateString(java.lang.String, java.lang.String)
	 */
	public PooledString updateString(String stringPlain, String user) throws IOException {
		return this.updateString(new UploadString(stringPlain), user);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolClient#updateString(java.lang.String, java.lang.String, java.lang.String)
	 */
	public PooledString updateString(String stringPlain, String stringParsed, String user) throws IOException {
		return this.updateString(new UploadString(stringPlain, stringParsed), user);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolClient#updateString(de.uka.ipd.idaho.onn.stringPool.StringPoolClient.UploadString, java.lang.String)
	 */
	public PooledString updateString(UploadString string, String user) throws IOException {
		UploadString[] strings = {string};
		PooledStringIterator stringIt = this.updateStrings(strings, user);
		if (stringIt.hasNextString())
			return stringIt.getNextString();
		IOException ioe = stringIt.getException();
		if (ioe == null)
			return null;
		throw ioe;
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolClient#updateStrings(java.lang.String[], java.lang.String)
	 */
	public PooledStringIterator updateStrings(String[] stringsPlain, String user) {
		UploadString[] strings = new UploadString[stringsPlain.length];
		for (int s = 0; s < stringsPlain.length; s++)
			strings[s] = new UploadString(stringsPlain[s]);
		return this.updateStrings(strings, user);
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolClient#updateStrings(de.uka.ipd.idaho.onn.stringPool.StringPoolClient.UploadString[], java.lang.String)
	 */
	public PooledStringIterator updateStrings(UploadString[] strings, String user) {
		final long requestTime = System.currentTimeMillis();
		if ((user == null) || (user.length() == 0))
			user = "Anonymous";
		ArrayList stringList = new ArrayList();
		for (int s = 0; s < strings.length; s++) try {
			MutableAnnotation stringParsed;
			if (strings[s].stringParsed == null)
				stringParsed = null;
			else stringParsed = SgmlDocumentReader.readDocument(new StringReader(strings[s].stringParsed));
			InternalPooledString ips = this.doStringUpdate(new InternalPooledString(requestTime, this.domainName, user, requestTime, this.domainName, user, 0, null, false, strings[s].stringPlain, stringParsed), user, "LOCAL");
			if (ips != null)
				stringList.add(ips);
		}
		catch (IOException ioe) {}
		final Iterator sit = stringList.iterator();
		return new PooledStringIterator() {
			public boolean hasNextString() {
				return sit.hasNext();
			}
			public PooledString getNextString() {
				InternalPooledString ips = ((InternalPooledString) sit.next());
				PooledStringLC ps = new PooledStringLC(ips, false, false);
				ps.parseError = ips.parseError;
				if ((ips.updateTime == ips.localUpdateTime) && (requestTime <= ips.updateTime)) {
					 if (ips.createTime >= requestTime)
						 ps.created = true;
					 else ps.updated = true;
				}
				return ps;
			}
			public IOException getException() {
				return null;
			}
		};
	}
}
