<#include "header.ftl">

Your ${job.req.format.getName()} download <#if job.req.root??>for ${job.req.root.rank} ${job.req.root.name} </#if>from "${job.dataset.title}<#if job.dataset.version??>, version ${job.dataset.version}</#if>" is ready:
${job.export.download} [${job.export.sizeWithUnit}]

<#if job.truncated?has_content>
The download exceeds the maximum amount of rows allowed in Excel spreadsheets for the following entities:
<#list job.truncated as x>
  ${x}
</#list>

These are truncated in this download which is otherwise complete. If you need access to all data please download an archive without the Excel option again.

</#if>
For help with opening and using downloaded files, please contact support or write to our mailinglist.

<#include "footer.ftl">
