<#ftl output_format="HTML">
<!DOCTYPE html>
<html>
<head>
  <title>${root.label}</title>
  <meta charset="UTF-8">
  <meta name="description" content="${root.label} in ${dataset.title}">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <link rel="stylesheet" href="${css}">
  <script src="https://kit.fontawesome.com/9dcb058c00.js" crossorigin="anonymous"></script>
</head>
<body>

<header>
  <div class="site-name">
    <a href="https://data.catalogue.life/dataset/${dataset.key?c}/classification"><img id="logo" src="https://api.catalogue.life/dataset/${dataset.key?c}/logo?size=MEDIUM">${dataset.title!"${root.label} in dataset ${dataset.key}"}</a>
  </div>
</header>

<div class="Overview">
  <p>Listing of all taxa in ${root.labelHtml}</p>
  <#if dataset.description??>
  <p>${dataset.description}</p>
  </#if>
</div>

<#list classification as ht>
<div class="HigherTaxon" id="${ht.getId()}">
  <div class="Name">${ht.rank}: <strong>${ht.name} ${ht.authorship!""}</strong>
   <#--
    <div class="Reference">Page reference (Original description): Linnaeus, C. (1758), <a href="https://www.biodiversitylibrary.org/page/726898" target="_blank"><strong>9</strong> <i class="fas fa-external-link-alt fa-sm"></i></a></div>
    -->
  </div>
</div>
</#list>