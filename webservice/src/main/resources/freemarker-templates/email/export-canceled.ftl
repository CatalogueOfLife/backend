<#include "header.ftl">

Your ${job.req.format.getName()} download <#if job.req.root??>for ${job.req.root.rank} ${job.req.root.name} </#if>from "${job.dataset.title}" was canceled.

<#include "footer.ftl">
