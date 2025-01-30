You requested the following datasets:

<#list job.datasets as d>
  ${d.key?c}: ${d.title}<#if d.version??>, version ${d.version}</#if>
</#list>
