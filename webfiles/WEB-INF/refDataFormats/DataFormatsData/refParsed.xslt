<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:mods="http://www.loc.gov/mods/v3">
	<xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes"/>
	<xsl:output omit-xml-declaration="yes"/>
	
	<xsl:template match="//reference">
		<mods:mods><xsl:call-template name="title"/><xsl:call-template name="author"/><mods:typeOfResource>text</mods:typeOfResource><xsl:call-template name="hostVolume"/><xsl:call-template name="isbn"/><xsl:call-template name="doi"/><xsl:call-template name="url"/><mods:classification><xsl:value-of select="./@type"/></mods:classification></mods:mods>
	</xsl:template>
	
	<xsl:template name="author">
		<xsl:choose>
			<xsl:when test="not(./author) and ./editor"><xsl:call-template name="editors"/></xsl:when>
			<xsl:otherwise><xsl:call-template name="authors"/></xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="authors">
		<xsl:for-each select="./author"><mods:name type="personal"><mods:role><mods:roleTerm>Author</mods:roleTerm></mods:role><mods:namePart><xsl:value-of select="."/></mods:namePart></mods:name></xsl:for-each>
	</xsl:template>
	<xsl:template name="editors">
		<xsl:for-each select="./editor"><mods:name type="personal"><mods:role><mods:roleTerm>Editor</mods:roleTerm></mods:role><mods:namePart><xsl:value-of select="."/></mods:namePart></mods:name></xsl:for-each>
	</xsl:template>
	
	<xsl:template name="title">
		<xsl:if test="./title"><mods:titleInfo><mods:title><xsl:value-of select="./title"/></mods:title></mods:titleInfo></xsl:if>
	</xsl:template>
	
	<xsl:template name="pagination">
		<xsl:choose>
			<xsl:when test="./pagination and contains(./pagination, '-')"><mods:part><mods:extent unit="page"><mods:start><xsl:value-of select="substring-before(./pagination, '-')"/></mods:start><mods:end><xsl:value-of select="substring-after(./pagination, '-')"/></mods:end></mods:extent></mods:part></xsl:when>
			<xsl:when test="./pagination"><mods:part><mods:extent unit="page"><mods:start><xsl:value-of select="/pagination"/></mods:start><mods:end><xsl:value-of select="./pagination"/></mods:end></mods:extent></mods:part></xsl:when>
			<xsl:when test="./firstPage and ./lastPage"><mods:extent unit="page"><mods:start><xsl:value-of select="./firstPage"/></mods:start><mods:end><xsl:value-of select="./lastPage"/></mods:end></mods:extent></xsl:when>
			<xsl:when test="./firstPage"><mods:extent unit="page"><mods:start><xsl:value-of select="./firstPage"/></mods:start><mods:end><xsl:value-of select="./firstPage"/></mods:end></mods:extent></xsl:when>
			<xsl:otherwise/>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="isbn">
		<xsl:if test="./isbn"><mods:identifier type="ISBN"><xsl:value-of select="./isbn"/></mods:identifier></xsl:if>
	</xsl:template>
	<xsl:template name="doi">
		<xsl:if test="./doi"><mods:identifier type="DOI"><xsl:value-of select="./doi"/></mods:identifier></xsl:if>
	</xsl:template>
	<xsl:template name="url">
		<xsl:if test="./url"><mods:location><mods:url><xsl:value-of select="./url"/></mods:url></mods:location></xsl:if>
	</xsl:template>
	
	<xsl:template name="hostVolume">
		<xsl:choose>
			<xsl:when test="./@type = 'book' or ./@type = 'proceedings'"><xsl:call-template name="originInfo"/></xsl:when>
			<xsl:when test="./@type = 'book chapter' or ./@type = 'proceedings paper'"><mods:relatedItem type="host"><xsl:call-template name="editors"/><xsl:call-template name="volumeTitle"/><xsl:call-template name="originInfo"/><xsl:call-template name="partInfoBook"/></mods:relatedItem></xsl:when>
			<xsl:when test="./@type = 'journal volume' or ./@type = 'journal article'"><mods:relatedItem type="host"><xsl:if test="./author"><xsl:call-template name="editors"/></xsl:if><xsl:call-template name="volumeTitle"/><xsl:call-template name="journalName"/><xsl:call-template name="partInfoJournal"/></mods:relatedItem></xsl:when>
			<xsl:when test="./@type = 'url'"/>
			<xsl:otherwise>UNKNOWN_REFERENCE_TYPE: <xsl:value-of select="./@type"/></xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="volumeTitle">
		<xsl:if test="./volumeTitle"><mods:titleInfo><mods:title><xsl:for-each select="./volumeTitle"><xsl:if test="./preceding-sibling::volumeTitle"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text></xsl:if><xsl:value-of select="."/><xsl:if test="not(contains('?!.', substring(., (string-length(.)-0))))">.</xsl:if></xsl:for-each></mods:title></mods:titleInfo></xsl:if>
	</xsl:template>
	
	<xsl:template name="journalName">
		<xsl:if test="./journal"><mods:titleInfo><mods:title><xsl:value-of select="./journal"/></mods:title></mods:titleInfo></xsl:if>
	</xsl:template>
	
	<xsl:template name="originInfo">
		<mods:originInfo><xsl:call-template name="yearOriginInfo"/><xsl:call-template name="publisher"/><xsl:call-template name="location"/></mods:originInfo>
	</xsl:template>
	
	<xsl:template name="yearOriginInfo">
		<xsl:if test="./year"><mods:dateIssued><xsl:value-of select="./year"/></mods:dateIssued></xsl:if>
	</xsl:template>
	<xsl:template name="publisher">
			<xsl:if test="./publisher"><mods:publisher><xsl:value-of select="./publisher"/></mods:publisher></xsl:if>
	</xsl:template>
	<xsl:template name="location">
		<xsl:if test="./location"><mods:place><mods:placeTerm><xsl:value-of select="./location"/></mods:placeTerm></mods:place></xsl:if>
	</xsl:template>
	
	<xsl:template name="partInfoBook">
		<xsl:if test="./pagination or ./firstPage"><mods:part><xsl:call-template name="pagination"/></mods:part></xsl:if>
	</xsl:template>
	
	<xsl:template name="partInfoJournal">
		<mods:part><xsl:call-template name="pagination"/><xsl:call-template name="yearJournal"/><xsl:call-template name="partJournal"/></mods:part>
	</xsl:template>
	
	<xsl:template name="yearJournal">
		<xsl:if test="./year"><mods:date><xsl:value-of select="./year"/></mods:date></xsl:if>
	</xsl:template>
	<xsl:template name="partJournal">
		<xsl:if test="./volume"><mods:detail type="volume"><mods:number><xsl:value-of select="./volume"/></mods:number></mods:detail></xsl:if><xsl:if test="./issue"><mods:detail type="issue"><mods:number><xsl:value-of select="./issue"/></mods:number></mods:detail></xsl:if><xsl:if test="./numero"><mods:detail type="number"><mods:number><xsl:value-of select="./numero"/></mods:number></mods:detail></xsl:if>
	</xsl:template>
	
</xsl:stylesheet>
