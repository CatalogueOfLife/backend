current_edition = "monthly"

[monthly]
edition = "${d.released}"
<#if d.citation??>
citation = "${d.citation}"
<#else>
citation = "<#list d.editors![] as author>${author}<#sep>, </#list> (${d.released?string['yyyy']}). ${d.title!}, ${d.released}. Digital resource at www.catalogueoflife.org/col. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858."
</#if>

[annual]
edition = 2019
citation = "Roskov Y., Ower G., Orrell T., Nicolson D., Bailly N., Kirk P.M., Bourgoin T., DeWalt R.E., Decock W., Nieukerken E. van, Zarucchi J., Penev L., eds. (2019). Species 2000 & ITIS Catalogue of Life, 2019 Annual Checklist. Digital resource at www.catalogueoflife.org/annual-checklist/2019. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-884X."
