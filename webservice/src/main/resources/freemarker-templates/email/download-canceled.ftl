Hello ${user.firstname!user.lastname!user.username},

Your ${export.request.format.getName()} download <#if export.request.root??>for ${export.request.root.rank} ${export.request.root.name} </#if>from "${dataset.title}<#if dataset.version??>, version ${dataset.version}</#if>" was canceled.

<#include "footer.ftl">
