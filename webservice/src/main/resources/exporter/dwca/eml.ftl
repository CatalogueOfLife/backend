<?xml version="1.0" encoding="utf-8"?>
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="eml://ecoinformatics.org/eml-2.1.1 http://rs.gbif.org/schema/eml-gbif-profile/1.1/eml.xsd"
        packageId="col-clb-${key}"  system="http://catalogueoflife.org" scope="system"
  xml:lang="en">

<#macro tag name value>
<#if value??>
  <${name}>${value}</${name}>
</#if>
</#macro>

<#macro person p>
<#if p.familyName??>
  <individualName>
    <@tag name="givenName" value=p.givenName! />
    <surName>${p.familyName!}</surName>
    <@tag name="electronicMailAddress" value=p.email! />
    <@tag name="userId" value=p.orcid! />
  </individualName>
</#if>
</#macro>

<#macro organisation o>
<#if o.name??>
  <organizationName>${o.name}</organizationName>
  <address>
    <@tag name="city" value=o.city! />
    <@tag name="administrativeArea" value=o.state! />
    <@tag name="country" value=o.country! />
  </address>
</#if>
</#macro>

<dataset>
    <@tag name="title" value=title />
    <creator/>
    <metadataProvider />
  <#if organisations??>
   <#list organisations as o>
   <associatedParty>
    <@organisation o />
   </associatedParty>
   </#list>
  </#if>
  <#if authors??>
   <#list authors as p>
   <associatedParty>
    <@person p />
    <role>AUTHOR</role>
   </associatedParty>
   </#list>
  </#if>
  <#if editors??>
   <#list editors as p>
   <associatedParty>
    <@person p />
    <role>EDITOR</role>
   </associatedParty>
   </#list>
  </#if>
    <@tag name="pubDate" value=released! />
    <language>ENGLISH</language>
  <#if description??>
    <abstract>
      <para>${description}</para>
    </abstract>
  </#if>
    <intellectualRights />
  <#if website??>
    <distribution scope="document">
      <online>
        <url function="information">${website}</url>
      </online>
    </distribution>
  </#if>

  <#if contact??>
    <contact>
      <@person contact />
    </contact>
  </#if>
</dataset>

<additionalMetadata>
    <metadata>
      <gbif>
        <@tag name="citation" value=citation! />
        <@tag name="resourceLogoUrl" value=logo! />
      </gbif>
    </metadata>
</additionalMetadata>

</eml:eml>