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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.LinkedList;

/**
 * Client for the RefBank data index.
 * 
 * @author sautter
 */
public class RefBankDataIndexRestClient implements RefBankDataIndexConstants {
	
	private String baseUrl;
	
	/**
	 * Constructor
	 * @param baseUrl the URL of the RefBank data index to connect to
	 */
	public RefBankDataIndexRestClient(String baseUrl) {
		this.baseUrl = baseUrl;
	}
	
	public String[] getPersonNames() throws IOException {
		return this.getData(PERSONS_DATA_TYPE);
	}
	
	public String[] getJournalNames() throws IOException {
		return this.getData(JOURNALS_DATA_TYPE);
	}
	
	public String[] getPublishers() throws IOException {
		return this.getData(PUBLISHERS_DATA_TYPE);
	}
	
	public String[] getDocumentOrigins() throws IOException {
		return this.getData(ORIGINS_DATA_TYPE);
	}
	
	private String[] getData(String type) throws IOException {
		URL dataUrl = new URL(this.baseUrl + "?" + DATA_TYPE_PARAMETER + "=" + type);
		LinkedList dataList = new LinkedList();
		BufferedReader br = new BufferedReader(new InputStreamReader(dataUrl.openStream(), ENCODING));
		String data;
		while ((data = br.readLine()) != null) {
			data = data.trim();
			if (data.length() != 0)
				dataList.addLast(data);
		}
		return ((String[]) dataList.toArray(new String[dataList.size()]));
	}
	
	public static void main(String[] args) throws Exception {
		RefBankDataIndexRestClient dirc = new RefBankDataIndexRestClient("http://localhost:8080/gnubTest/data");
		String[] authors = dirc.getPersonNames();
		for (int a = 0; a < authors.length; a++)
			System.out.println(authors[a]);
	}
}
