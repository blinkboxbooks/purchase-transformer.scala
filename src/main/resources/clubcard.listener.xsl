<?xml version="1.0"?>

<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                              xmlns:msg="http://schemas.blinkbox.com/books/purchasing/v1"
                              xmlns:r="http://schemas.blinkboxbooks.com/messaging/routing/v1"
                              xmlns:v="http://schemas.blinkboxbooks.com/messaging/versioning" >


<xsl:output indent="yes" />

<xsl:template match="/msg:purchase">
	<xsl:element name="ClubcardMessage" xmlns="http://schemas.blinkboxbooks.com/events/clubcard/v1" >

        <xsl:namespace name="r" select="'http://schemas.blinkboxbooks.com/messaging/routing/v1'" />
        <xsl:namespace name="v" select="'http://schemas.blinkboxbooks.com/messaging/versioning'" />
        <xsl:attribute name="r:originator">purchasing-service</xsl:attribute>
        <xsl:attribute name="v:version">1.0</xsl:attribute>

        <xsl:element name="userId">
			<xsl:value-of select="userId" />
		</xsl:element>
			
		<xsl:element name="clubcardNumber">
			<xsl:value-of select="clubcardNumber" />
		</xsl:element>
			
		<xsl:element name="points">
			<xsl:value-of select="clubcardPointsAward" />
		</xsl:element>
			
		<xsl:element name="transactions">
			<xsl:value-of select="basketItems/basketItem[1]/isbn" />
		</xsl:element>
			
		<xsl:element name="transactionDate">
			<xsl:value-of select="transactionDate" />
		</xsl:element>
			
		<xsl:element name="reason">
			<xsl:text>Purchased basket #</xsl:text>
			<xsl:value-of select="basketId" />
		</xsl:element>
	</xsl:element>
</xsl:template>

</xsl:stylesheet>
