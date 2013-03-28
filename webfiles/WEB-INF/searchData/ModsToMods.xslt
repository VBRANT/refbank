<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fo="http://www.w3.org/1999/XSL/Format" xmlns:mods="http://www.loc.gov/mods/v3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
	<xsl:output omit-xml-declaration="yes"/>
	
	<xsl:template match="/">
		<xsl:apply-templates select="//mods:mods"/>
	</xsl:template>
	
	<xsl:template match="mods:mods">
		<xsl:copy-of select="."/>
	</xsl:template>
	
</xsl:stylesheet>