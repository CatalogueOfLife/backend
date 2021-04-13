<div class="level${cssLevel}" id="${info.getId()}">
  <div class="Name">${info.taxon.name.rank}: ${info.taxon.labelHtml}
  <#if info.taxon.name.publishedInId??>
    <#assign ref = info.getReference(info.taxon.name.publishedInId) >
    <div class="Reference">${ref.citation}</div>
  </#if>
  <#--
    <div class="NameRelationship">Has type genus (Type genus): <em>Alucita</em> Linnaeus, 1758</div>
  -->
  </div>
<#if info.synonyms??>
  <div class="Synonyms">
   <#list info.synonyms as s>
    <div class="Synonym">
      <div class="Name">=&nbsp; ${s.labelHtml}
      </div>
    </div>
   </#list>
  </div>
</#if>
<#if info.taxon.referenceIds??>
  <div class="References">
   <#list info.taxon.referenceIds as rid>
    <#assign ref = info.getReference(rid) >
    <div class="Reference">${ref.citation}</div>
   </#list>
  </div>
</#if>
<#if info.vernacularNames??>
  <div class="Vernaculars">
   <#list info.vernacularNames as v>
    <span class="Vernacular">${v.name} [${v.language}]</span>
   </#list>
  </div>
</#if>
