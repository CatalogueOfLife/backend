<#include "header.ftl">

We are sorry, but your ${job.getClass().getSimpleName()} #${job.getAttempt()} of project ${job.dataset.title} has failed!

Start: ${job.started}
Finished: ${job.getFinished()}
Error: ${job.error}


<#include "footer.ftl">
