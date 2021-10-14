Hello ${user.firstname!user.lastname!user.username},

We are sorry, but an error has occurred processing your ${export.request.format.getName()} download <#if export.request.root??>for ${export.request.root.rank} ${export.request.root.name} </#if>from "${dataset.title}<#if dataset.version??>, version ${dataset.version}</#if>".

If the problem persists, contact us at ${from} or post to our mailinglist.
Please include the download key (${key}) of the failed download.

<#include "footer.ftl">
