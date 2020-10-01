<!DOCTYPE html>
<html>
<head>
  <title>${d.title}</title>
  <meta charset="UTF-8">
  <meta name="description" content="${d.title!}">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <#--
  <link rel="stylesheet" href="https://data.catalogue.life/dataset/0/export/css">
  -->
  <link rel="stylesheet" href="file:///Users/markus/code/col/backend/webservice/src/main/resources/exporter/html/catalogue.css">
  <script src="https://kit.fontawesome.com/9dcb058c00.js" crossorigin="anonymous"></script>
</head>
<body>

<header>
  <div class="site-name">
    <a href="https://data.catalogue.life/dataset/${d.key?c}/classification"><img id="logo" src="https://api.catalogue.life/dataset/${d.key?c}/logo?size=MEDIUM">${d.title!"Export of dataset ${d.key}"}</a>
  </div>
</header>

<#if d.description??>
  <div class="Overview">
    <p>${d.description}</p>
  </div>
</#if>

<#list classification as t>
<div class="HigherTaxon" id="${t.getId()}">
  <div class="Name">${t.name.rank}: <strong>${t.label}</strong>
   <#--
    <div class="Reference">Page reference (Original description): Linnaeus, C. (1758), <a href="https://www.biodiversitylibrary.org/page/726898" target="_blank"><strong>9</strong> <i class="fas fa-external-link-alt fa-sm"></i></a></div>
    -->
  </div>
   <#--
  <div class="References">
    <div class="Reference"><strong>Linnaeus, C. (1758)</strong> Systema naturae per regna tria naturae: secundum classes, ordines, genera, species, cum characteribus, differentiis, synonymis, locis. 1-824. <a href="https://www.biodiversitylibrary.org/page/726886" target="_blank"><i class="fas fa-external-link-alt fa-sm"></i></a></div>
  </div>
    -->
</div>
</#list>