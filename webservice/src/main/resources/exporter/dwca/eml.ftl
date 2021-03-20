<#ftl output_format="XML">
<?xml version="1.0" encoding="utf-8"?>
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="eml://ecoinformatics.org/eml-2.1.1 http://rs.gbif.org/schema/eml-gbif-profile/1.1/eml.xsd"
        packageId="col-clb-${key?c}"  system="http://catalogueoflife.org" scope="system"
  xml:lang="en">

<#macro tag name value indent>
<#if value?has_content>
<#list 0..<indent as i> </#list><${name}>${value}</${name}>
</#if>
</#macro>

<#macro person p indent>
<#if p.familyName??>
<#list 0..<indent as i> </#list><individualName>
    <@tag name="givenName" value=p.givenName! indent=indent+2 />
    <@tag name="surName" value=p.familyName! indent=indent+2 />
    <@tag name="electronicMailAddress" value=p.email! indent=indent+2 />
    <@tag name="userId" value=p.orcid! indent=indent+2 />
<#list 0..<indent as i> </#list></individualName>
</#if>
</#macro>

<#macro organisation o indent>
<#if o.name??>
<#list 0..<indent as i> </#list><organizationName>${o.name}</organizationName>
<#if o.city?has_content || o.state?has_content || o.country?has_content>
<#list 0..<indent as i> </#list><address>
    <@tag name="city" value=o.city! indent=indent+2 />
    <@tag name="administrativeArea" value=o.state! indent=indent+2 />
    <@tag name="country" value=o.country! indent=indent+2 />
<#list 0..<indent as i> </#list></address>
</#if>
</#if>
</#macro>

<dataset>
  <@tag name="title" value=title indent=2 />
  <#if authors?has_content>
  <creator>
    <@person p=authors?first indent=4/>
  </creator>
  </#if>
  <#if organisations??>
   <#list organisations as o>
  <associatedParty>
    <@organisation o=o indent=4 />
  </associatedParty>
   </#list>
  </#if>
  <#if authors?has_content>
   <#list authors[1..] as p>
  <associatedParty>
    <@person p=p indent=4 />
    <role>author</role>
  </associatedParty>
   </#list>
  </#if>
  <#if editors??>
   <#list editors as p>
  <associatedParty>
    <@person p=p indent=4 />
    <role>editor</role>
  </associatedParty>
   </#list>
  </#if>
  <@tag name="pubDate" value=released! indent=4 />
  <language>english</language>
  <#if description??>
  <abstract>
    <para>${description}</para>
  </abstract>
  </#if>
  <#if license?? && license.isConcrete()>
  <intellectualRights>
    <para><ulink url="${license.url}"><citetitle>${license.title}</citetitle></ulink></para>
  </intellectualRights>
  </#if>
  <#if website??>
  <distribution scope="document">
    <online>
      <url function="information">${website}</url>
    </online>
  </distribution>
  </#if>
  <#if geographicScope??>
  <coverage>
    <geographicCoverage>
      <geographicDescription>${geographicScope}</geographicDescription>
    </geographicCoverage>
  </coverage>
  </#if>
  <#if contact??>
  <contact>
    <@person p=contact indent=4 />
  </contact>
  </#if>
</dataset>

<#if citation?has_content || logo?has_content || completeness?has_content || confidence?has_content>
<additionalMetadata>
  <metadata>
   <#if citation?has_content || logo?has_content>
    <gbif>
      <@tag name="citation" value=citation! indent=6 />
      <@tag name="resourceLogoUrl" value=logo! indent=6 />
    </gbif>
   </#if>
   <#if completeness?has_content || confidence?has_content>
    <col>
      <@tag name="completeness" value=completeness! indent=6 />
      <@tag name="confidence" value=confidence! indent=6 />
    </col>
   </#if>
  </metadata>
</additionalMetadata>
</#if>

</eml:eml>
