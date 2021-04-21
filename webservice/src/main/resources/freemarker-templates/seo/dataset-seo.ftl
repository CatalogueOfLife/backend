<meta property="og:title" content="${title!}" />
<meta property="og:url" content="https://www.catalogueoflife.org/data/dataset/${key?c}" />
<meta property="og:image" content="https://api.catalogueoflife.org/dataset/3LR/source/${key?c}/logo?size=LARGE" />
<meta property="og:description" content="${description!title!}" />
<meta name="twitter:card" content="summary"/>
<meta name="twitter:site" content="@catalogueoflife"/>
<meta name="twitter:title" content="${title!}" />
<meta name="twitter:description" content="${description!title}" />
<meta name="twitter:image" content="https://api.catalogueoflife.org/dataset/3LR/source/${key?c}/logo?size=LARGE" />

<#macro person p>
    "familyName": "${p.familyName!}",
    "givenName": "${p.givenName!}",
    <#if p.email??>
    "email": "${p.email}",
    </#if>
    <#if p.orcid??>
    "identifier": "https://orcid.org/${p.orcid}"
    </#if>
</#macro>

<script type="application/ld+json">
{
  "@context": "https://schema.org/",
  "@type": "Dataset",
  "@id": "${key?c}",
  "url": "https://www.catalogueoflife.org/data/dataset/${key?c}",
  "name": "${title!alias!}",
  <#if authors??>
  "author": [
   <#list authors as p>
    <@person p=p />
   </#list>
  ],
  </#if>
  <#if editors??>
  "editor": [
   <#list editors as p>
    <@person p=p />
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
  "datePublished": "${released!}",
  "publisher": {
    "@type": "Organization",
    "name": "The Catalogue of Life Partnership",
    "url": "http://www.catalogueoflife.org/"
  },
  "provider": {
    "@type": "Organization",
    "name": "GBIF",
    "url": "https://www.gbif.org",
    "logo": "https://www.gbif.org/img/logo/GBIF50.png",
    "email": "info@gbif.org",
    "telephone": "+45 35 32 14 70"
  }
}
</script>