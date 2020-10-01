<div class="level${level}" id="${t.getId()}">
  <div class="Name">${t.taxon.name.rank}: <strong>${t.taxon.label}</strong>
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
      <div class="Name">=&nbsp;${s.name.rank} (${s.status}): <strong>${s.label}</strong>
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
