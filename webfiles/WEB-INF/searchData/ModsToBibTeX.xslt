<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fo="http://www.w3.org/1999/XSL/Format" xmlns:mods="http://www.loc.gov/mods/v3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
	<xsl:output omit-xml-declaration="yes"/>
	
	<xsl:template match="/">
		<xsl:apply-templates select="//mods:mods"/>
	</xsl:template>
	
	<xsl:template match="mods:mods">
		<xsl:choose>
			<xsl:when test="//mods:classification = 'journal article'">
				<xsl:text>@Article{</xsl:text><xsl:call-template name="id"/>
				<xsl:call-template name="authors"/>
				<xsl:call-template name="title"/>
				<xsl:call-template name="dateJournal"/>
				<xsl:call-template name="journal"/>
				<xsl:call-template name="volume"/>
				<xsl:call-template name="pagination"/>
				<xsl:text>&#x0A;}</xsl:text>
			</xsl:when>
			<xsl:when test="//mods:classification = 'journal volume'">
				<xsl:text>@Misc{</xsl:text><xsl:call-template name="id"/>
				<xsl:call-template name="authors"/>
				<xsl:call-template name="title"/>
				<xsl:call-template name="dateJournal"/>
				<xsl:call-template name="journal"/>
				<xsl:call-template name="volume"/>
				<xsl:text>&#x0A;}</xsl:text>
			</xsl:when>
			<xsl:when test="//mods:classification = 'book chapter'">
				<xsl:text>@Incollection{</xsl:text><xsl:call-template name="id"/>
				<xsl:call-template name="authors"/>
				<xsl:call-template name="title"/>
				<xsl:apply-templates select="./mods:relatedItem[./@type = 'host']"/>
				<xsl:text>&#x0A;}</xsl:text>
			</xsl:when>
			<xsl:when test="//mods:classification = 'book'">
				<xsl:text>@Book{</xsl:text><xsl:call-template name="id"/>
				<xsl:call-template name="authors"/>
				<xsl:call-template name="title"/>
				<xsl:call-template name="dateBook"/>
				<xsl:call-template name="publisher"/>
				<xsl:text>&#x0A;}</xsl:text>
			</xsl:when>
			<xsl:when test="//mods:classification = 'proceedings paper' or //mods:classification = 'conference paper'">
				<xsl:text>@Inproceedings{</xsl:text><xsl:call-template name="id"/>
				<xsl:call-template name="authors"/>
				<xsl:call-template name="title"/>
				<xsl:apply-templates select="./mods:relatedItem[./@type = 'host']"/>
				<xsl:text>&#x0A;}</xsl:text>
			</xsl:when>
			<xsl:when test="//mods:classification = 'proceedings' or //mods:classification = 'conference proceedings'">
				<xsl:text>@Inproceedings{</xsl:text><xsl:call-template name="id"/>
				<xsl:call-template name="authorsIfGiven"/>
				<xsl:call-template name="title"/>
				<xsl:call-template name="dateBook"/>
				<xsl:call-template name="publisherIfGiven"/>
				<xsl:text>&#x0A;}</xsl:text>
			</xsl:when>
			<xsl:otherwise>BibTeX output is currently not supported for reference type '<xsl:value-of select="//mods:classification"/>'. Please contact your system administrator.</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template match="mods:relatedItem[./@type = 'host']">
		<xsl:choose>
			<xsl:when test="//mods:classification = 'proceedings paper' or //mods:classification = 'conference paper'">
				<xsl:call-template name="dateBook"/>
				<xsl:call-template name="bookTitle"/>
				<xsl:call-template name="editorsIfGiven"/>
				<xsl:call-template name="publisherIfGiven"/>
				<xsl:call-template name="paginationIfGiven"/>
			</xsl:when>
			<xsl:otherwise>
				<xsl:call-template name="dateBook"/>
				<xsl:call-template name="bookTitle"/>
				<xsl:call-template name="editors"/>
				<xsl:call-template name="publisher"/>
				<xsl:call-template name="pagination"/>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="id">
		<xsl:value-of select="./mods:identifier[./@type = 'RefBankID']"/>
	</xsl:template>
	
	<xsl:template name="authorsIfGiven">
		<xsl:if test="./mods:name[.//mods:roleTerm = 'Author']"><xsl:call-template name="authors"/></xsl:if>
	</xsl:template>
	<xsl:template name="authors">
		<xsl:text disable-output-escaping="yes">,&#x0A;author = &quot;</xsl:text>
		<xsl:for-each select="./mods:name[.//mods:roleTerm = 'Author']">
			<xsl:if test="./preceding-sibling::mods:name[.//mods:roleTerm = 'Author']"><xsl:text disable-output-escaping="yes">&#x20;and&#x20;</xsl:text></xsl:if>
			<xsl:value-of select="./mods:namePart"/>
		</xsl:for-each>
		<xsl:text disable-output-escaping="yes">&quot;</xsl:text>
	</xsl:template>
	
	<xsl:template name="editorsIfGiven">
		<xsl:if test="./mods:name[.//mods:roleTerm = 'Editor']"><xsl:call-template name="editors"/></xsl:if>
	</xsl:template>
	<xsl:template name="editors">
		<xsl:text disable-output-escaping="yes">,&#x0A;editor = &quot;</xsl:text>
		<xsl:for-each select="./mods:name[.//mods:roleTerm = 'Editor']">
			<xsl:if test="./preceding-sibling::mods:name[.//mods:roleTerm = 'Editor']"><xsl:text disable-output-escaping="yes">&#x20;and&#x20;</xsl:text></xsl:if>
			<xsl:value-of select="./mods:namePart"/>
		</xsl:for-each>
		<xsl:text disable-output-escaping="yes">&quot;</xsl:text>
	</xsl:template>
	
	<xsl:template name="dateJournal">
		<xsl:text disable-output-escaping="yes">,&#x0A;year = &quot;</xsl:text>
		<xsl:value-of select=".//mods:relatedItem[./@type = 'host']/mods:part/mods:date"/>
		<xsl:text disable-output-escaping="yes">&quot;</xsl:text>
	</xsl:template>
	
	<xsl:template name="dateBook">
		<xsl:text disable-output-escaping="yes">,&#x0A;year = &quot;</xsl:text>
		<xsl:value-of select=".//mods:originInfo/mods:dateIssued"/>
		<xsl:text disable-output-escaping="yes">&quot;</xsl:text>
	</xsl:template>
	
	<xsl:template name="title">
		<xsl:text disable-output-escaping="yes">,&#x0A;title = &quot;</xsl:text>
		<xsl:value-of select="./mods:titleInfo/mods:title"/>
		<xsl:text disable-output-escaping="yes">&quot;</xsl:text>
	</xsl:template>
	
	<xsl:template name="bookTitle">
		<xsl:text disable-output-escaping="yes">,&#x0A;booktitle = &quot;</xsl:text>
		<xsl:value-of select="./mods:titleInfo/mods:title"/>
		<xsl:text disable-output-escaping="yes">&quot;</xsl:text>
	</xsl:template>
	
	<xsl:template name="journal">
		<xsl:text disable-output-escaping="yes">,&#x0A;journal = &quot;</xsl:text>
		<xsl:value-of select=".//mods:relatedItem[./@type = 'host']/mods:titleInfo/mods:title"/>
		<xsl:text disable-output-escaping="yes">&quot;</xsl:text>
	</xsl:template>
	
	<xsl:template name="volume">
		<xsl:text disable-output-escaping="yes">,&#x0A;volume = &quot;</xsl:text>
		<xsl:value-of select=".//mods:relatedItem[./@type = 'host']/mods:part/mods:detail[./@type = 'volume']/mods:number"/>
		<xsl:text disable-output-escaping="yes">&quot;</xsl:text>
	</xsl:template>
	
	<xsl:template name="publisherIfGiven">
		<xsl:if test="./mods:originInfo/mods:publisher or ./mods:originInfo/mods:place/mods:placeTerm"><xsl:call-template name="publisher"/></xsl:if>
	</xsl:template>
	<xsl:template name="publisher">
		<xsl:text disable-output-escaping="yes">,&#x0A;publisher = &quot;</xsl:text>
		<xsl:choose>
			<xsl:when test="./mods:originInfo/mods:publisher and ./mods:originInfo/mods:place/mods:placeTerm">
				<xsl:value-of select="./mods:originInfo/mods:publisher"/>,&#x20;<xsl:value-of select="./mods:originInfo/mods:place/mods:placeTerm"/>
			</xsl:when>
			<xsl:when test="./mods:originInfo/mods:publisher">
				<xsl:value-of select="./mods:originInfo/mods:publisher"/>
			</xsl:when>
			<xsl:when test="./mods:originInfo/mods:place/mods:placeTerm">
				<xsl:value-of select="./mods:originInfo/mods:place/mods:placeTerm"/>
			</xsl:when>
			<xsl:otherwise>UNKNOWN</xsl:otherwise>
		</xsl:choose>
		<xsl:text disable-output-escaping="yes">&quot;</xsl:text>
	</xsl:template>
	
	<xsl:template name="paginationIfGiven">
		<xsl:if test=".//mods:part/mods:extent[./@unit = 'page']/mods:start"><xsl:call-template name="pagination"/></xsl:if>
	</xsl:template>
	<xsl:template name="pagination">
		<xsl:text disable-output-escaping="yes">,&#x0A;pages = &quot;</xsl:text>
		<xsl:value-of select=".//mods:part/mods:extent[./@unit = 'page']/mods:start"/>-<xsl:value-of select=".//mods:part/mods:extent[./@unit = 'page']/mods:end"/>
		<xsl:text disable-output-escaping="yes">&quot;</xsl:text>
	</xsl:template>
</xsl:stylesheet>