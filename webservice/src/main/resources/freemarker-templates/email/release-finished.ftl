<#include "header.ftl">

Your ${job.getClass().getSimpleName()} #${job.attempt} of project ${job.dataset.title!} has completed.

${job.newDataset.alias!}: ${job.newDataset.url}
Start: ${job.started}
Finished: ${job.getFinished()}

Release logs are available at ${job.getReportURI()}

<#include "footer.ftl">
