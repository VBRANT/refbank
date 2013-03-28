<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:mods="http://www.loc.gov/mods/v3">
	<xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes"/>
	<xsl:output omit-xml-declaration="yes"/>
	
	<xsl:template match="//mods:mods[not(.//mods:titleInfo/mods:title)]" priority="10">
		<xsl:text disable-output-escaping="yes">ERROR:Missing title</xsl:text>
	</xsl:template>
	
	<xsl:template match="//mods:mods[not(./mods:classification = 'url') and not(.//mods:name/mods:role/mods:roleTerm = 'Author')]" priority="10">
		<xsl:text disable-output-escaping="yes">ERROR:Missing author</xsl:text>
	</xsl:template>
	
	<xsl:template match="//mods:mods[(./mods:classification = 'journal article' or ./mods:classification = 'journal volume') and not(.//mods:relatedItem[./@type = 'host']/mods:part/mods:detail[./@type = 'volume'])]" priority="10">
		<xsl:text disable-output-escaping="yes">ERROR:Missing volume number</xsl:text>
	</xsl:template>
	
	<xsl:template match="//mods:mods[(./mods:classification = 'book' or ./mods:classification = 'book chapter') and not(.//mods:originInfo/mods:publisher) and not(.//mods:originInfo/mods:place/mods:placeTerm)]" priority="10">
		<xsl:text disable-output-escaping="yes">ERROR:Missing publisher and location</xsl:text>
	</xsl:template>
	
	<xsl:template match="//mods:mods[(./mods:classification = 'journal article' or ./mods:classification = 'book chapter') and not(.//mods:relatedItem[./@type = 'host']/mods:part/mods:extent[./@unit = 'page'])]" priority="10">
		<xsl:text disable-output-escaping="yes">ERROR:Missing pagination</xsl:text>
	</xsl:template>
	
	<xsl:template match="//mods:mods">
		<xsl:call-template name="author"/><xsl:call-template name="year"/><xsl:call-template name="title"/><xsl:call-template name="hostVolume"/><xsl:call-template name="pagination"/><xsl:call-template name="isbn"/><xsl:call-template name="doi"/><xsl:call-template name="url"/>
		<xsl:text disable-output-escaping="yes">&#xa;</xsl:text>
	</xsl:template>
	
	<xsl:template name="author">
		<xsl:choose>
			<xsl:when test="not(.//mods:name[./mods:role/mods:roleTerm = 'Author']) and .//mods:name[./mods:role/mods:roleTerm = 'Editor'] and (.//mods:dateIssued or .//mods:date)"><xsl:call-template name="editors"/>, Ed<xsl:if test="count(.//mods:name[./mods:role/mods:roleTerm = 'Editor']) > 1">s</xsl:if></xsl:when>
			<xsl:when test="not(.//mods:name[./mods:role/mods:roleTerm = 'Author']) and .//mods:name[./mods:role/mods:roleTerm = 'Editor']"><xsl:call-template name="editors"/> (Ed<xsl:if test="count(.//mods:name[./mods:role/mods:roleTerm = 'Editor']) > 1">s</xsl:if>)</xsl:when>
			<xsl:otherwise><xsl:call-template name="authors"/></xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	<xsl:template name="authors">
		<xsl:for-each select=".//mods:name[./mods:role/mods:roleTerm = 'Author']"><xsl:if test="./preceding-sibling::mods:name[./mods:role/mods:roleTerm = 'Author']">, </xsl:if><xsl:value-of select="./mods:namePart"/></xsl:for-each>
	</xsl:template>
	
	<xsl:template name="year">
		<xsl:choose>
			<xsl:when test=".//mods:dateIssued"> (<xsl:value-of select=".//mods:dateIssued"/>)</xsl:when>
			<xsl:when test=".//mods:date"> (<xsl:value-of select=".//mods:date"/>)</xsl:when>
			<xsl:otherwise/>
		</xsl:choose>
	</xsl:template>
	<xsl:template name="title"><!-- TODO figure out why substring has to go to string-lenth to get last character -->
		<xsl:if test="./mods:titleInfo/mods:title">: <xsl:value-of select="./mods:titleInfo/mods:title"/><xsl:if test="not(contains('?!.', substring(./mods:titleInfo/mods:title, (string-length(./mods:titleInfo/mods:title)-0))))">.</xsl:if></xsl:if>
	</xsl:template>
	
	<xsl:template name="pagination">
		<xsl:if test=".//mods:part/mods:extent[./@unit = 'page']/mods:start">: <xsl:value-of select=".//mods:part/mods:extent[./@unit = 'page']/mods:start"/><xsl:if test=".//mods:part/mods:extent[./@unit = 'page']/mods:end and not(.//mods:part/mods:extent[./@unit = 'page']/mods:end = .//mods:part/mods:extent[./@unit = 'page']/mods:start)">-<xsl:value-of select=".//mods:part/mods:extent[./@unit = 'page']/mods:end"/></xsl:if></xsl:if>
	</xsl:template>
	
	<xsl:template name="isbn">
		<xsl:if test=".//mods:identifier[./@type = 'ISBN']">, ISBN: <xsl:value-of select=".//mods:identifier[./@type = 'ISBN']"/></xsl:if>
	</xsl:template>
	<xsl:template name="doi">
		<xsl:if test=".//mods:identifier[./@type = 'DOI']">, DOI: <xsl:value-of select=".//mods:identifier[./@type = 'DOI']"/></xsl:if>
	</xsl:template>
	<xsl:template name="url">
		<xsl:if test="false and .//mods:location/mods:url">, URL: <xsl:value-of select=".//mods:location/mods:url"/></xsl:if>
	</xsl:template>
	
	<xsl:template name="hostVolume">
		<xsl:choose>
			<xsl:when test=".//mods:classification = 'book'"><xsl:call-template name="bookVolume"/><xsl:call-template name="locationAndPublisher"/></xsl:when>
			<xsl:when test=".//mods:classification = 'book chapter'"> In<xsl:call-template name="editor"/><xsl:call-template name="volumeTitle"/><xsl:call-template name="bookVolume"/><xsl:call-template name="locationAndPublisher"/></xsl:when>
			<xsl:when test=".//mods:classification = 'journal volume' and .//mods:part/mods:detail[./@type = 'title']/mods:title"><xsl:call-template name="journal"/></xsl:when>
			<xsl:when test=".//mods:classification = 'journal volume'"><xsl:call-template name="parts"/></xsl:when>
			<xsl:when test=".//mods:classification = 'journal article' and .//mods:part/mods:detail[./@type = 'title']/mods:title"> In<xsl:call-template name="editor"/><xsl:call-template name="volumeTitle"/><xsl:call-template name="journal"/></xsl:when>
			<xsl:when test=".//mods:classification = 'journal article'"><xsl:call-template name="journal"/></xsl:when>
			<xsl:when test=".//mods:classification = 'proceedings'"><xsl:call-template name="locationAndPublisher"/></xsl:when>
			<xsl:when test=".//mods:classification = 'proceedings paper'"> In<xsl:call-template name="editor"/><xsl:call-template name="volumeTitle"/><xsl:call-template name="locationAndPublisher"/></xsl:when>
			<xsl:when test=".//mods:classification = 'url'"/>
			<xsl:otherwise>UNKNOWN_REFERENCE_TYPE: <xsl:value-of select=".//mods:classification"/></xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="editor">
		<xsl:if test=".//mods:name[./mods:role/mods:roleTerm = 'Editor']">: <xsl:call-template name="editors"/> (Ed<xsl:if test="count(.//mods:name[./mods:role/mods:roleTerm = 'Editor']) > 1">s</xsl:if>)</xsl:if>
	</xsl:template>
	<xsl:template name="editors">
		<xsl:if test=".//mods:name[./mods:role/mods:roleTerm = 'Editor']"><xsl:for-each select=".//mods:name[./mods:role/mods:roleTerm = 'Editor']"><xsl:if test="./preceding-sibling::mods:name[./mods:role/mods:roleTerm = 'Editor']">, </xsl:if><xsl:value-of select="./mods:namePart"/></xsl:for-each></xsl:if>
	</xsl:template>
	
	<xsl:template name="volumeTitle"><!-- TODO figure out why substring has to go to string-lenth to get last character -->
		<xsl:choose>
			<xsl:when test=".//mods:part/mods:detail[./@type = 'title']/mods:title">:<xsl:for-each select=".//mods:part/mods:detail[./@type = 'title']/mods:title"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select="."/><xsl:if test="not(contains('?!.', substring(., (string-length(.)-0))))">.</xsl:if></xsl:for-each></xsl:when>
			<xsl:when test=".//mods:relatedItem[./@type = 'host']/mods:titleInfo/mods:title">:<xsl:for-each select=".//mods:relatedItem[./@type = 'host']/mods:titleInfo/mods:title"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select="."/><xsl:if test="not(contains('?!.', substring(., (string-length(.)-0))))">.</xsl:if></xsl:for-each></xsl:when>
			<xsl:otherwise/>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="bookVolume">
		<xsl:if test="./volume"> Volume <xsl:value-of select="./volume"/>.</xsl:if>
	</xsl:template>
	
	<xsl:template name="journal">
		<xsl:if test=".//mods:relatedItem[./@type = 'host']/mods:titleInfo/mods:title"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select=".//mods:relatedItem[./@type = 'host']/mods:titleInfo/mods:title"/><xsl:call-template name="parts"/></xsl:if>
	</xsl:template>
	
	<xsl:template name="parts">
		<xsl:choose>
			<xsl:when test=".//mods:part/mods:detail[./@type = 'numero']/mods:number and .//mods:part/mods:detail[./@type = 'issue']/mods:number and .//mods:part/mods:detail[./@type = 'volume']/mods:number"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select=".//mods:part/mods:detail[./@type = 'volume']/mods:number"/> (<xsl:value-of select=".//mods:part/mods:detail[./@type = 'issue']/mods:number"/>), No. <xsl:value-of select=".//mods:part/mods:detail[./@type = 'numero']/mods:number"/></xsl:when>
			<xsl:when test=".//mods:part/mods:detail[./@type = 'numero']/mods:number and .//mods:part/mods:detail[./@type = 'issue']/mods:number"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select=".//mods:part/mods:detail[./@type = 'issue']/mods:number"/>, No. <xsl:value-of select=".//mods:part/mods:detail[./@type = 'numero']/mods:number"/></xsl:when>
			<xsl:when test=".//mods:part/mods:detail[./@type = 'numero']/mods:number and .//mods:part/mods:detail[./@type = 'volume']/mods:number"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select=".//mods:part/mods:detail[./@type = 'volume']/mods:number"/>, No. <xsl:value-of select=".//mods:part/mods:detail[./@type = 'numero']/mods:number"/></xsl:when>
			<xsl:when test=".//mods:part/mods:detail[./@type = 'issue']/mods:number and .//mods:part/mods:detail[./@type = 'volume']/mods:number"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select=".//mods:part/mods:detail[./@type = 'volume']/mods:number"/> (<xsl:value-of select=".//mods:part/mods:detail[./@type = 'issue']/mods:number"/>)</xsl:when>
			<xsl:when test=".//mods:part/mods:detail[./@type = 'volume']/mods:number"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select=".//mods:part/mods:detail[./@type = 'volume']/mods:number"/></xsl:when>
			<xsl:when test=".//mods:part/mods:detail[./@type = 'numero']/mods:number"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select=".//mods:part/mods:detail[./@type = 'numero']/mods:number"/></xsl:when>
			<xsl:when test=".//mods:part/mods:detail[./@type = 'issue']/mods:number"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select=".//mods:part/mods:detail[./@type = 'issue']/mods:number"/></xsl:when>
			<xsl:otherwise/>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="locationAndPublisher">
		<xsl:choose>
			<xsl:when test=".//mods:originInfo/mods:publisher and .//mods:originInfo/mods:place/mods:placeTerm"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select=".//mods:originInfo/mods:place/mods:placeTerm"/>: <xsl:value-of select=".//mods:originInfo/mods:publisher"/></xsl:when>
			<xsl:when test=".//mods:originInfo/mods:publisher"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select=".//mods:originInfo/mods:publisher"/></xsl:when>
			<xsl:when test=".//mods:originInfo/mods:place/mods:placeTerm"><xsl:text disable-output-escaping="yes">&#x20;</xsl:text><xsl:value-of select=".//mods:originInfo/mods:place/mods:placeTerm"/></xsl:when>
			<xsl:otherwise/>
		</xsl:choose>
	</xsl:template>
	
</xsl:stylesheet>
