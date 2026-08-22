<#include "header.ftl">

Your ChecklistBank search download ${job.key} from dataset "${job.dataset.title}<#if job.dataset.version??>, version ${job.dataset.version}</#if>" is ready:
${job.result.download} [${job.result.sizeWithUnit}]

The archive contains the matching name usages as a ColDP NameUsage file together with the dataset metadata and a README describing the exported search.

<#include "footer.ftl">
