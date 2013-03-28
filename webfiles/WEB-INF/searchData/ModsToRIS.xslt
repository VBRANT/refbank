<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fo="http://www.w3.org/1999/XSL/Format" xmlns:mods="http://www.loc.gov/mods/v3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
	<xsl:output omit-xml-declaration="yes"/>
	
	<xsl:template match="/">
		<xsl:apply-templates select="//mods:mods"/>
	</xsl:template>
	
	<xsl:template match="mods:mods">
		<xsl:choose>
			<xsl:when test="//mods:classification = 'journal article'">
				<xsl:text>TY  - JOUR</xsl:text>
				<xsl:call-template name="authors"/>
				<xsl:call-template name="title"/>
				<xsl:call-template name="dateJournal"/>
				<xsl:call-template name="journal"/>
				<xsl:call-template name="volume"/>
				<xsl:call-template name="pagination"/>
				<xsl:text>&#x0A;ER  -</xsl:text>
			</xsl:when>
			<xsl:when test="//mods:classification = 'journal volume'">
				<xsl:text>TY  - JFULL</xsl:text>
				<xsl:call-template name="authors"/>
				<xsl:call-template name="title"/>
				<xsl:call-template name="dateJournal"/>
				<xsl:call-template name="journal"/>
				<xsl:call-template name="volume"/>
				<xsl:text>&#x0A;ER  -</xsl:text>
			</xsl:when>
			<xsl:when test="//mods:classification = 'book chapter'">
				<xsl:text>TY  - CHAP</xsl:text>
				<xsl:call-template name="authors"/>
				<xsl:call-template name="chapterTitle"/>
				<xsl:apply-templates select="./mods:relatedItem[./@type = 'host']"/>
				<xsl:text>&#x0A;ER  -</xsl:text>
			</xsl:when>
			<xsl:when test="//mods:classification = 'book'">
				<xsl:text>TY  - BOOK</xsl:text>
				<xsl:call-template name="authors"/>
				<xsl:call-template name="title"/>
				<xsl:call-template name="dateBook"/>
				<xsl:call-template name="publisher"/>
				<xsl:text>&#x0A;ER  -</xsl:text>
			</xsl:when>
			<xsl:when test="//mods:classification = 'proceedings paper' or //mods:classification = 'conference paper'">
				<xsl:text>TY  - CPAPER</xsl:text>
				<xsl:call-template name="authors"/>
				<xsl:call-template name="title"/>
				<xsl:apply-templates select="./mods:relatedItem[./@type = 'host']"/>
				<xsl:text>&#x0A;ER  -</xsl:text>
			</xsl:when>
			<xsl:when test="//mods:classification = 'proceedings' or //mods:classification = 'conference proceedings'">
				<xsl:text>TY  - CONF</xsl:text>
				<xsl:call-template name="authors"/>
				<xsl:call-template name="title"/>
				<xsl:call-template name="dateBook"/>
				<xsl:call-template name="publisherIfGiven"/>
				<xsl:text>&#x0A;ER  -</xsl:text>
			</xsl:when>
			<xsl:otherwise>RIS output is currently not supported for reference type '<xsl:value-of select="//mods:classification"/>'. Please contact your system administrator.</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template match="mods:relatedItem[./@type = 'host']">
		<xsl:choose>
			<xsl:when test="//mods:classification = 'proceedings paper' or //mods:classification = 'conference paper'">
				<xsl:call-template name="dateBook"/>
				<xsl:call-template name="title"/>
				<xsl:call-template name="editors"/>
				<xsl:call-template name="publisherIfGiven"/>
				<xsl:call-template name="paginationIfGiven"/>
			</xsl:when>
			<xsl:otherwise>
				<xsl:call-template name="dateBook"/>
				<xsl:call-template name="title"/>
				<xsl:call-template name="editors"/>
				<xsl:call-template name="publisher"/>
				<xsl:call-template name="pagination"/>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="authors">
		<xsl:for-each select=".//mods:name[.//mods:roleTerm = 'Author']/mods:namePart">
			<xsl:text>&#x0A;AU  - </xsl:text><xsl:value-of select="."/>
		</xsl:for-each>
	</xsl:template>
	
	<xsl:template name="editors">
		<xsl:for-each select=".//mods:name[.//mods:roleTerm = 'Editor']/mods:namePart">
			<xsl:text>&#x0A;A2  - </xsl:text><xsl:value-of select="."/>
		</xsl:for-each>
	</xsl:template>
	
	<xsl:template name="dateJournal">
		<xsl:text>&#x0A;PY  - </xsl:text><xsl:value-of select=".//mods:relatedItem[./@type = 'host']/mods:part/mods:date"/>
	</xsl:template>
	
	<xsl:template name="dateBook">
		<xsl:text>&#x0A;PY  - </xsl:text><xsl:value-of select=".//mods:originInfo/mods:dateIssued"/>
	</xsl:template>
	
	<xsl:template name="title">
		<xsl:text>&#x0A;TI  - </xsl:text><xsl:value-of select="./mods:titleInfo/mods:title"/>
	</xsl:template>
	
	<xsl:template name="chapterTitle">
		<xsl:text>&#x0A;Tl  - </xsl:text><xsl:value-of select="./mods:titleInfo/mods:title"/>
	</xsl:template>
	
	<xsl:template name="journal">
		<xsl:text>&#x0A;JO  - </xsl:text><xsl:value-of select=".//mods:relatedItem[./@type = 'host']/mods:titleInfo/mods:title"/>
	</xsl:template>
	
	<xsl:template name="volume">
		<xsl:text>&#x0A;VL  - </xsl:text><xsl:value-of select=".//mods:relatedItem[./@type = 'host']/mods:part/mods:detail[./@type = 'volume']/mods:number"/>
	</xsl:template>
	
	<xsl:template name="publisherIfGiven">
		<xsl:if test="./mods:originInfo/mods:publisher or ./mods:originInfo/mods:place/mods:placeTerm"><xsl:call-template name="publisher"/></xsl:if>
	</xsl:template>
	<xsl:template name="publisher">
		<xsl:if test="./mods:originInfo/mods:publisher">
			<xsl:text>&#x0A;PB  - </xsl:text><xsl:value-of select="./mods:originInfo/mods:publisher"/>
		</xsl:if>
		<xsl:if test="./mods:originInfo/mods:place/mods:placeTerm">
			<xsl:text>&#x0A;CY  - </xsl:text><xsl:value-of select="./mods:originInfo/mods:place/mods:placeTerm"/>
		</xsl:if>
	</xsl:template>
	
	<xsl:template name="paginationIfGiven">
		<xsl:if test=".//mods:part/mods:extent[./@unit = 'page']/mods:start"><xsl:call-template name="pagination"/></xsl:if>
	</xsl:template>
	<xsl:template name="pagination">
		<xsl:text>&#x0A;SP  - </xsl:text><xsl:value-of select=".//mods:part/mods:extent[./@unit = 'page']/mods:start"/>
		<xsl:text>&#x0A;EP  - </xsl:text><xsl:value-of select=".//mods:part/mods:extent[./@unit = 'page']/mods:end"/>
	</xsl:template>
</xsl:stylesheet>