Hello ${user.firstname!user.lastname!user.username},

Your ${export.request.format.getName()} download <#if export.request.root??>for ${export.request.root.rank} ${export.request.root.name} </#if>from "${dataset.title}" is ready:
${export.download} [${export.size}]

For help with opening und using downloaded files, please contact us at ${from} or post to our mailinglist.

<#include "footer.ftl">