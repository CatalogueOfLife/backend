Thanks,
${fromName!"ChecklistBank"}


<#if dataset??>
---
${dataset.title}<#if dataset.version??>, version ${dataset.version}</#if>:
https://www.checklistbank.org/dataset/${dataset.key?c}
</#if>
---
ChecklistBank: https://www.checklistbank.org
Mailinglist: https://lists.gbif.org/mailman/listinfo/col-users
Support : ${replyTo!}
