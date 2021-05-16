<#assign description>${info.taxon.label} in the Catalogue of Life<#if source??> based on ${source.citation!source.title!"unknown"}</#if></#assign>

<meta property="og:title" content="${info.taxon.getLabel()}" />
<meta property="og:url" content="https://www.catalogueoflife.org/data/taxon/${info.taxon.getId()}" />
<meta property="og:image" content="https://www.catalogueoflife.org/images/col_square_logo.jpg" />
<meta property="og:description" content="${description}" />
<meta name="twitter:card" content="summary"/>
<meta name="twitter:site" content="@catalogueoflife"/>
<meta name="twitter:title" content="${info.taxon.label}" />
<meta name="twitter:description" content="${description}" />
<meta name="twitter:image" content="https://www.catalogueoflife.org/images/col_square_logo.jpg" />

<!--
TaxonName DRAFT Profile:
https://bioschemas.org/profiles/TaxonName/0.1-DRAFT/
https://bioschemas.org/profiles/Taxon/0.6-RELEASE/
-->
<script type="application/ld+json">
{
  "@context": [
    "https://schema.org/",
    {
      "dwc": "http://rs.tdwg.org/dwc/terms/",
      "col": "http://catalogueoflife.org/terms/"
    }
  ],
  "@type": "Taxon",
  "additionalType": [
    "dwc:Taxon",
    "http://rs.tdwg.org/ontology/voc/TaxonConcept#TaxonConcept"
  ],
  "identifier": [
    {
      "@type": "PropertyValue",
      "name": "dwc:taxonID",
      "propertyID": "http://rs.tdwg.org/dwc/terms/taxonID",
      "value": "${info.taxon.getId()}"
    },
    {
      "@type": "PropertyValue",
      "name": "col:ID",
      "propertyID": "http://catalogueoflife.org/terms/ID",
      "value": "${info.taxon.getId()}"
    }
  ],
  "name": "${info.taxon.label}",
  "scientificName": {
    "@type": "TaxonName",
    "name": "${info.taxon.name.scientificName!}",
    "author": "${info.taxon.name.authorship!}",
    "taxonRank": "${info.taxon.name.rank!}"
   <#if info.getPublishedInReference()??>
    ,"isBasedOn": {
      "@type": "ScholarlyArticle",
      "name": "${info.getPublishedInReference().citation!}"
    }
   </#if>
  },
  <#if info.taxon.name.rank??>
  "taxonRank": [
    "http://api.catalogueoflife.org/vocab/rank/${info.taxon.name.rank}",
    "${info.taxon.name.rank}"
  ],
  </#if>

<#if info.synonyms?has_content>
  "alternateName": [
     <#list info.synonyms as s>
      "${s.label}"<#sep>,</#sep>
     </#list>
  ],
  "alternateScientificName": [
    <#list info.synonyms as s>
    {
      "@type": "TaxonName",
      "name": "${s.name.scientificName}",
      "author": "${s.name.authorship!}",
      "taxonRank": "${s.name.rank}"
      <#if s.name.publishedInId??>
       ,"isBasedOn": {
          "@type": "ScholarlyArticle",
          "name": "${info.getReference(s.name.publishedInId).citation!}"
        }
      </#if>
    }<#sep>,</#sep>
    </#list>
  ],
</#if>

<#if info.vernacularNames?has_content>
  "dwc:vernacularName": [
  <#list info.vernacularNames as v>
    {
      "@language": "${v.language!}",
      "@value": "${v.name!}"
    }<#sep>,</#sep>
  </#list>
  ],
</#if>

<#if parent??>
  "parentTaxon": {
    "@type": "Taxon",
    "name": "${parent.label!}",
    "scientificName": {
      "@type": "TaxonName",
      "name": "${parent.name!}",
      "author": "${parent.authorship!}",
      "taxonRank": "${parent.rank!}"
    },
    "identifier": [
      {
        "@type": "PropertyValue",
        "name": "dwc:taxonID",
        "propertyID": "http://rs.tdwg.org/dwc/terms/taxonID",
        "value": "${parent.id}"
      },
      {
        "@type": "PropertyValue",
        "name": "col:ID",
        "propertyID": "http://catalogueoflife.org/terms/ID",
        "value": "${parent.id}"
      }
    ],
    "taxonRank": [
      "http://rs.gbif.org/vocabulary/gbif/rank/${parent.rank}",
      "${parent.rank}"
    ]
  }
</#if>
}
</script>