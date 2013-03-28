<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:mods="http://www.loc.gov/mods/v3">
	<xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes"/>
	<xsl:output omit-xml-declaration="yes"/>
	
	<xsl:template match="//mods:mods">
		<xsl:choose>
			<xsl:when test=".//mods:originInfo">
				<!-- got origin info ==> book or proceedings, or part of either -->
				<xsl:choose>
					<xsl:when test=".//mods:relatedItem[./@type = 'host']/mods:part/mods:extent[./@unit = 'page']">
						<!-- got pagination ==> part of host volume -->
						<xsl:choose>
							<xsl:when test=".//mods:relatedItem[./@type = 'host']/mods:titleInfo/mods:title[starts-with(., 'Proc')]">proceedings paper</xsl:when>
							<xsl:otherwise>book chapter</xsl:otherwise>
						</xsl:choose>
					</xsl:when>
					<xsl:otherwise>
						<!-- no pagination ==> whole volume, or proceedings paper -->
						<xsl:choose>
							<xsl:when test=".//mods:relatedItem[./@type = 'host']/mods:titleInfo/mods:title[starts-with(., 'Proc')]">proceedings paper</xsl:when>
							<xsl:when test=".//mods:titleInfo/mods:title[starts-with(., 'Proc')]">proceedings</xsl:when>
							<xsl:otherwise>book</xsl:otherwise>
						</xsl:choose>
					</xsl:otherwise>
				</xsl:choose>
			</xsl:when>
			<xsl:otherwise>
				<!-- no origin info ==> likely journal or part thereof -->
				<xsl:choose>
					<xsl:when test=".//mods:relatedItem[./@type = 'host']/mods:part/mods:detail[./@type = 'volume']">
						<!-- got volume number ==> journal or part thereof -->
						<xsl:choose>
							<xsl:when test=".//mods:relatedItem[./@type = 'host']/mods:part/mods:extent[./@unit = 'page']">journal article</xsl:when>
							<xsl:otherwise>journal volume</xsl:otherwise>
						</xsl:choose>
					</xsl:when>
					<xsl:otherwise>
						<!-- no volume number ==> likely proceedings -->
						<xsl:choose>
							<xsl:when test=".//mods:relatedItem[./@type = 'host']/mods:part/mods:extent[./@unit = 'page']">proceedings paper</xsl:when>
							<xsl:otherwise>proceedings</xsl:otherwise>
						</xsl:choose>
					</xsl:otherwise>
				</xsl:choose>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
</xsl:stylesheet>
