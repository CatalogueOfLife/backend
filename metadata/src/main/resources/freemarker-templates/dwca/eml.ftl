<#ftl output_format="XML">
<?xml version="1.0" encoding="utf-8"?>
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="eml://ecoinformatics.org/eml-2.1.1 http://rs.gbif.org/schema/eml-gbif-profile/1.1/eml.xsd"
        packageId="col-clb-${(key!0)?c}"  system="http://catalogueoflife.org" scope="system"
  xml:lang="en">

<#macro tag name value indent>
<#if value?has_content>
<#list 0..<indent as i> </#list><${name}>${value}</${name}>
</#if>
</#macro>

<#macro agent a indent>
<#if a.isPerson()>
<#list 0..<indent as i> </#list><individualName>
    <@tag name="givenName" value=a.given! indent=indent+2 />
    <@tag name="surName" value=a.family! indent=indent+2 />
    <@tag name="userId" value=a.orcidAsUrl! indent=indent+2 />
<#list 0..<indent as i> </#list></individualName>
</#if>
<#if a.organization?has_content>
<#list 0..<indent as i> </#list><organizationName>${a.organization}</organizationName>
</#if>
<#if a.city?has_content || a.state?has_content || a.country?has_content>
<#list 0..<indent as i> </#list><address>
    <@tag name="city" value=a.city! indent=indent+2 />
    <@tag name="administrativeArea" value=a.state! indent=indent+2 />
    <@tag name="country" value=a.country! indent=indent+2 />
<#list 0..<indent as i> </#list></address>
</#if>
<@tag name="electronicMailAddress" value=a.email! indent=indent />
<@tag name="onlineUrl" value=a.url! indent=indent />
</#macro>

<#macro party role agents=[]>
  <#if agents?has_content>
   <#list agents as a>
  <associatedParty>
    <@agent a=a indent=4 />
    <role>${role}</role>
  </associatedParty>
   </#list>
  </#if>
</#macro>

<dataset>
  <@tag name="title" value=title indent=2 />
  <@tag name="shortName" value=alias! indent=2 />
  <#if creator?has_content>
  <creator>
    <@agent a=creator?first indent=4/>
  </creator>
  </#if>
  <#if creator?has_content>
    <@party role="author" agents=creator[1..] />
  </#if>
  <@party role="editor" agents=editor />
  <@party role="contributor" agents=contributor />
  <@tag name="pubDate" value=issued! indent=4 />
  <language>english</language>
  <#if description??>
  <abstract>
    <para>${description}</para>
  </abstract>
  </#if>
  <#if license?? && license.isCreativeCommons()>
  <intellectualRights>
    <para><ulink url="${license.url}"><citetitle>${license.title}</citetitle></ulink></para>
  </intellectualRights>
  </#if>
  <#if url??>
  <distribution scope="document">
    <online>
      <url function="information">${url}</url>
    </online>
  </distribution>
  </#if>
  <#if geographicScope?? || taxonomicScope?? >
  <coverage>
    <#if geographicScope?? >
    <geographicCoverage>
      <geographicDescription>${geographicScope}</geographicDescription>
    </geographicCoverage>
    </#if>
    <#if taxonomicScope?? >
    <taxonomicCoverage>
      <generalTaxonomicCoverage>${taxonomicScope}</generalTaxonomicCoverage>
    </taxonomicCoverage>
    </#if>
    </coverage>
  </#if>
  <#if contact??>
  <contact>
    <@agent a=contact indent=4 />
  </contact>
  </#if>
  <#if publisher??>
  <publisher>
    <@agent a=publisher indent=4 />
  </publisher>
  </#if>
</dataset>

<#if citation?has_content || logo?has_content || completeness?has_content || confidence?has_content>
<additionalMetadata>
  <metadata>
   <#if citation?has_content || logo?has_content || source?has_content>
    <gbif>
      <hierarchyLevel>dataset</hierarchyLevel>
      <@tag name="citation" value=citationText! indent=6 />
      <@tag name="resourceLogoUrl" value=logo! indent=6 />
      <#if source?has_content>
      <bibliography>
       <#list source as src>
        <citation<#if src.doi??> identifier="${src.doi.getUrl()}"</#if>>${src.citationText!}</citation>
       </#list>
      </bibliography>
      </#if>
    </gbif>
   </#if>
   <#if completeness?has_content || confidence?has_content || version?has_content>
    <col>
      <@tag name="version" value=version! indent=6 />
      <@tag name="completeness" value=completeness! indent=6 />
      <@tag name="confidence" value=confidence! indent=6 />
    </col>
   </#if>
  </metadata>
</additionalMetadata>
</#if>

</eml:eml>
