<#include "header.ftl">

We are sorry, but your ${job.getClass().getSimpleName()} #${job.attempt} of project ${job.dataset.title} has failed!

Start: ${job.started}
Finished: ${job.getFinished()}
Error: ${job.error}


<#include "footer.ftl">
