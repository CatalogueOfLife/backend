<#if doi??>,
  <meta name="DC.identifier" scheme="DCTERMS.URI" content="${doi}" />
</#if>

<#--  Freemarker template with a Dataset object as the bean  -->

<#assign _title>${title!alias!}</#assign>
<#assign _description>${description!title!}</#assign>
<#--
  try out with GOOGLE TEST TOOL https://search.google.com/test/rich-results?utm_campaign=sdtt&utm_medium=url&url=https://www.dev.checklistbank.org/dataset/1010
-->
<meta name="title" content="${_title}" />
<meta name="description" content="${_description}" />
<meta property="og:title" content="${_title}" />
<meta property="og:url" content="https://www.checklistbank.org/dataset/${key?c}" />
<meta property="og:image" content="https://api.checklistbank.org/dataset/${key?c}/logo?size=LARGE" />
<meta property="og:description" content="${_description}" />
<meta name="twitter:card" content="summary"/>
<meta name="twitter:title" content="${_title}" />
<meta name="twitter:description" content="${_description}" />
<meta name="twitter:image" content="https://api.checklistbank.org/dataset/${key?c}/logo?size=LARGE" />

<#macro person p>
  {
    "familyName": "${p.familyName!}",
    "givenName": "${p.givenName!}"
    <#if p.email??>,
    "email": "${p.email}"
    </#if>
    <#if p.orcid??>,
    "identifier": "https://orcid.org/${p.orcid}"
    </#if>
  }
</#macro>

<#macro agent a>
  {
    <#if a.url??>,
    "url": "${a.url}",
    </#if>
    <#if a.email??>,
    "email": "${a.email}",
    </#if>
    <#if a.address??>,
    "address": "${a.address}",
    </#if>
    "name": "${a.name!}"
  }
</#macro>

<script type="application/ld+json">
{
  "@context": "https://schema.org/",
  "@type": "Dataset",
  "@id": "${key?c}",
  "url": "https://www.checklistbank.org/dataset/${key?c}",
  "name": "${_title}",
  <#if creator?has_content>
  "author": [
   <#list creator as p>
    <@person p=p /><#sep>,</#sep>
   </#list>
  ],
  </#if>
  <#if editor?has_content>
  "editor": [
   <#list editor as p>
    <@person p=p /><#sep>,</#sep>
   </#list>
  ],
  </#if>
  "description": "${_description!}",
  "temporalCoverage": "${temporalCoverage!}",
  "spatialCoverage": "${spatialCoverage!}",
  <#if license??>
  "license": "${license.url!'unknown'}",
  </#if>
  "inLanguage": "eng",
  "version": "${version!}",
  "datePublished": "${issued!}",
  <#if publisher??>
  "publisher": {
    "@type": "Organization",
    <@agent a=publisher />
  },
  </#if>
  "provider": {
    "@type": "Organization",
    "name": "Global Biodiversity Information Facility (GBIF)",
    "url": "https://www.gbif.org",
    "logo": "https://www.gbif.org/img/logo/GBIF50.png",
    "email": "info@gbif.org",
    "telephone": "+45 35 32 14 70"
  }
}
</script>
