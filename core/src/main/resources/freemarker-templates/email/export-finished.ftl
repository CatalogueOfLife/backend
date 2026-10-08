<#include "header.ftl">

Your ${job.req.format.getName()} download <#if job.req.root??>for ${job.req.root.rank} ${job.req.root.name} </#if>from "${job.dataset.title}<#if job.dataset.version??>, version ${job.dataset.version}</#if>" is ready:
${job.export.download} [${job.export.sizeWithUnit}]

For help with opening and using downloaded files, please contact support or write to our mailinglist.

<#include "footer.ftl">
