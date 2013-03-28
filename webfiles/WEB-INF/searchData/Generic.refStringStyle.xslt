<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes"/>
	<xsl:output omit-xml-declaration="yes"/>
	
	<!--
Harvard
Sautter, G., Böhm, K. & Agosti, D., 2007. A Quantitative Comparison of XML Schemas for Taxonomic Publications. Biodiversity Informatics, 4, pp.1–13.

Chicago
JOURNAL ARTICLE: Sautter, G., K. Böhm, and D. Agosti. 2007. ‘A Quantitative Comparison of XML Schemas for Taxonomic Publications’. Biodiversity Informatics 4: 1–13.
JOURNAL ARTICLE: Kossinets, Gueorgi, and Duncan J. Watts. “Origins of Homophily in an Evolving Social Network.” <i>American Journal of Sociology</i> 115 (2009): 405–50. doi:10.1086/599247.
BOOK: Ward, Geoffrey C., and Ken Burns. <i>The War: An Intimate History, 1941–1945</i>. New York: Knopf, 2007.
BOOK CHAPTER: Cicero, Quintus Tullius. “Handbook on Canvassing for the Consulship.” In <i>Rome: Late Republic and Principate</i>, edited by Walter Emil Kaegi Jr. and Peter White, 33–46. Chicago: University of Chicago Press, 1986.
CONF PAPER: Adelman, Rachel. “ ‘Such Stuff as Dreams Are Made On’: God’s Footstool in the Aramaic Targumim and Midrashic Tradition.” In <i>Proceedings of Conference Blah</i>, 2009.
AUTHORS: one: trivial, two: "and", three+: "," ... ", and"

Pensoft
Sautter G, Böhm K, Agosti D (2007) A Quantitative Comparison of XML Schemas for Taxonomic Publications. Biodiversity Informatics 4: 1–13.

MLA (Modern Languages Association)
Sautter, G., K. Böhm, and D. Agosti. ‘A Quantitative Comparison of XML Schemas for Taxonomic Publications’. Biodiversity Informatics 4 (2007): 1–13. Print.

APA (American Psychology Association 6th edition)
Sautter, G., Böhm, K., & Agosti, D. (2007). A Quantitative Comparison of XML Schemas for Taxonomic Publications. Biodiversity Informatics, 4, 1–13.
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
