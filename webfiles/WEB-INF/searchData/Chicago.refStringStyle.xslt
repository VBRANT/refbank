<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes"/>
	<xsl:output omit-xml-declaration="yes"/>
	
	<xsl:template match="//bibRef">
		<html><xsl:call-template name="author"/><xsl:call-template name="title"/><xsl:call-template name="hostVolume"/><xsl:call-template name="isbn"/><xsl:call-template name="doi"/><xsl:call-template name="url"/>.</html>
		<xsl:text disable-output-escaping="yes">&#xa;</xsl:text>
	</xsl:template>
	
	<xsl:template name="author">
		<xsl:choose>
			<xsl:when test="not(./author) and ./editor"><xsl:call-template name="editors"/>, ed<xsl:if test="count(./editor) > 1">s</xsl:if>.</xsl:when>
			<xsl:otherwise><xsl:call-template name="authors"/></xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	<xsl:template name="authors">
		<xsl:choose>
			<xsl:when test="count(./author) = 1"><xsl:value-of select="./author"/></xsl:when>
			<xsl:when test="count(./author) = 2"><xsl:value-of select="./author[1]"/> and <xsl:value-of select="./author[2]"/></xsl:when>
			<xsl:otherwise><xsl:for-each select="./author"><xsl:if test="./preceding-sibling::author">, <xsl:if test="not(./following-sibling::author)">and </xsl:if></xsl:if><xsl:value-of select="."/></xsl:for-each></xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="year">
		<xsl:if test="./year"><xsl:value-of select="./year"/></xsl:if>
	</xsl:template>
	<xsl:template name="title">
		<xsl:choose>
			<xsl:when test="./volumeTitle or ./journal"> "<xsl:call-template name="titleString"/>"</xsl:when>
			<xsl:otherwise><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><i><xsl:call-template name="titleString"/></i></xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	<xsl:template name="titleString"><!-- TODO figure out why substring has to go to string-lenth to get last character -->
		<xsl:if test="./title"><xsl:value-of select="./title"/><xsl:if test="not(contains('?!.', substring(./title, (string-length(./title)-0))))">.</xsl:if></xsl:if>
	</xsl:template>
	
	<xsl:template name="pagination">
		<xsl:choose>
			<xsl:when test="./pagination"><xsl:value-of select="./pagination"/></xsl:when>
			<xsl:when test="./firstPage"><xsl:value-of select="./firstPage"/><xsl:if test="./lastPage and not(./lastPage = ./firstPage)">-<xsl:value-of select="./lastPage"/></xsl:if></xsl:when>
			<xsl:otherwise/>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="isbn">
		<xsl:if test="./isbn">, ISBN <xsl:value-of select="./isbn"/></xsl:if>
	</xsl:template>
	<xsl:template name="doi">
		<xsl:if test="./doi">, doi: <xsl:value-of select="./doi"/></xsl:if>
	</xsl:template>
	<xsl:template name="url">
		<xsl:if test="./url">, <xsl:value-of select="./url"/></xsl:if>
	</xsl:template>
	
	<xsl:template name="hostVolume">
		<xsl:choose>
			<xsl:when test="./@type = 'book'"><xsl:call-template name="bookVolume"/><xsl:call-template name="locationAndPublisher"/>, <xsl:call-template name="year"/></xsl:when>
			<xsl:when test="./@type = 'book chapter'"> In<xsl:call-template name="volumeTitle"/><xsl:call-template name="editor"/>, <xsl:call-template name="pagination"/>.<xsl:call-template name="locationAndPublisher"/>, <xsl:call-template name="year"/></xsl:when>
			<xsl:when test="./@type = 'journal volume' and ./journal and not(./journal = ./title)"><xsl:call-template name="journal"/>, <xsl:call-template name="year"/></xsl:when>
			<xsl:when test="./@type = 'journal volume'"><xsl:call-template name="parts"/>, <xsl:call-template name="year"/></xsl:when>
			<xsl:when test="./@type = 'journal article' and ./volumeTitle"> In<xsl:call-template name="volumeTitle"/><xsl:call-template name="editor"/><xsl:call-template name="journal"/> (<xsl:call-template name="year"/>): <xsl:call-template name="pagination"/></xsl:when>
			<xsl:when test="./@type = 'journal article'"><xsl:call-template name="journal"/> (<xsl:call-template name="year"/>): <xsl:call-template name="pagination"/></xsl:when>
			<xsl:when test="./@type = 'proceedings'"><xsl:call-template name="locationAndPublisher"/>, <xsl:call-template name="year"/></xsl:when>
			<xsl:when test="./@type = 'proceedings paper'"> In<xsl:call-template name="volumeTitle"/><xsl:call-template name="editor"/><xsl:if test="./pagination or ./firstPage">, <xsl:call-template name="pagination"/></xsl:if>, <xsl:call-template name="locationAndPublisher"/>, <xsl:call-template name="year"/></xsl:when>
			<xsl:when test="./@type = 'url'"/>
			<xsl:otherwise>UNKNOWN_REFERENCE_TYPE: <xsl:value-of select="./@type"/></xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="editor">
		<xsl:if test="./editor">, ed. <xsl:call-template name="editors"/></xsl:if>
	</xsl:template>
	<xsl:template name="editors">
		<xsl:choose>
			<xsl:when test="count(./editor) = 1"><xsl:value-of select="./editor"/></xsl:when>
			<xsl:when test="count(./editor) = 2"><xsl:value-of select="./editor[1]"/> and <xsl:value-of select="./editor[2]"/></xsl:when>
			<xsl:otherwise><xsl:for-each select="./editor"><xsl:if test="./preceding-sibling::editor">, <xsl:if test="not(./following-sibling::editor)">and </xsl:if></xsl:if><xsl:value-of select="."/></xsl:for-each></xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="volumeTitle"><!-- TODO figure out why substring has to go to string-lenth to get last character -->
		<xsl:if test="./volumeTitle"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><i><xsl:for-each select="./volumeTitle"><xsl:if test="./preceding-sibling::volumeTitle"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text></xsl:if><xsl:value-of select="."/><xsl:if test="not(contains('?!.', substring(., (string-length(.)-0))))">.</xsl:if></xsl:for-each></i></xsl:if>
	</xsl:template>
	
	<xsl:template name="bookVolume">
		<xsl:if test="./volume"> Volume <xsl:value-of select="./volume"/>.</xsl:if>
	</xsl:template>
	
	<xsl:template name="journal">
		<xsl:if test="./journal"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><i><xsl:value-of select="./journal"/></i><xsl:call-template name="parts"/></xsl:if>
	</xsl:template>
	
	<xsl:template name="parts">
		<xsl:choose>
			<xsl:when test="./numero and ./issue and ./volume"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select="./volume"/> (<xsl:value-of select="./issue"/>), No. <xsl:value-of select="./numero"/></xsl:when>
			<xsl:when test="./numero and ./issue"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select="./issue"/>, No. <xsl:value-of select="./numero"/></xsl:when>
			<xsl:when test="./numero and ./volume"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select="./volume"/>, No. <xsl:value-of select="./numero"/></xsl:when>
			<xsl:when test="./issue and ./volume"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select="./volume"/> (<xsl:value-of select="./issue"/>)</xsl:when>
			<xsl:when test="./volume"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select="./volume"/></xsl:when>
			<xsl:when test="./numero"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select="./numero"/></xsl:when>
			<xsl:when test="./issue"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select="./issue"/></xsl:when>
			<xsl:otherwise/>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="locationAndPublisher">
		<xsl:choose>
			<xsl:when test="./publisher and ./location"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select="./location"/>: <xsl:value-of select="./publisher"/></xsl:when>
			<xsl:when test="./publisher"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select="./publisher"/></xsl:when>
			<xsl:when test="./location"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select="./location"/></xsl:when>
			<xsl:otherwise/>
		</xsl:choose>
	</xsl:template>
	
</xsl:stylesheet>
