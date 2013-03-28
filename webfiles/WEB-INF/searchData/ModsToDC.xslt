<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:mods="http://www.loc.gov/mods/v3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:dc="http://purl.org/dc/elements/1.1/">
	<xsl:output omit-xml-declaration="yes"/>
	
	<xsl:template match="/">
		<reference><xsl:text>&#x0A;</xsl:text>
		<xsl:apply-templates select="//mods:mods"/>
		<xsl:call-template name="id"/>
		<xsl:text>&#x0A;</xsl:text></reference>
	</xsl:template>
	
	<xsl:template match="mods:mods">
		<xsl:choose>
			<xsl:when test="//mods:classification = 'journal article'">
				<dc:type>Journal Article</dc:type>
				<xsl:call-template name="authors"/>
				<xsl:call-template name="title"/>
				<xsl:call-template name="dateJournal"/>
				<xsl:call-template name="journal"/>
			</xsl:when>
			<xsl:when test="//mods:classification = 'journal volume'">
				<dc:type>Journal Volume</dc:type>
				<xsl:call-template name="authors"/>
				<xsl:call-template name="title"/>
				<xsl:call-template name="dateJournal"/>
				<xsl:call-template name="journal"/>
			</xsl:when>
			<xsl:when test="//mods:classification = 'book chapter'">
				<dc:type>Book Chapter</dc:type>
				<xsl:call-template name="authors"/>
				<xsl:call-template name="title"/>
				<xsl:apply-templates select="./mods:relatedItem[./@type = 'host']"/>
			</xsl:when>
			<xsl:when test="//mods:classification = 'book'">
				<dc:type>Book</dc:type>
				<xsl:call-template name="authors"/>
				<xsl:call-template name="title"/>
				<xsl:call-template name="dateBook"/>
				<xsl:call-template name="publisher"/>
			</xsl:when>
			<xsl:when test="//mods:classification = 'proceedings paper' or //mods:classification = 'conference paper'">
				<dc:type>Proceedings Paper</dc:type>
				<xsl:call-template name="authors"/>
				<xsl:call-template name="title"/>
				<xsl:apply-templates select="./mods:relatedItem[./@type = 'host']"/>
			</xsl:when>
			<xsl:when test="//mods:classification = 'proceedings' or //mods:classification = 'conference proceedings'">
				<dc:type>Proceedings</dc:type>
				<xsl:call-template name="authors"/>
				<xsl:call-template name="title"/>
				<xsl:call-template name="dateBook"/>
				<xsl:call-template name="publisherIfGiven"/>
			</xsl:when>
			<xsl:otherwise>Dublin Core output is currently not supported for reference type '<xsl:value-of select="//mods:classification"/>'. Please contact your system administrator.</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="id">
		<xsl:if test=".//mods:identifier[./@type = 'RefBankID']"><xsl:text>&#x0A;</xsl:text><dc:identifier><xsl:value-of select=".//mods:identifier[./@type = 'RefBankID']"/></dc:identifier></xsl:if>
	</xsl:template>
	
	<xsl:template match="mods:relatedItem[./@type = 'host']">
		<xsl:choose>
			<xsl:when test="//mods:classification = 'proceedings paper' or //mods:classification = 'conference paper'">
				<xsl:call-template name="dateBook"/>
				<xsl:call-template name="editors"/>
				<xsl:call-template name="hostBook"/>
				<xsl:call-template name="publisherIfGiven"/>
			</xsl:when>
			<xsl:otherwise>
				<xsl:call-template name="dateBook"/>
				<xsl:call-template name="editors"/>
				<xsl:call-template name="hostBook"/>
				<xsl:call-template name="publisher"/>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="authors">
		<xsl:for-each select=".//mods:name[.//mods:roleTerm = 'Author']/mods:namePart">
			<xsl:text>&#x0A;</xsl:text><dc:creator><xsl:value-of select="."/></dc:creator>
		</xsl:for-each>
	</xsl:template>
	
	<xsl:template name="editors">
		<xsl:for-each select=".//mods:name[.//mods:roleTerm = 'Editor']/mods:namePart">
			<xsl:text>&#x0A;</xsl:text><dc:contributor><xsl:value-of select="."/></dc:contributor>
		</xsl:for-each>
	</xsl:template>
	
	<xsl:template name="dateJournal">
		<xsl:text>&#x0A;</xsl:text><dc:date><xsl:value-of select=".//mods:relatedItem[./@type = 'host']/mods:part/mods:date"/></dc:date>
	</xsl:template>
	
	<xsl:template name="dateBook">
		<xsl:text>&#x0A;</xsl:text><dc:date><xsl:value-of select=".//mods:originInfo/mods:dateIssued"/></dc:date>
	</xsl:template>
	
	<xsl:template name="title">
		<xsl:text>&#x0A;</xsl:text><dc:title><xsl:value-of select="./mods:titleInfo/mods:title"/></dc:title>
	</xsl:template>
	
	<xsl:template name="journal">
		<xsl:text>&#x0A;</xsl:text><dc:description><xsl:value-of select=".//mods:relatedItem[./@type = 'host']/mods:titleInfo/mods:title"/> (<xsl:value-of select=".//mods:relatedItem[./@type = 'host']/mods:part/mods:detail[./@type = 'volume']/mods:number"/>)<xsl:call-template name="pagination"/></dc:description>
	</xsl:template>
	
	<xsl:template name="hostBook">
		<xsl:text>&#x0A;</xsl:text><dc:description><xsl:value-of select="./mods:titleInfo/mods:title"/><xsl:call-template name="pagination"/></dc:description>
	</xsl:template>
	
	<xsl:template name="publisherIfGiven">
		<xsl:if test="./mods:originInfo/mods:publisher or ./mods:originInfo/mods:place/mods:placeTerm"><xsl:call-template name="publisher"/></xsl:if>
	</xsl:template>
	<xsl:template name="publisher">
		<xsl:text disable-output-escaping="yes">&#x0A;</xsl:text>
		<xsl:choose>
			<xsl:when test="./mods:originInfo/mods:publisher and ./mods:originInfo/mods:place/mods:placeTerm">
				<dc:publisher><xsl:value-of select="./mods:originInfo/mods:publisher"/>,&#x20;<xsl:value-of select="./mods:originInfo/mods:place/mods:placeTerm"/></dc:publisher>
			</xsl:when>
			<xsl:when test="./mods:originInfo/mods:publisher">
				<dc:publisher><xsl:value-of select="./mods:originInfo/mods:publisher"/></dc:publisher>
			</xsl:when>
			<xsl:when test="./mods:originInfo/mods:place/mods:placeTerm">
				<dc:publisher><xsl:value-of select="./mods:originInfo/mods:place/mods:placeTerm"/></dc:publisher>
			</xsl:when>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="pagination">
		<xsl:if test=".//mods:part/mods:extent[./@unit = 'page']">: <xsl:value-of select=".//mods:part/mods:extent[./@unit = 'page']/mods:start"/><xsl:if test=".//mods:part/mods:extent[./@unit = 'page']/mods:end">-<xsl:value-of select=".//mods:part/mods:extent[./@unit = 'page']/mods:end"/></xsl:if></xsl:if>
	</xsl:template>
</xsl:stylesheet>