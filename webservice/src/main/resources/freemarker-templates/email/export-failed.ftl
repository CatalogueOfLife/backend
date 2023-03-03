<#include "header.ftl">

We are sorry, but an error has occurred processing your ${job.req.format.getName()} download <#if job.req.root??>for ${job.req.root.rank} ${job.req.root.name} </#if>from "${job.dataset.title}<#if job.dataset.version??>, version ${job.dataset.version}</#if>".

<#include "failed.ftl">

<#include "footer.ftl">
