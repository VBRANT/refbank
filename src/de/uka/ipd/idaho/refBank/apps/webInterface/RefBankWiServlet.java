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
package de.uka.ipd.idaho.refBank.apps.webInterface;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

import javax.servlet.ServletException;

import de.uka.ipd.idaho.htmlXmlUtil.accessories.HtmlPageBuilder;
import de.uka.ipd.idaho.refBank.RefBankConstants;
import de.uka.ipd.idaho.refBank.apps.RefBankAppServlet;

/**
 * @author sautter
 */
public abstract class RefBankWiServlet extends RefBankAppServlet implements RefBankConstants {
	
	private TreeMap dataFormats = new TreeMap();
	
	/**
	 * This implementation loads plug-in reference data formats. Sub classes
	 * overwriting it thus have to make the super call.
	 * @see de.uka.ipd.idaho.onn.stringPool.apps.StringPoolAppServlet#doInit()
	 */
	protected void doInit() throws ServletException {
		super.doInit();
		
		//	load data formats
		File dfFolder = new File(this.webInfFolder, "refDataFormats");
		if (dfFolder.exists()) {
			RefDataFormat[] dataFormats = RefDataFormat.getDataFormats(dfFolder);
			for (int f = 0; f < dataFormats.length; f++)
				this.dataFormats.put(dataFormats[f].name, dataFormats[f]);
		}
	}
	
	/**
	 * @return the names of plug-in input data formats
	 */
	protected String[] getInputDataFormatNames() {
		ArrayList idfNames = new ArrayList(this.dataFormats.size());
		for (Iterator fit = this.dataFormats.keySet().iterator(); fit.hasNext();) {
			String dfName = ((String) fit.next());
			RefDataFormat rdf = getDataFormat(dfName);
			if ((rdf != null) && rdf.isInputFormat())
				idfNames.add(dfName);
		}
		return ((String[]) idfNames.toArray(new String[idfNames.size()]));
	}
	
	/**
	 * @return the plug-in input data formats
	 */
	protected RefDataFormat[] getInputDataFormats() {
		ArrayList idfs = new ArrayList(this.dataFormats.size());
		for (Iterator fit = this.dataFormats.keySet().iterator(); fit.hasNext();) {
			String dfName = ((String) fit.next());
			RefDataFormat rdf = getDataFormat(dfName);
			if ((rdf != null) && rdf.isInputFormat())
				idfs.add(rdf);
		}
		return ((RefDataFormat[]) idfs.toArray(new RefDataFormat[idfs.size()]));
	}
	
	/**
	 * @return the names of plug-in output data formats
	 */
	protected String[] getOutputDataFormatNames() {
		ArrayList odfNames = new ArrayList(this.dataFormats.size());
		for (Iterator fit = this.dataFormats.keySet().iterator(); fit.hasNext();) {
			String dfName = ((String) fit.next());
			RefDataFormat rdf = getDataFormat(dfName);
			if ((rdf != null) && rdf.isOutputFormat())
				odfNames.add(dfName);
		}
		return ((String[]) odfNames.toArray(new String[odfNames.size()]));
	}
	
	/**
	 * @return the plug-in output data formats
	 */
	protected RefDataFormat[] getOutputDataFormats() {
		ArrayList odfs = new ArrayList(this.dataFormats.size());
		for (Iterator fit = this.dataFormats.keySet().iterator(); fit.hasNext();) {
			String dfName = ((String) fit.next());
			RefDataFormat rdf = getDataFormat(dfName);
			if ((rdf != null) && rdf.isOutputFormat())
				odfs.add(rdf);
		}
		return ((RefDataFormat[]) odfs.toArray(new RefDataFormat[odfs.size()]));
	}
	
	/**
	 * @param name the data format name
	 * @return the data format with the argument name
	 */
	protected RefDataFormat getDataFormat(String name) {
		return ((name == null) ? null : ((RefDataFormat) this.dataFormats.get(name)));
	}
	
	/**
	 * This implementation shuts down plug-in reference data formats. Sub
	 * classes overwriting it thus have to make the super call.
	 * @see de.uka.ipd.idaho.onn.stringPool.apps.StringPoolAppServlet#exit()
	 */
	protected void exit() {
		super.exit();
		
		//	shut down data formats
		RefDataFormat[] dataFormats = ((RefDataFormat[]) this.dataFormats.values().toArray(new RefDataFormat[this.dataFormats.size()]));
		for (int f = 0; f < dataFormats.length; f++)
			dataFormats[f].exit();
		this.dataFormats.clear();
	}
	
	public String[] getOnloadCalls() {
		String[] olcs = {"showUserMenu();"};
		return olcs;
	}
	
	public void writePageHeadExtensions(HtmlPageBuilder out) throws IOException {
		out.writeLine("<script type=\"text/javascript\">");
		
		out.writeLine("var user = '';");
		
		out.writeLine("function getUser(silent) {");
		out.writeLine("  if (user.length == 0) {");
		out.writeLine("    var cUser = document.cookie;");
		out.writeLine("    if ((cUser != null) && (cUser.indexOf('user=') != -1)) {");
		out.writeLine("      cUser = cUser.substring(cUser.indexOf('user=') + 'user='.length);");
		out.writeLine("      if (cUser.indexOf(';') != -1)");
		out.writeLine("        cUser = cUser.substring(0, cUser.indexOf(';'));");
		out.writeLine("      user = unescape(cUser);");
		out.writeLine("    }");
		out.writeLine("    if ((silent == null) && (user.length == 0))");
		out.writeLine("      editUserName();");
		out.writeLine("  }");
		out.writeLine("  return (user.length != 0);");
		out.writeLine("}");
		
		out.writeLine("function editUserName() {");
		out.writeLine("  var pUser = window.prompt('Please enter a user name so RefBank can credit your contribution', ((user == null) ? '' : user));");
		out.writeLine("  if (pUser == null)");
		out.writeLine("    return false;");
		out.writeLine("  else user = pUser.replace(/^\\s+|\\s+$/g,'');");
		out.writeLine("  document.cookie = ('user=' + user + ';domain=' + escape(window.location.hostname) + ';expires=' + new Date(1509211565944).toGMTString());");
		out.writeLine("  var undl = document.getElementById('userNameDisplayLabel');");
		out.writeLine("  if (undl != null) {");
		out.writeLine("    if (undl.firstChild)");
		out.writeLine("      undl.firstChild.nodeValue = ((user.length != 0) ? 'Current screen name:' : 'No screen name specified');");
		out.writeLine("    else undl.appendChild(document.createTextNode((user.length != 0) ? 'Current screen name:' : 'No screen name specified'));");
		out.writeLine("  }");
		out.writeLine("  var unds = document.getElementById('userNameDisplaySpan');");
		out.writeLine("  if (unds != null) {");
		out.writeLine("    if (unds.firstChild)");
		out.writeLine("      unds.firstChild.nodeValue = user;");
		out.writeLine("    else unds.appendChild(document.createTextNode(user));");
		out.writeLine("  }");
		out.writeLine("  return false;");
		out.writeLine("}");
		
		out.writeLine("function showUserMenu() {");
		out.writeLine("  var bodyRoot = document.getElementsByTagName('body')[0];");
		out.writeLine("  if (bodyRoot == null)");
		out.writeLine("    return;");
		out.writeLine("  var umRoot = document.createElement('div');");
		out.writeLine("  setAttribute(umRoot, 'width', '100%');");
		out.writeLine("  setAttribute(umRoot, 'align', 'right');");
		out.writeLine("  var undl = document.createElement('span');");
		out.writeLine("  setAttribute(undl, 'id', 'userNameDisplayLabel');");
		out.writeLine("  undl.appendChild(document.createTextNode(getUser('silent') ? 'Currently credited as:' : 'Please enter your name so RefBank can credit your contributions'));");
		out.writeLine("  umRoot.appendChild(undl);");
		out.writeLine("  var unds = document.createElement('span');");
		out.writeLine("  setAttribute(unds, 'id', 'userNameDisplaySpan');");
		out.writeLine("  setAttribute(unds, 'style', 'margin-left: 5px; font-weight: bold;');");
		out.writeLine("  unds.appendChild(document.createTextNode(user));");
		out.writeLine("  umRoot.appendChild(unds);");
		out.writeLine("  var uneb = document.createElement('a');");
		out.writeLine("  setAttribute(uneb, 'href', '#');");
		out.writeLine("  setAttribute(uneb, 'class', 'footerNavigationLink');");
		out.writeLine("  setAttribute(uneb, 'onclick', 'return editUserName();');");
		out.writeLine("  setAttribute(uneb, 'style', 'margin-left: 10px;');");
		out.writeLine("  uneb.appendChild(document.createTextNode('Enter / Edit'));");
		out.writeLine("  umRoot.appendChild(uneb);");
		out.writeLine("  bodyRoot.insertBefore(umRoot, bodyRoot.firstChild);");
		out.writeLine("}");
		
		out.writeLine("function setAttribute(node, name, value) {");
		out.writeLine("  if (!node.setAttributeNode)");
		out.writeLine("    return;");
		out.writeLine("  var attribute = document.createAttribute(name);");
		out.writeLine("  attribute.nodeValue = value;");
		out.writeLine("  node.setAttributeNode(attribute);");
		out.writeLine("}");
		out.writeLine("</script>");
	}
}