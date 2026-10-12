<#include "header.ftl">

Your ${job.getClass().getSimpleName()} #${job.getAttempt()} of project ${job.dataset.title!} has completed.

${job.newDataset.alias!}: ${job.newDataset.url}
Start: ${job.started}
Finished: ${job.getFinished()}

<#if job.failedSectors?? && job.failedSectors?has_content>
${job.failedSectors?size} merge sectors failed and are missing from the release:
<#list job.failedSectors as fs>
 - sector ${fs.sectorKey()?c} of dataset ${fs.subjectDatasetKey()?c}: ${fs.reason()}
</#list>

</#if>
Release logs are available at ${job.getReportURI()}

<#include "footer.ftl">
