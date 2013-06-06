RefBank, the distributed platform for biliographic references.
Copyright (C) 2011-2013 ViBRANT (FP7/2007-2013, GA 261532), by D. King & G. Sautter

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program (LICENSE.txt); if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.



SYSTEM REQUIREMENTS

Java Runtime Environment 1.5 or higher, Sun/Oracle JRE recommended

Apache Tomcat 5.5 or higher (other servlet containers should work as well, but have not been tested yet)

A database server, e.g. PostgreSQL (drivers included for version 8.2) or Microsoft SQL Server (drivers included)
Instead, you can also use Apache Derby embedded database (included)
(using Apache Derby is the default configuration, so you can test RefBank without setting up a database)



SETTING UP A RefBank NODE (you likely have already done the first three steps when you open this file)

Download RefBank.zip into Tomcat's webapps folder (an exploded archive directory, zipped up for your convenience; WAR deployment is impractical, as updates would uverwrite the configurations you make)
Instead, you can also check out the project from GIT, build the ZIP file using Ant, and then deploy RefBank.zip to your Tomcat

Create a RefBank sub folder in Tomcat's webapps folder.

Un-zip the exploded archive directory into the RefBank folder.
If you have WebAppUpdater (builds with idaho-core) installed, you can also simply type "bash update RefBank" in the console.

Put RefBank.zip into the RefBank folder for others to download.

Now, it's time for some configuration:
	
    To enable the RefBank node to store parsed references in the file system and connect to other RefBank nodes, give the web application the permission to create and manipulate files and folders within its deployment folder and to establish outgoing network connections (there are two ways to achieve this):

        The simple, but very coarse way is to disable Tomcat's security manager altogether (not recommended)

        More finegrained way is to add the permission statement below to Tomcat's security configuration (recommended); the security configuration resides inside Tomcat's conf folder, which is located on the same level as the webapps folder; the actual configuration file to add the permission to is either catalina.policy directly in the conf folder, or 04webapps.policy in the conf/policy.d folder, whichever is present; if both files are present, either will do:

        grant codeBase "file:${catalina.base}/webapps/RefBank/WEB-INF/lib/-" {
        	permission java.net.SocketPermission "*.*", "connect";
        	permission java.io.FilePermission "WEB-INF/-", "read,write,delete,execute";
        }

    Adjust the config.cnfg files in the WEB-INF/<xyz>Data folders:

        In the config file in WEB-INF/rbkData/, enter a (presumably) globally unique RefBank domain name, which should identify the institution the RefBank node runs in and, if the institution runs multiple RefBank nodes, also distingish the node being set up from the other ones already running

        In the same file, enter the prefered access URL for the node, i.e., the (prefered) URL for accessing the node from the WWW

        In the same file, enter the administration passcode for the RefBank node being set up

        To secure reference upload with ReCAPTCHA to avoid spamming, obtain a ReCAPTCHA API key pair and put it in the config file in the WEB-INF/uploadData/ folder

    If not using an embedded database, create a database for RefBank in your database server, e.g. RefBankDB

    Adjust the web.cnfg file in the WEB-INF folder:

        Adjust the JDBC settings to access the database created for RefBank
        (by default configured to use Apache Derby in embedded mode)

        Set the stringPoolNodeName setting to the name assigned to the RefBank servlet in the web.xml (which is RefBank if you did not change it) so dependent local servlets can connect to it directly (Java method invocations) instead of the local network loopback adapter for better performance
        (if you do not change the web.xml file, you need not change this setting, either)

        Set the stringPoolNodeUrl setting to the access URL you configured above, or to a localhost URL; in any case, the URL used should point to the RefBank servlet directly for better performance, even if the prefered external access URL is one proxied through a local Apache web server or the like
        (the default setting assumes Tomcat running on port 8080, you need to change this only if your Tomcat is running on a different port)

To make your RefBank node credit your institution, do the following:

    Put your own institution logo in the images folder

    Customize the files footer.html and popupFooter.html in the WEB-INF folder to include your institution name and logo by replacing
    yourLogo.gif with the name of your logo image file,
    yourUrl.org with the link to your institution,
    Your Institution with the name of your institution,
    YourInstitutionAcronym with the acronym of your institution

Put the WAR file you downloaded into the webapps/RefBank/ folder so others can download it



LINKING THE RefBank NODE TO THE NETWORK

Access the web application through a browser (the search form should show up)

Follow the Administer This Node link at the bottom of the page

Enter the passcode configured above to access the administration page

Enter the access URL of another RefBank node (maybe the one the zip file was downloaded from, simply by replacing the RefBank.zip file name with rbk, resulting in http://<refBankHostDownloadedFromIncludingPort>/RefBank/rbk, for instance) into the Connect to other Nodes form and click the Add Node button
==> A list of other nodes shows up, labeled Connected Nodes

Configure replication of data in the Connected Nodes table and click the Update Nodes button to submit it
==> afterwards, the web application might be busy for a while importing the references from the other nodes via the replication mechanism



CUSTOMIZING THE LAYOUT

The servlets generate the search and upload forms as well as the search results and reference detail views dynamically from multiple files residing in the WEB-INF folder or one of its sub folders:

refBank.html is the template for the main pages

The bodies of header.html, navigation.html, and footer.html are inserted in the template where the <includeFile file="<filename>.html"> tags are in the template

The CSS styles for all these files are in refBank.css, refBank.2.css, and refBank.3.css, each representing a different layout variant
(the refBank.3.css layout is active in the default configuration)

refBankPopup.html is the template for the reference detail views

The bodies of popupHeader.html, popupNavigation.html, and popupFooter.html are inserted in the template where the <includeFile file="<filename>.html"> tags are in the template

The CSS styles for all these files are also in refBank.css, refBank.2.css, and refBank.3.css

The search or upload form is inserted where the <includeForm/> tag is in the template

The search or upload result is inserted where the <includeResult/> tag is in the template

    The search form content comes from WEB-INF/searchData/searchFields.html; the actual form tag is created by the servlet

    The CSS styles for the search form and results are in WEB-INF/searchData/refBankSearch.css. WEB-INF/searchData/refBankSearch.css, and WEB-INF/searchData/refBankSearch.css, corresponding to the respective variants of refBank.css

    The upload form comes from WEB-INF/searchData/uploadFields.html; the actual form tag is created by the servlet

    The reCAPTCHA widget is inserted where the <includeReCAPTCHA/> tag is in uploadFields.html

    The CSS styles for the upload form and results are in WEB-INF/uploadData/refBankUpload.css, WEB-INF/uploadData/refBankUpload.2.css, and WEB-INF/uploadData/refBankUpload.3.css, corresponding to the respective variants of refBank.css

onnNodeAdminPage.html is the template for the administration page

The respective CSS styles are in onnNodeAdminPage.css, onnNodeAdminPage.2.css, and onnNodeAdminPage.3.css, corresponding to the respective variants of refBank.css

To customize general page layout, change the refBank.html and refBankPopup.html files and the respective stylesheets

    This can be as simple customizing respective CSS styles (can be tested on statically saved post-generation HTML pages)

    This can include changing the file names or the placement of the <includeFile .../> tags; when changing the file names or adding <includeFile .../> tags, make sure that the references files exist (requires the web application to run for testing)

    This can include changing the placement of the <includeForm/> and <includeResult/> tags; make sure, however, that these tags remain in the template page, as otherwise the functional parts of the pages cannot be inserted (requires the web application to run for testing)

To customize page header, navigation, or footer, customize the respective HTML files and the respective stylesheets

    This can be as simple customizing respective CSS styles (can be tested on statically saved post-generation HTML pages)

    This can include adding new <includeFile .../> tags; when doing this, make sure that the references files exist (requires the web application to run for testing)

Do not add header, navigation, or footer content to the refBank.html or refBankPopup.html files directly, but use the respective inserted files insted (requires the web application to run for testing)

If you alter any other files, include them in the update.cnfg file (with full path, starting with WEB-INF) so they are not replaced in case of an update.