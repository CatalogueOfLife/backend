<div class="level${level}" id="${t.getId()}">
  <div class="Name">${t.taxon.name.rank}: ${t.taxon.labelHtml}
  <#if t.taxon.name.publishedInId??>
    <#assign ref = t.getReference(t.taxon.name.publishedInId) >
    <div class="Reference">${ref.citation}</div>
  </#if>
  <#--
    <div class="NameRelationship">Has type genus (Type genus): <em>Alucita</em> Linnaeus, 1758</div>
  -->
  </div>
<#if t.synonyms??>
  <div class="Synonyms">
   <#list t.synonyms as s>
    <div class="Synonym">
      <div class="Name">=&nbsp; ${s.labelHtml}
      </div>
    </div>
   </#list>
  </div>
</#if>
<#if t.taxon.referenceIds??>
  <div class="References">
   <#list t.taxon.referenceIds as rid>
    <#assign ref = t.getReference(rid) >
    <div class="Reference">${ref.citation}</div>
   </#list>
  </div>
</#if>
<#if t.vernacularNames??>
  <div class="Vernaculars">
   <#list t.vernacularNames as v>
    <span class="Vernacular">${v.name} [${v.language}]</span>
   </#list>
  </div>
</#if>
