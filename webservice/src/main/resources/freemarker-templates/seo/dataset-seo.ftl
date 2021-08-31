<#ftl output_format="XHTML">
<#assign _title>${title!} | COL</#assign>
<#assign _description>${description!title!}</#assign>
<#--
  try out with GOOGLE TEST TOOL https://search.google.com/test/rich-results?utm_campaign=sdtt&utm_medium=url&url=https://www.catalogueoflife.org/data/dataset/1010
-->
<meta name="title" content="${_title}" />
<meta name="description" content="${_description}" />
<meta property="og:title" content="${_title}" />
<meta property="og:url" content="https://www.catalogueoflife.org/data/dataset/${key?c}" />
<meta property="og:image" content="https://api.catalogueoflife.org/dataset/3LR/source/${key?c}/logo?size=LARGE" />
<meta property="og:description" content="${_description}" />
<meta name="twitter:card" content="summary"/>
<meta name="twitter:site" content="@catalogueoflife"/>
<meta name="twitter:title" content="${_title}" />
<meta name="twitter:description" content="${_description}" />
<meta name="twitter:image" content="https://api.catalogueoflife.org/dataset/3LR/source/${key?c}/logo?size=LARGE" />

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

<script type="application/ld+json">
{
  "@context": "https://schema.org/",
  "@type": "Dataset",
  "@id": "${key?c}",
  "url": "https://www.catalogueoflife.org/data/dataset/${key?c}",
  "name": "${title!alias!}",
  <#if authors?has_content>
  "author": [
   <#list authors as p>
    <@person p=p /><#sep>,</#sep>
   </#list>
  ],
  </#if>
  <#if editors?has_content>
  "editor": [
   <#list editors as p>
    <@person p=p /><#sep>,</#sep>
   </#list>
  ],
  </#if>
  "description": "${description!}",
  "temporalCoverage": "${temporalCoverage!}",
  "spatialCoverage": "${spatialCoverage!}",
  <#if license??>
  "license": "${license.url!'unknown'}",
  </#if>
  "inLanguage": "eng",
  "version": "${version!}",
  "datePublished": "${issued!}",
  "publisher": {
    "@type": "Organization",
    "name": "Catalogue of Life (COL)",
    "url": "http://www.catalogueoflife.org/"
  },
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