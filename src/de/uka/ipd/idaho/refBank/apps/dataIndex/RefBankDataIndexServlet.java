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
package de.uka.ipd.idaho.refBank.apps.dataIndex;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.uka.ipd.idaho.easyIO.EasyIO;
import de.uka.ipd.idaho.easyIO.IoProvider;
import de.uka.ipd.idaho.easyIO.SqlQueryResult;
import de.uka.ipd.idaho.easyIO.sql.TableDefinition;
import de.uka.ipd.idaho.easyIO.web.WebAppHost;
import de.uka.ipd.idaho.gamta.MutableAnnotation;
import de.uka.ipd.idaho.gamta.QueriableAnnotation;
import de.uka.ipd.idaho.gamta.TokenSequenceUtils;
import de.uka.ipd.idaho.gamta.util.SgmlDocumentReader;
import de.uka.ipd.idaho.gamta.util.gPath.GPath;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient.PooledString;
import de.uka.ipd.idaho.onn.stringPool.StringPoolClient.PooledStringIterator;
import de.uka.ipd.idaho.refBank.RefBankClient;
import de.uka.ipd.idaho.refBank.apps.RefBankAppServlet;
import de.uka.ipd.idaho.stringUtils.StringIterator;
import de.uka.ipd.idaho.stringUtils.StringVector;

/**
 * Servlet providing data from individual fields of references in concise form.
 * 
 * @author sautter
 */
public class RefBankDataIndexServlet extends RefBankAppServlet implements RefBankDataIndexConstants {
	
	private static class CountingStringSet {
		private TreeMap content = new TreeMap();
//		private int size = 0;
		
		CountingStringSet() {}
		
		StringIterator getIterator() {
			final Iterator it = this.content.keySet().iterator();
			return new StringIterator() {
				public boolean hasNext() {
					return it.hasNext();
				}
				public Object next() {
					return it.next();
				}
				public void remove() {}
				public boolean hasMoreStrings() {
					return it.hasNext();
				}
				public String nextString() {
					return ((String) it.next());
				}
			};
		}
		
		boolean add(String string) {
			Int i = ((Int) this.content.get(string));
//			this.size++;
			if (i == null) {
				this.content.put(string, new Int(1));
				return true;
			}
			else {
				i.increment();
				return false;
			}
		}
		
		boolean remove(String string) {
			Int i = ((Int) this.content.get(string));
			if (i == null)
				return false;
//			this.size--;
			if (i.intValue() > 1) {
				i.decrement();
				return false;
			}
			else {
				this.content.remove(string);
				return true;
			}
		}
		
		private class Int {
			private int value;
			Int(int val) {
				this.value = val;
			}
			int intValue() {
				return this.value;
			}
			void increment() {
				this.value ++;
			}
			void decrement() {
				this.value --;
			}
		}
	}
	
	private CountingStringSet persons = new CountingStringSet();
	private CountingStringSet journals = new CountingStringSet();
	private CountingStringSet publishers = new CountingStringSet();
	
	
	private static final String REFERENCE_ID_COLUMN_NAME = "ReferenceId";
	private static final String REFERENCE_ID_HASH_COLUMN_NAME = "IdHash"; // int hash of the ID string, speeding up joins with index table
	
	private static final String PERSON_TABLE_NAME = "RefBankPersons";
	private static final String PERSON_NAME_COLUMN_NAME = "PersonName";
	private static final int PERSON_NAME_COLUMN_LENGTH = 92;
	
	private static final String DOC_ORIGIN_TABLE_NAME = "RefBankDocOrigin";
	private static final String DOC_ORIGIN_COLUMN_NAME = "DocOrigin";
	private static final int DOC_ORIGIN_COLUMN_LENGTH = 219;
	private static final String DOC_ORIGIN_TYPE_COLUMN_NAME = "OriginType";
	
	private IoProvider io;
	
//	private RefBankClient refBankClient;
	private long lastReceived = 0;
	
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolAppServlet#doInit()
	 */
	protected void doInit() throws ServletException {
		super.doInit();
		
		//	get timestamp of last received update
		try {
			this.lastReceived = Long.parseLong(this.getSetting("lastReceived", ("" + this.lastReceived)));
		} catch (NumberFormatException nfe) {}
		
		// get and check database connection
		this.io = WebAppHost.getInstance(this.getServletContext()).getIoProvider();
		if (!this.io.isJdbcAvailable())
			throw new RuntimeException("RefBankDataIndex: Cannot work without database access.");
		
		//	produce author table
		TableDefinition ptd = new TableDefinition(PERSON_TABLE_NAME);
		ptd.addColumn(REFERENCE_ID_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 32);
		ptd.addColumn(REFERENCE_ID_HASH_COLUMN_NAME, TableDefinition.INT_DATATYPE, 0);
		ptd.addColumn(PERSON_NAME_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, PERSON_NAME_COLUMN_LENGTH);
		if (!this.io.ensureTable(ptd, true))
			throw new RuntimeException("RefBankDataIndex: Cannot work without database access.");
		
		this.io.indexColumn(PERSON_TABLE_NAME, REFERENCE_ID_COLUMN_NAME);
		this.io.indexColumn(PERSON_TABLE_NAME, REFERENCE_ID_HASH_COLUMN_NAME);
		
		//	produce doc origin table
		TableDefinition otd = new TableDefinition(DOC_ORIGIN_TABLE_NAME);
		otd.addColumn(REFERENCE_ID_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, 32);
		otd.addColumn(REFERENCE_ID_HASH_COLUMN_NAME, TableDefinition.INT_DATATYPE, 0);
		otd.addColumn(DOC_ORIGIN_COLUMN_NAME, TableDefinition.VARCHAR_DATATYPE, DOC_ORIGIN_COLUMN_LENGTH);
		otd.addColumn(DOC_ORIGIN_TYPE_COLUMN_NAME, TableDefinition.CHAR_DATATYPE, 1);
		if (!this.io.ensureTable(otd, true))
			throw new RuntimeException("RefBankDataIndex: Cannot work without database access.");
		
		this.io.indexColumn(DOC_ORIGIN_TABLE_NAME, REFERENCE_ID_COLUMN_NAME);
		this.io.indexColumn(DOC_ORIGIN_TABLE_NAME, REFERENCE_ID_HASH_COLUMN_NAME);
		this.io.indexColumn(DOC_ORIGIN_TABLE_NAME, DOC_ORIGIN_TYPE_COLUMN_NAME);
		
		//	fill in-memory data structures
		String query = null;
		SqlQueryResult sqr;
		
		//	load persons
		try {
			query = "SELECT " + PERSON_NAME_COLUMN_NAME + " FROM " + PERSON_TABLE_NAME + ";";
			sqr = this.io.executeSelectQuery(query);
			while (sqr.next())
				this.persons.add(sqr.getString(0));
		}
		catch (SQLException sqle) {
			System.out.println("RefBankDataIndex: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while loading persons.");
			System.out.println("  query was " + query);
		}
		
		//	load journal / publisher names
		try {
			query = "SELECT " + DOC_ORIGIN_COLUMN_NAME + ", " + DOC_ORIGIN_TYPE_COLUMN_NAME + " FROM " + DOC_ORIGIN_TABLE_NAME + ";";
			sqr = this.io.executeSelectQuery(query);
			while (sqr.next()) {
				if ("J".equals(sqr.getString(1)))
					this.journals.add(sqr.getString(0));
				else if ("P".equals(sqr.getString(1)))
					this.publishers.add(sqr.getString(0));
			}
		}
		catch (SQLException sqle) {
			System.out.println("RefBankDataIndex: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while loading journals / publishers.");
			System.out.println("  query was " + query);
		}
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolAppServlet#exit()
	 */
	protected void exit() {
		this.setSetting("lastReceived", ("" + this.lastReceived));
		super.exit();
		this.io.close();
	}
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolAppServlet#fetchUpdates()
	 */
	protected boolean fetchUpdates() {
		return true; // we do want periodical updates
	}
	
	private static final GPath authorsPath = new GPath("//mods:name[.//mods:roleTerm = 'Author']/mods:namePart");
	private static final GPath editorsPath = new GPath("//mods:relatedItem[./@type = 'host']//mods:name[.//mods:roleTerm = 'Editor']/mods:namePart");
	
	private static final GPath journalNamePath = new GPath("//mods:relatedItem[./@type = 'host']//mods:titleInfo/mods:title");
	
	private static final GPath originInfoPath = new GPath("//mods:originInfo");
	private static final GPath originInfo_publisherNamePath = new GPath("//mods:publisher");
	private static final GPath originInfo_publisherLocationPath = new GPath("//mods:place/mods:placeTerm");
	
	
	/* (non-Javadoc)
	 * @see de.uka.ipd.idaho.onn.stringPool.StringPoolAppServlet#fetchUpdates(long)
	 */
	protected void fetchUpdates(long lastLookup) throws IOException {
		
		//	retrieve RefBank client on the fly to use local bridge if possible
		RefBankClient rbc = this.getRefBankClient();
		
		//	we have to catch this, as super class might start update thread before we have the client instantiated
		if (rbc == null)
//		if (this.refBankClient == null)
			return;
		
		//	read update feed
		long timestampThreshold = Math.max((this.lastReceived - 999), 1);
		LinkedList updateIdList = new LinkedList();
		System.out.println("Fetching updates since " + timestampThreshold + " from " + this.stringPoolNodeUrl);
//		PooledStringIterator pbri = this.refBankClient.getStringsUpdatedSince(timestampThreshold);
		PooledStringIterator pbri = rbc.getStringsUpdatedSince(timestampThreshold);
		while (pbri.hasNextString()) {
			PooledString ps = pbri.getNextString();
			if (ps.getParseChecksum() != null) {
				updateIdList.addLast(ps.id);
				this.lastReceived = Math.max(this.lastReceived, ps.getNodeUpdateTime());
			}
		}
		System.out.println("Got " + updateIdList.size() + " updates");
		
		//	do updates
		while (updateIdList.size() != 0) {
			String updateId = ((String) updateIdList.removeFirst());
//			PooledString ps = this.refBankClient.getString(updateId);
			PooledString ps = rbc.getString(updateId);
			if (ps == null)
				continue;
			else if ("AutomatedParser".equals(ps.getUpdateUser()))
				continue;
			
			//	parse XML reference
			MutableAnnotation refParsed = SgmlDocumentReader.readDocument(new StringReader(ps.getStringParsed()));
			System.out.println("Got parsed reference for ID " + updateId);
			
			//	get and store persons / editors
			StringVector authorList = new StringVector();
			QueriableAnnotation[] authors = authorsPath.evaluate(refParsed, null);
			for (int a = 0; a < authors.length; a++)
				authorList.addElementIgnoreDuplicates(TokenSequenceUtils.concatTokens(authors[a], true, true));
			QueriableAnnotation[] editors = editorsPath.evaluate(refParsed, null);
			for (int e = 0; e < editors.length; e++)
				authorList.addElementIgnoreDuplicates(TokenSequenceUtils.concatTokens(editors[e], true, true));
			this.storePersons(authorList.toStringArray(), updateId);
			System.out.println("Got persons: "  + authorList.concatStrings(", "));
			
			//	get and store origins
			StringVector originList = new StringVector();
			
			//	get and store journal names
			QueriableAnnotation[] journalNames = journalNamePath.evaluate(refParsed, null);
			for (int j = 0; j < journalNames.length; j++)
				originList.addElementIgnoreDuplicates(TokenSequenceUtils.concatTokens(journalNames[j], true, true));
			if (originList.size() != 0) {
				this.storeOrigins(originList.toStringArray(), updateId, true);
				System.out.println(" - got origins: "  + originList.concatStrings(", "));
				continue;
			}
			
			//	get and store publishers
			QueriableAnnotation[] originInfos = originInfoPath.evaluate(refParsed, null);
			for (int o = 0; o < originInfos.length; o++) {
				QueriableAnnotation[] publisherNames = originInfo_publisherNamePath.evaluate(originInfos[o], null);
				QueriableAnnotation[] publisherLocations = originInfo_publisherLocationPath.evaluate(originInfos[o], null);
				if ((publisherNames.length + publisherLocations.length) == 0)
					continue;
				String publisher;
				if (publisherNames.length == 0)
					publisher = TokenSequenceUtils.concatTokens(publisherLocations[0], true, true);
				else if (publisherLocations.length == 0)
					publisher = TokenSequenceUtils.concatTokens(publisherNames[0], true, true);
				else publisher = (TokenSequenceUtils.concatTokens(publisherNames[0], true, true) + ", " + TokenSequenceUtils.concatTokens(publisherLocations[0], true, true));
				originList.addElementIgnoreDuplicates(publisher);
			}
			if (originList.size() != 0) {
				this.storeOrigins(originList.toStringArray(), updateId, false);
				System.out.println(" - got origins: "  + originList.concatStrings(", "));
				continue;
			}
		}
	}

	private void storePersons(String[] persons, String refId) {
		HashSet toRemove = new HashSet();
		HashSet toAdd = new HashSet(Arrays.asList(persons));
		
		//	diff with database
		String query = null;
		try {
			query = "SELECT " + PERSON_NAME_COLUMN_NAME + 
					" FROM " + PERSON_TABLE_NAME + 
					" WHERE " + REFERENCE_ID_HASH_COLUMN_NAME + " = " + refId.hashCode() +
						" AND " + REFERENCE_ID_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(refId) + "'" +
					";";
			SqlQueryResult sqr = this.io.executeSelectQuery(query);
			while (sqr.next()) {
				String person = sqr.getString(0);
				if (!toAdd.remove(person))
					toRemove.add(person);
			}
		}
		catch (SQLException sqle) {
			System.out.println("RefBankDataIndex: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while loading existing persons for reference '" + refId + "'.");
			System.out.println("  query was " + query);
		}
		
		//	remove persons
		if (toRemove.size() != 0) {
			StringBuffer rPersonWhere = new StringBuffer();
			for (Iterator rit = toRemove.iterator(); rit.hasNext();) {
				String rPerson = ((String) rit.next());
				if (rPersonWhere.length() != 0)
					rPersonWhere.append(", ");
				rPersonWhere.append("'" + EasyIO.sqlEscape(rPerson) + "'");
				this.persons.remove(rPerson);
			}
			try {
				query = "DELETE FROM " + PERSON_TABLE_NAME + 
						" WHERE " + REFERENCE_ID_HASH_COLUMN_NAME + " = " + refId.hashCode() +
							" AND " + REFERENCE_ID_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(refId) + "'" +
							" AND " + PERSON_NAME_COLUMN_NAME + " IN (" + rPersonWhere.toString() + ")" +
						";";
				this.io.executeUpdateQuery(query);
			}
			catch (SQLException sqle) {
				System.out.println("RefBankDataIndex: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while removing persons for reference '" + refId + "'.");
				System.out.println("  query was " + query);
			}
		}
		
		//	add persons
		if (toAdd.size() != 0) {
			for (Iterator ait = toAdd.iterator(); ait.hasNext();) {
				String aPerson = ((String) ait.next());
				this.persons.add(aPerson);
				try {
					query = "INSERT INTO " + PERSON_TABLE_NAME + " (" + 
							REFERENCE_ID_COLUMN_NAME + ", " + REFERENCE_ID_HASH_COLUMN_NAME + ", " + PERSON_NAME_COLUMN_NAME + 
							") VALUES (" +
							"'" + EasyIO.sqlEscape(refId) + "', " + refId.hashCode() + ", '" + EasyIO.sqlEscape(aPerson) + "'" +
							");";
					this.io.executeUpdateQuery(query);
				}
				catch (SQLException sqle) {
					System.out.println("RefBankDataIndex: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while adding persons for reference '" + refId + "'.");
					System.out.println("  query was " + query);
				}
			}
		}
	}
	
	private void storeOrigins(String origins[], String refId, boolean isJournal) {
		HashSet toRemove = new HashSet();
		HashSet toAdd = new HashSet(Arrays.asList(origins));
		
		//	diff with database
		String query = null;
		try {
			query = "SELECT " + DOC_ORIGIN_COLUMN_NAME + 
					" FROM " + DOC_ORIGIN_TABLE_NAME + 
					" WHERE " + REFERENCE_ID_HASH_COLUMN_NAME + " = " + refId.hashCode() +
						" AND " + REFERENCE_ID_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(refId) + "'" +
					";";
			SqlQueryResult sqr = this.io.executeSelectQuery(query);
			while (sqr.next()) {
				String origin = sqr.getString(0);
				if (!toAdd.remove(origin))
					toRemove.add(origin);
			}
		}
		catch (SQLException sqle) {
			System.out.println("RefBankDataIndex: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while loading existing origins for reference '" + refId + "'.");
			System.out.println("  query was " + query);
		}
		
		//	remove origins
		if (toRemove.size() != 0) {
			StringBuffer rOriginWhere = new StringBuffer();
			for (Iterator rit = toRemove.iterator(); rit.hasNext();) {
				String rOrigin = ((String) rit.next());
				if (rOriginWhere.length() != 0)
					rOriginWhere.append(", ");
				rOriginWhere.append("'" + EasyIO.sqlEscape(rOrigin) + "'");
				(isJournal ? this.journals : this.publishers).remove(rOrigin);
			}
			try {
				query = "DELETE FROM " + DOC_ORIGIN_TABLE_NAME + 
						" WHERE " + REFERENCE_ID_HASH_COLUMN_NAME + " = " + refId.hashCode() +
							" AND " + REFERENCE_ID_COLUMN_NAME + " LIKE '" + EasyIO.sqlEscape(refId) + "'" +
							" AND " + DOC_ORIGIN_COLUMN_NAME + " IN (" + rOriginWhere.toString() + ")" +
						";";
				this.io.executeUpdateQuery(query);
			}
			catch (SQLException sqle) {
				System.out.println("RefBankDataIndex: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while removing persons for reference '" + refId + "'.");
				System.out.println("  query was " + query);
			}
		}
		
		//	add origins
		if (toAdd.size() != 0) {
			for (Iterator ait = toAdd.iterator(); ait.hasNext();) {
				String aOrigin = ((String) ait.next());
				(isJournal ? this.journals : this.publishers).add(aOrigin);
				try {
					query = "INSERT INTO " + DOC_ORIGIN_TABLE_NAME + " (" + 
							REFERENCE_ID_COLUMN_NAME + ", " + REFERENCE_ID_HASH_COLUMN_NAME + ", " + DOC_ORIGIN_COLUMN_NAME + ", " + DOC_ORIGIN_TYPE_COLUMN_NAME +
							") VALUES (" +
							"'" + EasyIO.sqlEscape(refId) + "', " + refId.hashCode() + ", '" + EasyIO.sqlEscape(aOrigin) + "', '" + (isJournal ? 'J' : 'P') + "'" +
							");";
					this.io.executeUpdateQuery(query);
				}
				catch (SQLException sqle) {
					System.out.println("RefBankDataIndex: " + sqle.getClass().getName() + " (" + sqle.getMessage() + ") while adding origins for reference '" + refId + "'.");
					System.out.println("  query was " + query);
				}
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String type = request.getParameter(DATA_TYPE_PARAMETER);
		
		if (PERSONS_DATA_TYPE.equals(type))
			this.sendPersons(response);
		
		else if (JOURNALS_DATA_TYPE.equals(type))
			this.sendOrigins(response, 'J');
		
		else if (PUBLISHERS_DATA_TYPE.equals(type))
			this.sendOrigins(response, 'P');
		
		else if (ORIGINS_DATA_TYPE.equals(type))
			this.sendOrigins(response, 'O');
		
		else response.sendError(HttpServletResponse.SC_BAD_REQUEST);
	}
	
	private void sendPersons(HttpServletResponse response) throws IOException {
		response.setContentType("text/plain");
		response.setCharacterEncoding(ENCODING);
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
		for (StringIterator ait = this.persons.getIterator(); ait.hasMoreStrings();) {
			bw.write(ait.nextString());
			bw.newLine();
		}
		bw.flush();
	}
	
	private void sendOrigins(HttpServletResponse response, char type) throws IOException {
		response.setContentType("text/plain");
		response.setCharacterEncoding(ENCODING);
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), ENCODING));
		if ((type == 'J') || (type == 'O'))
			for (StringIterator jit = this.journals.getIterator(); jit.hasMoreStrings();) {
				bw.write(jit.nextString());
				bw.newLine();
			}
		if ((type == 'P') || (type == 'O'))
			for (StringIterator pit = this.publishers.getIterator(); pit.hasMoreStrings();) {
				bw.write(pit.nextString());
				bw.newLine();
			}
		bw.flush();
	}
//	
//	public static void main(String[] args) throws Exception {
//		RefBankDataIndexServlet grdis = new RefBankDataIndexServlet();
//		grdis.parsedBucketNodeUrl = "http://plazi2.cs.umb.edu:8080/gnubTest/pb";
//		grdis.init(Settings.loadSettings(new File("E:/Projektdaten/GNUB/WebApp/pbData/config.gnub.Idaho.cnfg")));
//		grdis.fetchUpdates(0);
//	}
}
