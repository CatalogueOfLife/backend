Thanks,
${fromName!"ChecklistBank"}


<#if job.dataset?has_content>
---
${job.dataset.title}<#if job.dataset.version??>, version ${job.dataset.version}</#if>:
https://www.checklistbank.org/dataset/${job.dataset.key?c}
</#if>
---

ChecklistBank: https://www.checklistbank.org
Mailinglist: https://lists.gbif.org/mailman/listinfo/col-users
Support : ${replyTo!}
