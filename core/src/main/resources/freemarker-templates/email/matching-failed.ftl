<#include "header.ftl">

We are sorry, but an error has occurred processing your bulk matching request ${job.key} against dataset "${job.dataset.title}<#if job.dataset.version??>, version ${job.dataset.version}</#if>".

<#include "failed.ftl">

<#include "footer.ftl">
