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

import de.uka.ipd.idaho.refBank.RefBankConstants;

/**
 * @author sautter
 *
 */
public interface RefBankDataIndexConstants extends RefBankConstants {
	
	public static final String DATA_TYPE_PARAMETER = "type";
	
	public static final String PERSONS_DATA_TYPE = "persons";
	public static final String JOURNALS_DATA_TYPE = "journals";
	public static final String PUBLISHERS_DATA_TYPE = "publishers";
	public static final String ORIGINS_DATA_TYPE = "origins";
}
