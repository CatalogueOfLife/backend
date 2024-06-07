<#include "header.ftl">

Your taxonomic alignment request ${job.key} between

<#if job.taxon??>
 ${job.taxon.name} in </#if>dataset "${job.dataset.title}<#if job.dataset.version??>, version ${job.dataset.version}</#if>"
and<#if job.taxon2??>
 ${job.taxon2.name} in </#if>dataset "${job.dataset2.title}<#if job.dataset2.version??>, version ${job.dataset2.version}</#if>"

is ready:
${job.result.download} [${job.result.sizeWithUnit}]

<#include "footer.ftl">
