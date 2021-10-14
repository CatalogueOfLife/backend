Hello ${user.firstname!user.lastname!user.username},

Your ${export.request.format.getName()} download <#if export.request.root??>for ${export.request.root.rank} ${export.request.root.name} </#if>from "${dataset.title}<#if dataset.version??>, version ${dataset.version}</#if>" is ready:
${export.download} [${export.sizeWithUnit}]
<#if export.truncated?has_content>

The download exceeds the maximum amount of rows allowed in Excel spreadsheets for the following entities:
<#list export.truncated as x>
  ${x}
</#list>

These are truncated in this download which is otherwise complete. If you need access to all data please download an archive without the Excel option again.
</#if>

For help with opening und using downloaded files, please contact support or write to our mailinglist.

<#include "footer.ftl">