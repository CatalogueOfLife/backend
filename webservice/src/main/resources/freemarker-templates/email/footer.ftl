Thanks,
${fromName!"ChecklistBank"}


<#if job.dataset?has_content>
---
${job.dataset.title}<#if job.dataset.version??>, version ${job.dataset.version}</#if>:
https://www.${domain!"checklistbank.org"}/dataset/${job.dataset.key?c}
</#if>
<#if job.dataset2?has_content>
---
${job.dataset2.title}<#if job.dataset2.version??>, version ${job.dataset2.version}</#if>:
https://www.${domain!"checklistbank.org"}/dataset/${job.dataset2.key?c}
</#if>
---

ChecklistBank: https://www.${domain!"checklistbank.org"}
Mailinglist: https://lists.gbif.org/mailman/listinfo/col-users
Support : ${replyTo!}
