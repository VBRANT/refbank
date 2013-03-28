<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes"/>
	<xsl:output omit-xml-declaration="yes"/>
	
	<!--
Harvard
Sautter, G., Böhm, K. & Agosti, D., 2007. A Quantitative Comparison of XML Schemas for Taxonomic Publications. Biodiversity Informatics, 4, pp.1–13.

Blainey, G 2003a, Black kettle and full moon: daily life in a vanished Australia, Penguin/Viking, Camberwell, Victoria.

Blainey, G 2003b, The rush that never ended: a history of Australian mining, 5th edn, Melbourne University Press, Carlton, Vic..

Scutt, JA 2003a, 'Future access - discrimination and the Disability Discrimination Act', Access, vol. 5, no.3, pp. 6-10.

Scutt, JA 2003b, 'Without precedent: sex/gender discrimination in the High Court', Alternative Law Journal, vol. 28, no. 2, pp. 74-77.

BOOK: Besanko, D,  Dranove, D, Shanley, M & Schaefer, S 2003, Economics of strategy, 3rd edn, J.Wiley, New York.
BOOK: Author(s) of book - surname and initials Year of publication, Title of book - italicised, Edition, Publisher, Place of publication.
BOOK CHAPTER: Author(s) of chapter - surname and initials Year of publication, 'Title of chapter - in single quotation marks' [in] Author of book (if different), Title of book - italicised, Edition, Publisher, Place of publication, (optional) page numbers.
JOURNAL ARTICLE: Author(s) of article -surname and initials Year of publication, 'Title of article - in single quotation marks', Journal name - italicised, volume number, issue number, page number(s).
CONF PAPER: Author(s) of paper -surname and initials Year of publication, 'Title of paper - in single quotation marks' [in] Editor (if applicable), Title of published proceeding which may include place held and date(s) - italicised, Publisher, Place of publication, Page numbers.
	 -->
	
	<xsl:template match="//bibRef">
		<xsl:call-template name="author"/><xsl:call-template name="year"/><xsl:call-template name="title"/><xsl:call-template name="hostVolume"/><xsl:call-template name="pagination"/><xsl:call-template name="isbn"/><xsl:call-template name="doi"/><xsl:call-template name="url"/>
		<xsl:text disable-output-escaping="yes">&#xa;</xsl:text>
	</xsl:template>
	
	<xsl:template name="author">
		<xsl:choose>
			<xsl:when test="not(./author) and ./editor and ./year"><xsl:call-template name="editors"/>, Ed<xsl:if test="count(./editor) > 1">s</xsl:if></xsl:when>
			<xsl:when test="not(./author) and ./editor"><xsl:call-template name="editors"/> (Ed<xsl:if test="count(./editor) > 1">s</xsl:if>)</xsl:when>
			<xsl:otherwise><xsl:call-template name="authors"/></xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	<xsl:template name="authors">
		<xsl:for-each select="./author"><xsl:if test="./preceding-sibling::author">, </xsl:if><xsl:value-of select="."/></xsl:for-each>
	</xsl:template>
	
	<xsl:template name="year">
		<xsl:if test="./year"> (<xsl:value-of select="./year"/>)</xsl:if>
	</xsl:template>
	<xsl:template name="title"><!-- TODO figure out why substring has to go to string-lenth to get last character -->
		<xsl:if test="./title">: <xsl:value-of select="./title"/><xsl:if test="not(contains('?!.', substring(./title, (string-length(./title)-0))))">.</xsl:if></xsl:if>
	</xsl:template>
	
	<xsl:template name="pagination">
		<xsl:choose>
			<xsl:when test="./pagination">: <xsl:value-of select="./pagination"/></xsl:when>
			<xsl:when test="./firstPage">: <xsl:value-of select="./firstPage"/><xsl:if test="./lastPage and not(./lastPage = ./firstPage)">-<xsl:value-of select="./lastPage"/></xsl:if></xsl:when>
			<xsl:otherwise/>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="isbn">
		<xsl:if test="./isbn">, ISBN: <xsl:value-of select="./isbn"/></xsl:if>
	</xsl:template>
	<xsl:template name="doi">
		<xsl:if test="./doi">, DOI: <xsl:value-of select="./doi"/></xsl:if>
	</xsl:template>
	<xsl:template name="url">
		<xsl:if test="./url">, URL: <xsl:value-of select="./url"/></xsl:if>
	</xsl:template>
	
	<xsl:template name="hostVolume">
		<xsl:choose>
			<xsl:when test="./@type = 'book'"><xsl:call-template name="bookVolume"/><xsl:call-template name="locationAndPublisher"/></xsl:when>
			<xsl:when test="./@type = 'book chapter'"> In<xsl:call-template name="editor"/><xsl:call-template name="volumeTitle"/><xsl:call-template name="bookVolume"/><xsl:call-template name="locationAndPublisher"/></xsl:when>
			<xsl:when test="./@type = 'journal volume' and ./journal and not(./journal = ./title)"><xsl:call-template name="journal"/></xsl:when>
			<xsl:when test="./@type = 'journal volume'"><xsl:call-template name="parts"/></xsl:when>
			<xsl:when test="./@type = 'journal article' and ./volumeTitle"> In<xsl:call-template name="editor"/><xsl:call-template name="volumeTitle"/><xsl:call-template name="journal"/></xsl:when>
			<xsl:when test="./@type = 'journal article'"><xsl:call-template name="journal"/></xsl:when>
			<xsl:when test="./@type = 'proceedings'"><xsl:call-template name="locationAndPublisher"/></xsl:when>
			<xsl:when test="./@type = 'proceedings paper'"> In<xsl:call-template name="editor"/><xsl:call-template name="volumeTitle"/><xsl:call-template name="locationAndPublisher"/></xsl:when>
			<xsl:when test="./@type = 'url'"/>
			<xsl:otherwise>UNKNOWN_REFERENCE_TYPE: <xsl:value-of select="./@type"/></xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="editor">
		<xsl:if test="./editor">: <xsl:call-template name="editors"/> (Ed<xsl:if test="count(./editor) > 1">s</xsl:if>)</xsl:if>
	</xsl:template>
	<xsl:template name="editors">
		<xsl:if test="./editor"><xsl:for-each select="./editor"><xsl:if test="./preceding-sibling::editor">, </xsl:if><xsl:value-of select="."/></xsl:for-each></xsl:if>
	</xsl:template>
	
	<xsl:template name="volumeTitle"><!-- TODO figure out why substring has to go to string-lenth to get last character -->
		<xsl:if test="./volumeTitle">:<xsl:for-each select="./volumeTitle"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select="."/><xsl:if test="not(contains('?!.', substring(., (string-length(.)-0))))">.</xsl:if></xsl:for-each></xsl:if>
	</xsl:template>
	
	<xsl:template name="bookVolume">
		<xsl:if test="./volume"> Volume <xsl:value-of select="./volume"/>.</xsl:if>
	</xsl:template>
	
	<xsl:template name="journal">
		<xsl:if test="./journal"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select="./journal"/><xsl:call-template name="parts"/></xsl:if>
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
