<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
	xmlns:xs="http://www.w3.org/2001/XMLSchema" 
	xmlns:fn="http://www.w3.org/2005/xpath-functions"
	xmlns:nu="http://n.validator.nu/messages/"
	xmlns:html="http://www.w3.org/1999/xhtml"
	xmlns="http://www.w3.org/1999/xhtml">
	
	<xsl:output method="html" encoding="UTF-8" indent="yes"/>
	
	<xsl:template match="/nu:messages">
		<xsl:text disable-output-escaping='yes'>&lt;!DOCTYPE html&gt;</xsl:text>
		
		<html>
			<head>
				<title>Walidacja</title>
                <link href="../resources/styles.css" rel="stylesheet" />
			</head>
			<body>
				<div class="summary">
					<p>
                        <a>
                            <xsl:attribute name="href">
                                <xsl:value-of select="(nu:error/@url|nu:info/@url)[1]"/>
                            </xsl:attribute>
                            <xsl:value-of select="substring((nu:error/@url|nu:info/@url)[1], 8)"/>
                        </a>
					</p>
					<span class="badge errors">Errors: <xsl:value-of select="count(nu:error)"/></span>					
					<span class="badge warnings">Warnings: <xsl:value-of select="count(nu:info)"/></span>
				</div>
				<ol>
					<xsl:apply-templates select="nu:error|nu:info|nu:non-document-error"/>
				</ol>
			</body>
		</html>
	</xsl:template>
	
	<xsl:template match="nu:error|nu:info|nu:non-document-error">
		<li>
			<xsl:attribute name="class">
				<xsl:value-of select="name(.)"/>
			</xsl:attribute>
			<p class="message">
				<xsl:choose>
					<xsl:when test="name(.)='error'">
						Error:
					</xsl:when>
					<xsl:when test="name(.)='info'">
						Warning:
					</xsl:when>
					<xsl:when test="name(.)='non-document-error'">
						Error:
					</xsl:when>
				</xsl:choose>
				<xsl:apply-templates select="nu:message" />
			</p>
			<p class="location">
				<xsl:choose>
					<xsl:when test="@first-line">
						From line: <xsl:value-of select="@first-line" />,
						column: <xsl:value-of select="@first-column" />;
						To line: <xsl:value-of select="@last-line" />, 
						column: <xsl:value-of select="@last-column" />
					</xsl:when>
					<xsl:otherwise>
						Line: <xsl:value-of select="@last-line" />,
						column: <xsl:value-of select="@first-column" /> - <xsl:value-of select="@last-column" />
					</xsl:otherwise>					
				</xsl:choose>
			</p>
			<p class="extract">
				<code>
					<xsl:apply-templates select="nu:extract" />
				</code>
			</p>
            <p class="elaboration">
                <xsl:copy-of select="nu:elaboration" />
            </p>
		</li>
	</xsl:template>
	
	<xsl:template match="html:code">
		<code><xsl:value-of select="." /></code>
	</xsl:template>
	
	<xsl:template match="html:a">
		<a>
			<xsl:attribute name="href">
				<xsl:value-of select="@href" />
			</xsl:attribute>
			<xsl:attribute name="title">
				<xsl:value-of select="@title" />
			</xsl:attribute>
			<xsl:value-of select="." />
		</a>
	</xsl:template>

	<xsl:template match="nu:m">
		<span class="highlight"><xsl:value-of select="." /></span>
	</xsl:template>
	
	
</xsl:stylesheet>
