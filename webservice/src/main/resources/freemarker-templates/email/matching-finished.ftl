<#include "header.ftl">

Your bulk matching request ${job.key} against dataset "${job.dataset.title}<#if job.dataset.version??>, version ${job.dataset.version}</#if>" is ready:
${job.result.download} [${job.result.sizeWithUnit}]

<#include "footer.ftl">
