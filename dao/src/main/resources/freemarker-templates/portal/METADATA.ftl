<!DOCTYPE html>
<html>

  <head>
  <meta charset="utf-8">
  <meta http-equiv="X-UA-Compatible" content="IE=edge">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="DC.identifier" scheme="DCTERMS.URI" content="urn:ISSN:2405-8858" />

    <!-- Begin SEO -->

<#--
Freemarker template with the following variables:
 releaseKey - the dataset key of the release
 dataset - dataset object for the COL checklist
-->

<#assign _title>${dataset.title!} | COL</#assign>
<#assign _description>${dataset.description!dataset.title!}</#assign>
<#--
  try out with GOOGLE TEST TOOL https://search.google.com/test/rich-results?utm_campaign=sdtt&utm_medium=url&url=https://www.catalogueoflife.org/data/dataset/1010
-->
<meta name="title" content="${_title}" />
<meta name="description" content="${_description}" />
<meta property="og:title" content="${_title}" />
<meta property="og:url" content="https://www.catalogueoflife.org/data/dataset/metadata" />
<meta property="og:image" content="https://api.checklistbank.org/dataset/${dataset.key?c}/logo?size=LARGE" />
<meta property="og:description" content="${_description}" />
<meta name="twitter:card" content="summary"/>
<meta name="twitter:site" content="@catalogueoflife"/>
<meta name="twitter:title" content="${_title}" />
<meta name="twitter:description" content="${_description}" />
<meta name="twitter:image" content="https://api.checklistbank.org/dataset/${dataset.key?c}/logo?size=LARGE" />

<#macro person p>
  {
    "familyName": "${p.familyName!}",
    "givenName": "${p.givenName!}"
    <#if p.email??>,
    "email": "${p.email}"
    </#if>
    <#if p.orcid??>,
    "identifier": "https://orcid.org/${p.orcid}"
    </#if>
  }
</#macro>

<script type="application/ld+json">
{
  "@context": "https://schema.org/",
  "@type": "Dataset",
  "@id": "${dataset.key?c}",
  "url": "https://www.catalogueoflife.org/data/metadata",
  "name": "${dataset.title!source.alias!}",
  <#if authors?has_content>
  "author": [
   <#list authors as p>
    <@person p=p /><#sep>,</#sep>
   </#list>
  ],
  </#if>
  <#if editors?has_content>
  "editor": [
   <#list editors as p>
    <@person p=p /><#sep>,</#sep>
   </#list>
  ],
  </#if>
  "description": "${dataset.description!}",
  "temporalCoverage": "${dataset.temporalCoverage!}",
  "spatialCoverage": "${dataset.spatialCoverage!}",
  <#if license??>
  "license": "${dataset.license.url!'unknown'}",
  </#if>
  "inLanguage": "eng",
  "version": "${dataset.version!}",
  "datePublished": "${dataset.issued!}",
  "publisher": {
    "@type": "Organization",
    "name": "Catalogue of Life (COL)",
    "url": "https://www.catalogueoflife.org"
  },
  "provider": {
    "@type": "Organization",
    "name": "Global Biodiversity Information Facility (GBIF)",
    "url": "https://www.gbif.org",
    "logo": "https://www.gbif.org/img/logo/GBIF50.png",
    "email": "info@gbif.org",
    "telephone": "+45 35 32 14 70"
  }
}
</script>
    <!-- End SEO -->




  <link rel="stylesheet" href="/css/foundation.css">
  <link rel="stylesheet" href="/css/style.css">
  <link rel="stylesheet" href="/css/fontello.css">
  <!--
  <link rel="stylesheet" href="/css/font-awesome.css">
  -->
  <link rel="stylesheet" href="/css/custom.css">

  <link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/CatalogueOfLife/portal-components@v1.2.8/umd/main.css">
  <!-- Global site tag (gtag.js) - Google Analytics -->
  <script src="/javascripts/libs.js" type="text/javascript"></script>
  <script>
    // terrificjs bootstrap
    (function($) {
        $(document).ready(function() {
            var $page = $('body');
            var config = {
              dependencyPath: {
                plugin: 'javascripts/'
              }
            }
            var application = new Tc.Application($page, config);
            application.registerModules();
            application.start();
        });
    })(Tc.$);
  </script>
  <link href="https://fonts.googleapis.com/css?family=Raleway:400,700,300" media="screen" rel="stylesheet" type="text/css" />
  <script src="/javascripts/masonry.pkgd.js" type="text/javascript"></script>
  <script src="/javascripts/imagesloaded.pkgd.min.js" type="text/javascript"></script>
  <script src="/javascripts/slick.min.js" type="text/javascript"></script>
  <script src="/javascripts/json2.js" type="text/javascript"></script>
  <link rel="alternate" type="application/rss+xml" title="COL" href="https://www.catalogueoflife.org/feed.xml" />
  <script src="https://unpkg.com/react@16/umd/react.production.min.js" ></script>
  <script src="https://unpkg.com/react-dom@16/umd/react-dom.production.min.js" ></script>
  <script src="https://cdn.jsdelivr.net/gh/CatalogueOfLife/portal-components@v1.2.8/umd/col-browser.min.js" ></script>
  <script src="https://kit.fontawesome.com/9660302c12.js" crossorigin="anonymous"></script>
</head>


  <body>

    <div class='contain-to-grid sticky'>
  <nav class='top-bar' data-options='sticky_on: large' data-topbar=''>
    <ul class='title-area'>
      <li class='name'>
        <h1>
          <a href='/'>
            <img alt="" class="col-header-logo" src="/images/col_logo.svg"/>
          </a>
        </h1>
      </li>
      <li class='toggle-topbar menu-icon'>
        <a href='#'>Menu</a>
      </li>
    </ul>
    <section class='top-bar-section'>
      <ul class="right">




            <li class="">
              <a href="/">Home</a>
            </li>





            <li class="has-dropdown active">
              <a href="/data/search">Data</a>
              <ul class='dropdown'>

                  <li>
                    <a href="/data/search">Search</a>
                  </li>

                  <li>
                    <a href="/data/browse">Browse</a>
                  </li>

                  <li>
                    <a href="/data/metadata">Metadata</a>
                  </li>

                  <li>
                    <a href="/data/source-datasets">Source datasets</a>
                  </li>

                  <li>
                    <a href="/data/download">Download</a>
                  </li>

              </ul>
            </li>





            <li class="has-dropdown ">
              <a href="/about/catalogueoflife">About</a>
              <ul class='dropdown'>

                  <li>
                    <a href="/about/catalogueoflife">The Catalogue of Life</a>
                  </li>

                  <li>
                    <a href="/about/colpipeline">The COL data pipeline</a>
                  </li>

                  <li>
                    <a href="/about/colcommunity">The COL community</a>
                  </li>

                  <li>
                    <a href="/about/contributors">The COL contributors</a>
                  </li>

                  <li>
                    <a href="/about/colusage">Using the COL Checklist</a>
                  </li>

                  <li>
                    <a href="/about/governance">Governance</a>
                  </li>

                  <li>
                    <a href="/about/funding">Funding</a>
                  </li>

                  <li>
                    <a href="/about/glossary.html">Glossary</a>
                  </li>

              </ul>
            </li>





            <li class="">
              <a href="/news/index">News</a>
            </li>





            <li class="">
              <a href="/contact">Contact</a>
            </li>


      </ul>
    </section>
  </nav>
</div>

    <div id='main' role='main'>

<div id="health-warning" style="display: none"><div style="display: inline-block; vertical-align: middle; margin-right: 18px;"><div class="circle pulse tomato"></div></div><span >The system is under maintenance - you may encounter unexpected behaviour</span></div>
<script>
var interval = 30000
var show = function (elem) {
	elem.style.display = 'block';
};

var hide = function (elem) {
	elem.style.display = 'none';
};
function getHealth(){
var xhr = new XMLHttpRequest();
xhr.onload = function () {
    var healthWarining = document.getElementById("health-warning")
	if (xhr.status >= 200 && xhr.status < 300) {
		var background = JSON.parse(xhr.responseText);
        if(background.maintenance){
            show(healthWarining);
        } else {
            hide(healthWarining)
        }
	} else {
        show(healthWarining)
		//console.log(JSON.parse(xhr.responseText));
	}

};
xhr.open('GET', 'https://download.checklistbank.org/.status.json');
xhr.send();
}
getHealth()
setInterval(getHealth, interval);

</script>

      <div class='full parallax' style='position: relative; background-image: url(/images/species/Asplenium_trichomanes.jpg); color: #fff;'>
  <div class='row'>
    <div class='large-12 columns'>
      <div class="mod modSectionHeader big">
  <div class="special-title centered-text">
    <h2 style="color: #fff;">Metadata</h2>
  </div>

    <h3 class='centered-text' style='color: #fff'>About the current COL Checklist</h3>

</div>
    </div>
  </div>
  <div class='four spacing'></div>
  <div class="caption caption-absolute">

      <p><em>Asplenium trichomanes</em> L. - <a href="https://www.inaturalist.org/observations/15132827">Photo CC By Markus Döring</a></p>

    </div>
</div>


<div class='full'>
  <div class='row'>

    <div class='large-12 columns prose'>

      <h2 id="version">Version</h2>

<div id="version">
  Issued: <i>2022-07-12</i>
  <br />
  DOI: <a href="https://doi.org/10.48580/dfpz">10.48580/dfpz</a>
  <br />
  ChecklistBank: <a href="https://www.checklistbank.org/dataset/9830/about">9830</a>
  <br />
  <br />
</div>

<h2 id="abstract">Abstract</h2>

<p>The Catalogue of Life is an assembly of expert-based global species checklists with the aim to build a comprehensive catalogue of all known species of organisms on Earth. Continuous progress is made towards completion, but for now, it probably includes just over 80% of the world’s known species. The Catalogue of Life estimates 2.3M extant species on the planet recognised by taxonomists at present time. This means that for many groups it continues to be deficient, and users may notice that many species are still missing from the Catalogue.</p>

<h3 id="whats-new-in-july-2022-edition">What’s new in July 2022 edition?</h3>

<h4 id="72-checklists-have-been-updated">72 checklists have been updated:</h4>

<ul>
  <li>Alucitoidea</li>
  <li>Gelechiidae</li>
  <li>Global Lepidoptera Index</li>
  <li>ITIS (new checklists: family Blattisociidae; classes Pauropoda and Symphyla as a replacement for WoRMS data)</li>
  <li>Pterophoroidea</li>
  <li>Scarabs</li>
  <li>World Plants</li>
  <li>WoRMS, 65 checklists</li>
</ul>

<h2 id="recommended-citation">Recommended citation</h2>

<div id="bibtex" style="float: right;">
<a href="https://api.checklistbank.org/dataset/9830.bib"><img src="/images/bibtex_logo.png" style="height: 32px;" /></a>
</div>

<div class="csl-entry">B&aacute;nki, O., Roskov, Y., D&ouml;ring, M., Ower, G., Vandepitte, L., Hobern, D., Remsen, D., Schalk, P., DeWalt, R. E., Keping, M., Miller, J., Orrell, T., Aalbu, R., Adlard, R., Adriaenssens, E. M., Aedo, C., Aescht, E., Akkari, N., Alfenas-Zerbini, P., et al. (2022). <span style="font-style: italic">Catalogue of Life Checklist</span> (Version 2022-07-12). Catalogue of Life. https://doi.org/10.48580/dfpz</div>
<p><br />
Please also read the <a href="/about/colusage#recommended-citations">recommended citations for individual datasets contributing to the COL Checklist.</a></p>

<h3 id="authors">Authors</h3>

<div id="authors">
  <ul>

    <li>Bánki, Olaf <a href="https://orcid.org/0000-0001-6197-9951"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Naturalis Biodiversity Center, Leiden, Netherlands</i> - <i>COL Executive Secretary</i></li>

    <li>Roskov, Yury <a href="https://orcid.org/0000-0003-2137-2690"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Illinois Natural History Survey, Champaign, IL, United States of America</i> - <i>COL Executive Editor; ILDIS</i></li>

    <li>Döring, Markus <a href="https://orcid.org/0000-0001-7757-1889"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>GBIF, Berlin, Germany</i> - <i>COL Lead Developer</i></li>

    <li>Ower, Geoff <a href="https://orcid.org/0000-0002-9770-2345"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Illinois Natural History Survey, Champaign, IL, United States of America</i> - <i>COL Data Manager</i></li>

    <li>Vandepitte, Leen <a href="https://orcid.org/0000-0002-8160-7941"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Vlaams Instituut voor de Zee (VLIZ), Oostende, Belgium</i> - <i>Global Team Chair</i></li>

    <li>Hobern, Donald <a href="https://orcid.org/0000-0001-6492-4016"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Species 2000 Australia, Canberra, Australia</i> - <i>Taxonomy Group Chair; Pterophoroidea; Pterophoroidea; Alucitoidea; Alucitoidea; Gelechiidae; Gelechiidae; Global Lepidoptera Index</i></li>

    <li>Remsen, David <a href="https://orcid.org/0000-0003-1289-0840"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Marine Biological Laboratory, Woods Hole, United States of America</i> - <i>Information Systems Group Chair</i></li>

    <li>Schalk, Peter <a href="https://orcid.org/0000-0003-4536-7650"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Naturalis Biodiversity Center, Leiden, Netherlands</i> - <i>Board of Directors Chair</i></li>

    <li>DeWalt, R. Edward <a href="https://orcid.org/0000-0001-9985-9250"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Illinois Natural History Survey, Champaign, IL, United States of America</i> - <i>Board of Directors; SF Plecoptera</i></li>

    <li>Keping, Ma <a href="https://orcid.org/0000-0001-9112-5340"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Chinese Academy of Sciences, Beijing, China</i> - <i>Board of Directors</i></li>

    <li>Miller, Joe <a href="https://orcid.org/0000-0002-5788-9010"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>GBIF, Copenhagen, Denmark</i> - <i>COL+ Partnership</i></li>

    <li>Orrell, Thomas <a href="https://orcid.org/0000-0003-1038-3028"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Smithsonian Institution, Washington, D.C., United States of America</i> - <i>COL Partnership</i></li>

    <li>Aalbu, Rolf. <i>California Academy of Sciences, San Francisco, United States of America</i> - <i>Sepidiini tribe</i></li>

    <li>Adlard, Robert - <i>WoRMS Myxozoa</i></li>

    <li>Adriaenssens, Evelien M. <a href="https://orcid.org/0000-0003-4826-5406"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Aedo, Carlos. <i>Real Jardín Botánico de Madrid, CSIC, Madrid, Spain</i> - <i>RJB Geranium</i></li>

    <li>Aescht, E. - <i>CilCat</i></li>

    <li>Akkari, Nesrine - <i>WoRMS Myriapoda</i></li>

    <li>Alfenas-Zerbini, Poliane <a href="https://orcid.org/0000-0002-8203-5244"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Alonso-Zarazaga, Miguel A. <a href="https://orcid.org/0000-0002-6991-0980"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Museo Nacional de Ciencias Naturales, Madrid, Spain</i> - <i>WTaxa</i></li>

    <li>Altenburger, Katrin - <i>WoRMS Echinoidea</i></li>

    <li>Alvarez, Belinda - <i>WoRMS Porifera</i></li>

    <li>Alvarez, Fernando - <i>WoRMS Brachiopoda</i></li>

    <li>Anderson, Gary - <i>WoRMS Tanaidacea</i></li>

    <li>Antić, Dragan Z. - <i>WoRMS Myriapoda</i></li>

    <li>Antonietto, Lucas Silveira - <i>WoRMS Ostracoda</i></li>

    <li>Arango, Claudia - <i>WoRMS Pycnogonida</i></li>

    <li>Artois, Tom - <i>WoRMS Turbellarians</i></li>

    <li>Arvanitidis, Christos - <i>WoRMS Polychaeta</i></li>

    <li>Atkinson, Stephen - <i>WoRMS Myxozoa</i></li>

    <li>Auffenberg, Kurt - <i>WoRMS Mollusca</i></li>

    <li>Bailly, Nicolas <a href="https://orcid.org/0000-0003-4994-0653"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Beaty Biodiversity Museum, University of British Columbia, Vancouver, Canada</i> - <i>Global Team, Taxonomy Group</i></li>

    <li>Baker, Edward <a href="https://orcid.org/0000-0002-5887-9543"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>The Natural History Museum, London, United Kingdom</i> - <i>SF Phasmida; SF Chrysididae</i></li>

    <li>Bamber, Roger - <i>WoRMS Pycnogonida</i></li>

    <li>Bandesha, Farida - <i>WoRMS Echinoidea</i></li>

    <li>Bank, Ruud - <i>WoRMS Mollusca</i></li>

    <li>Barber, Anthony - <i>WoRMS Myriapoda</i></li>

    <li>Barber-James, H. - <i>FADA Ephemeroptera</i></li>

    <li>Barbosa, Joao Paulo - <i>WoRMS Myriapoda</i></li>

    <li>Bartolozzi, L. - <i>Brentids</i></li>

    <li>Bartsch, I. - <i>FADA Halacaridae</i></li>

    <li>Beccaloni, George W <a href="https://orcid.org/0000-0003-0323-8535"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Wallace Correspondence Project, London, United Kingdom</i> - <i>SF Cockroach; Global Lepidoptera Index</i></li>

    <li>Bellamy, C.L. - <i>Jewel Beetles</i></li>

    <li>Bellan-Santini, Denise - <i>WoRMS Amphipoda</i></li>

    <li>Bellinger, P.F. - <i>Collembola.org</i></li>

    <li>Ben-Dov, Yair. <i>Agricultural Research Organization, Bet Dagan, Israel</i> - <i>ScaleNet</i></li>

    <li>Bernot, James <a href="https://orcid.org/0000-0002-1769-8631"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Copepoda</i></li>

    <li>Bezerra, Tania Nara - <i>WoRMS Nematoda</i></li>

    <li>Bieler, Rüdiger - <i>WoRMS Mollusca</i></li>

    <li>Bisby, F. - <i>ILDIS</i></li>

    <li>Bitner, Maria Aleksandra - <i>WoRMS Brachiopoda</i></li>

    <li>Blasco-Costa, Isabel - <i>WoRMS Trematoda</i></li>

    <li>Bock, Phil - <i>WoRMS Bryozoa</i></li>

    <li>Bouchard, Patrice. <i>Agriculture and Agri-Food Canada, Ottawa, Canada</i> - <i>Sepidiini tribe</i></li>

    <li>Bouchet, Philippe - <i>WoRMS Mollusca</i></li>

    <li>Bourgoin, T. <a href="https://orcid.org/0000-0001-9277-2478"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Museum National d'Histoire Naturelle, Paris, France</i> - <i>FLOW; Board of Directors</i></li>

    <li>Boury-Esnault, Nicole - <i>WoRMS Porifera</i></li>

    <li>Bouzan, Rodrigo - <i>WoRMS Myriapoda</i></li>

    <li>Boxshall, Geoff <a href="https://orcid.org/0000-0001-8170-7734"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Bochusacea; WoRMS Brachypoda; WoRMS Mystacocarida; WoRMS Tantulocarida; WoRMS Merostomata; WoRMS Copepoda</i></li>

    <li>Boyko, Christopher - <i>WoRMS Isopoda</i></li>

    <li>Brandão, Simone <a href="https://orcid.org/0000-0002-3487-6129"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Ostracoda</i></li>

    <li>Braun, Holger <a href="https://orcid.org/0000-0002-1069-8794"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>División Entomología, Museo de La Plata, CEPAVE - CONICET, La Plata, Argentina</i> - <i>SF Orthoptera</i></li>

    <li>Bray, Rod - <i>WoRMS Cestoda; WoRMS Trematoda</i></li>

    <li>Brock, Paul D. <i>The Natural History Museum, London, United Kingdom</i> - <i>SF Phasmida; SF Phasmida</i></li>

    <li>Bronstein, Omri <a href="https://orcid.org/0000-0003-2620-3976"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Echinoidea</i></li>

    <li>Bruce, Niel - <i>WoRMS Isopoda</i></li>

    <li>Bueno-Villegas, Julian - <i>WoRMS Myriapoda</i></li>

    <li>Burckhardt, Daniel <a href="https://orcid.org/0000-0001-8368-5268"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Naturhistorisches Museum, Basel, Switzerland</i> - <i>MBB</i></li>

    <li>Bush, Louise - <i>WoRMS Turbellarians</i></li>

    <li>Böttger-Schnack, Ruth - <i>WoRMS Copepoda</i></li>

    <li>Büscher, Thies. <i>Christian-Albrechts-Universität, Kiel, Germany</i> - <i>SF Phasmida</i></li>

    <li>Błażewicz-Paszkowycz, Magdalena - <i>WoRMS Tanaidacea</i></li>

    <li>Cairns, Stephen - <i>WoRMS Scleractinia</i></li>

    <li>Calonje, Michael <a href="https://orcid.org/0000-0001-9650-3136"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Montgomery Botanical Center, Miami, United States of America</i> - <i>The World List of Cycads</i></li>

    <li>Carballo, José Luis - <i>WoRMS Porifera</i></li>

    <li>Cardoso, Lilian <a href="https://orcid.org/0000-0002-9289-2733"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Departamento de Zoologia, Universidade Federal do Rio de Janeiro, Rio de Janeiro, Brazil</i> - <i>WCO</i></li>

    <li>Carrera-Parra, Luis - <i>WoRMS Polychaeta</i></li>

    <li>Castilho, R.C. - <i>Mites GSD Ologamasidae; Mites GSD Rhodacaridae; Mites GSD Phytoseiidae; Mites GSD Tenuipalpidae</i></li>

    <li>Catalano, Sarah - <i>WoRMS Rhombozoa</i></li>

    <li>Chatrou, L.W. - <i>AnnonBase</i></li>

    <li>Chevillotte, Herve. <i>Institut de Recherche pour le Développement (IRD), Paris, France</i> - <i>TITAN</i></li>

    <li>Christiansen, K.A. - <i>Collembola.org</i></li>

    <li>Cianferoni, F. - <i>Brentids</i></li>

    <li>Cigliano, María Marta <a href="https://orcid.org/0000-0002-8840-2121"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>División Entomología, Museo de La Plata, CEPAVE - CONICET, La Plata, Argentina</i> - <i>SF Orthoptera; Global Team, Taxonomy Group</i></li>

    <li>Coleman, Charles Oliver <a href="https://orcid.org/0000-0002-3051-7575"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Amphipoda</i></li>

    <li>Collins, Allen - <i>WoRMS Cubozoa; WoRMS Staurozoa; WoRMS Scyphozoa</i></li>

    <li>Corbari, Laure <a href="https://orcid.org/0000-0002-3323-6162"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Amphipoda</i></li>

    <li>Cordeiro, Ralf - <i>WoRMS Octocorallia</i></li>

    <li>CoreoideaSF Team <i>The Natural History Museum, London, United Kingdom</i> - <i>SF Coreoidea</i></li>

    <li>Cornils, Astrid - <i>WoRMS Copepoda</i></li>

    <li>Costello, Mark <a href="https://orcid.org/0000-0003-2362-0328"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>University of Auckland, Auckland, New Zealand</i> - <i>WoRMS Amphipoda; Taxonomy Group</i></li>

    <li>Crosby, Marshall R. <i>Missouri Botanical Garden, St. Louis, United States of America</i> - <i>MOST</i></li>

    <li>Cruz-López, Jesus A. <a href="https://orcid.org/0000-0002-6128-0195"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Instituto Nacional de Investigaciones Forestales, Agrícolas y Pecuarias (INIFAP), Villa de Etla, Oaxaca, Mexico</i> - <i>WCO</i></li>

    <li>Culham, A. - <i>Droseraceae Database</i></li>

    <li>Cárdenas, Paco - <i>WoRMS Porifera</i></li>

    <li>Daly, Meg - <i>WoRMS Actiniaria; WoRMS Corallimorpharia</i></li>

    <li>Daneliya, Mikhail - <i>WoRMS Amphipoda</i></li>

    <li>Dauvin, Jean-Claude <a href="https://orcid.org/0000-0001-8361-5382"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Amphipoda</i></li>

    <li>Davie, Peter - <i>WoRMS Brachyura</i></li>

    <li>Davison, Andrew J. <a href="https://orcid.org/0000-0002-4991-9128"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>De Broyer, Claude - <i>WoRMS Amphipoda</i></li>

    <li>De Prins, Jurate <a href="https://orcid.org/0000-0001-7637-5755"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>Global Gracillariidae; Global Gracillariidae</i></li>

    <li>De Prins, Willy <a href="https://orcid.org/0000-0002-4430-1425"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>Global Gracillariidae; Global Gracillariidae</i></li>

    <li>DeSalle, Rob - <i>WoRMS Placozoa</i></li>

    <li>Decker, Peter - <i>WoRMS Myriapoda</i></li>

    <li>Decock, Wim <a href="https://orcid.org/0000-0002-2168-9471"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Vlaams Instituut voor de Zee (VLIZ), Oostende, Belgium</i> - <i>WoRMS liaison</i></li>

    <li>Deem, Lesley S. <i>Illinois Natural History Survey, University of Illinois, Champaign, IL, United States of America</i> - <i>SF Dermaptera</i></li>

    <li>Defaye, Danielle - <i>WoRMS Copepoda</i></li>

    <li>Dekker, Henk - <i>WoRMS Mollusca</i></li>

    <li>Dellapé, Pablo M <a href="https://orcid.org/0000-0002-6914-1026"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>División Entomología, Museo de La Plata, La Plata, Argentina</i> - <i>SF Lygaeoidea</i></li>

    <li>Dempsey, Donald M. <a href="https://orcid.org/0000-0002-2200-5828"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Den Heyer, J. - <i>BdelloideaBase</i></li>

    <li>Deprez, Tim <a href="https://orcid.org/0000-0003-2862-0028"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Nematoda</i></li>

    <li>Dippenaar, Susan M - <i>WoRMS Copepoda</i></li>

    <li>Dmitriev, D.A. <i>INHS, University of Illinois, Champaign, IL, United States of America</i> - <i>3i Auchenorrhyncha; 3i Auchenorrhyncha</i></li>

    <li>Dohrmann, Martin - <i>WoRMS Porifera</i></li>

    <li>Doner, Stacy - <i>WoRMS Polychaeta</i></li>

    <li>Dorkeld, Franck. <i>French National Institute for Agriculture, Food, and Environment (INRAE), Montpellier, France</i> - <i>SpmWeb</i></li>

    <li>Downey, Rachel - <i>WoRMS Porifera</i></li>

    <li>Ducarme, Frédéric - <i>WoRMS Echinoidea</i></li>

    <li>Dutilh, Bas E. <a href="https://orcid.org/0000-0003-2329-7890"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Díaz, Maria-Cristina - <i>WoRMS Porifera</i></li>

    <li>Eades, David C. <i>Illinois Natural History Survey, University of Illinois, Champaign, IL, United States of America</i> - <i>SF Orthoptera</i></li>

    <li>Eibye-Jacobsen, Danny - <i>WoRMS Polychaeta</i></li>

    <li>Eisendle, Ursula - <i>WoRMS Nematoda</i></li>

    <li>Eitel, Michael - <i>WoRMS Placozoa</i></li>

    <li>El Nagar, Aliya - <i>WoRMS Pycnogonida</i></li>

    <li>Emig, Christian - <i>WoRMS Brachiopoda</i></li>

    <li>Emig, Christian C. <i>BrachNet</i> - <i>Phoronida Database</i></li>

    <li>Encarnação, Sarita Camacho da - <i>WoRMS Foraminifera</i></li>

    <li>Engel, Michael S. <a href="https://orcid.org/0000-0003-3067-077X"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Natural History Museum and Department of Ecology and Evolutionary Biology, University of Kansas, Lawrence, Kansas, United States of America</i> - <i>SF Isoptera</i></li>

    <li>Enghoff, Henrik - <i>WoRMS Myriapoda</i></li>

    <li>Evans, G.A. - <i>Mites GSD Tenuipalpidae</i></li>

    <li>Evenhuis, Neal L <a href="https://orcid.org/0000-0002-1314-755X"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Bishop Museum, Honolulu, United States of America</i> - <i>Systema Dipterorum</i></li>

    <li>Faber, Marien - <i>WoRMS Mollusca</i></li>

    <li>Farjon, A. - <i>Conifer Database</i></li>

    <li>Fauchald, Kristian - <i>WoRMS Polychaeta</i></li>

    <li>Fautin, Daphne - <i>WoRMS Actiniaria</i></li>

    <li>Favret, Colin <a href="https://orcid.org/0000-0001-6243-3184"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Department of Biological Sciences, University of Montreal, Montreal, Canada</i> - <i>SF Aphid</i></li>

    <li>Fernández-Rodríguez, Vanessa - <i>WoRMS Polychaeta</i></li>

    <li>Figueroa, Diego - <i>WoRMS Copepoda</i></li>

    <li>Fišer, Cene - <i>WoRMS Amphipoda</i></li>

    <li>Forró, L. - <i>FADA Cladocera</i></li>

    <li>Forstner, Martina - <i>WoRMS Echinoidea</i></li>

    <li>Francis, Ardath - <i>Brassicaceae</i></li>

    <li>Froese, Rainer. <i>Helmholtz Center for Ocean Research, Kiel, Germany</i> - <i>FishBase</i></li>

    <li>Fuchs, Anne <a href="https://orcid.org/0000-0001-5737-8803"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Australian National Botanic Gardens, Canberra, Australia</i> - <i>Global Team</i></li>

    <li>Furuya, Hidetaka - <i>WoRMS Orthonectida</i></li>

    <li>Garcia-Alvarez, Oscar - <i>WoRMS Mollusca</i></li>

    <li>García, María Laura <a href="https://orcid.org/0000-0001-6468-6943"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Gardner, M. - <i>Conifer Database</i></li>

    <li>Garic, Rade - <i>WoRMS Appendicularia; WoRMS Thaliacea</i></li>

    <li>Garnett, Stephen <a href="https://orcid.org/0000-0002-0724-7060"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Charles Darwin University, Darwin, Australia</i> - <i>Global Team</i></li>

    <li>Gasca, Rebeca <a href="https://orcid.org/0000-0002-9716-1964"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Amphipoda</i></li>

    <li>Gattolliat, J.-L. - <i>FADA Ephemeroptera</i></li>

    <li>Gaviria-Melo, Santiago - <i>WoRMS Copepoda</i></li>

    <li>Gerken, Sarah - <i>WoRMS Cumacea</i></li>

    <li>Gibson, David <a href="https://orcid.org/0000-0002-2908-491X"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Monogenea; WoRMS Trematoda</i></li>

    <li>Gibson, Raymond - <i>WoRMS Nemertea</i></li>

    <li>Gielis, Cees <a href="https://orcid.org/0000-0003-0857-1679"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Naturalis Biodiversity Center, Leiden, Netherlands</i> - <i>Pterophoroidea; Alucitoidea</i></li>

    <li>Giribet, Gonzalo <a href="https://orcid.org/0000-0002-5467-8429"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Museum of Comparative Zoology, Department of Organismic and Evolutionary Biology, Harvard University, Cambridge, MA, United States of America</i> - <i>WCO</i></li>

    <li>Gittenberger, Arjan - <i>WoRMS Ascidiacea</i></li>

    <li>Glasby, Christopher - <i>WoRMS Polychaeta</i></li>

    <li>Glover, Adrian G. <a href="https://orcid.org/0000-0002-9489-074X"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Polychaeta</i></li>

    <li>Gofas, Serge <a href="https://orcid.org/0000-0002-3141-3700"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Xenoturbellida; WoRMS Mollusca</i></li>

    <li>Grabowski, Michal <a href="https://orcid.org/0000-0002-4551-3454"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Amphipoda</i></li>

    <li>Granado, Alexia de A. <a href="https://orcid.org/0000-0003-1256-3567"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Departamento de Invertebrados, Universidade Federal do Rio de Janeiro, Rio de Janeiro, Brazil</i> - <i>WCO</i></li>

    <li>Gray, Alex. <i>Species 2000 Scotland, United Kingdom</i> - <i>Board of Directors</i></li>

    <li>Grimaldi, David A. <a href="https://orcid.org/0000-0002-2271-0172"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Division of Invertebrate Zoology, American Museum of Natural History, New York, New York, United States of America</i> - <i>SF Isoptera</i></li>

    <li>Gross, Onno - <i>WoRMS Foraminifera</i></li>

    <li>Grun, Tobias B. - <i>WoRMS Echinoidea</i></li>

    <li>Guerra-García, José Manuel <a href="https://orcid.org/0000-0001-6050-4997"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Amphipoda</i></li>

    <li>Guglielmone, Alberto - <i>TicksBase</i></li>

    <li>Guilbert, E. - <i>Lace Bugs Database</i></li>

    <li>Guimarães, Stéfhanne - <i>WoRMS Ostracoda</i></li>

    <li>Gusenleitner, Josef. <i>Oberostereichisches Landesmuseum, Linz, Austria</i> - <i>ZOBODAT Vespoidea</i></li>

    <li>Gómez-Noguera, Samuel Enrique - <i>WoRMS Copepoda</i></li>

    <li>Haas, Fabian <a href="https://orcid.org/0000-0002-5918-2262"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>African Insect Science for Food and Health (ICIPE), Nairobi, Kenya</i> - <i>SF Dermaptera</i></li>

    <li>Hadfield, Kerry A. <a href="https://orcid.org/0000-0003-1308-6360"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Isopoda</i></li>

    <li>Hagborg, A. - <i>ELPT</i></li>

    <li>Hajdu, Eduardo - <i>WoRMS Porifera</i></li>

    <li>Harrach, Balázs <a href="https://orcid.org/0000-0002-1410-6469"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Harris, Leslie - <i>WoRMS Polychaeta</i></li>

    <li>Harrison, Robert L. <a href="https://orcid.org/0000-0002-8348-3874"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Hassler, Michael. <i>Individual custodian in cooperation with the Botanical Garden of the Karlsruhe Institute of Technology, Karlsruhe, Germany</i> - <i>World Ferns; World Plants</i></li>

    <li>Hayward, Bruce W. <a href="https://orcid.org/0000-0003-1302-7686"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Foraminifera</i></li>

    <li>Heads, Sam W <a href="https://orcid.org/0000-0002-3141-1940"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Illinois Natural History Survey, University of Illinois, Champaign, IL, United States of America</i> - <i>SF Coleorrhyncha</i></li>

    <li>Hendrickson, R. Curtis <a href="https://orcid.org/0000-0001-6986-4630"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Hendrycks, Ed - <i>WoRMS Amphipoda</i></li>

    <li>Henry, Thomas J. <i>Systematic Entomology Laboratory, Agricultural Research Service, USDA c/o National Museum of Natural History, Smithsonian Institution, Washington, DC, United States of America</i> - <i>SF Lygaeoidea</i></li>

    <li>Herbert, Dai - <i>WoRMS Mollusca</i></li>

    <li>Hernandes, F.A. - <i>BdelloideaBase</i></li>

    <li>Hernandez, Francisco. <i>Vlaams Instituut voor de Zee (VLIZ), Oostende, Belgium</i> - <i>Board of Directors</i></li>

    <li>Hernández-Crespo, Juan Carlos. <i>Real Jardín Botánico, Spanish National Research Council, Madrid, Spain</i> - <i>nomen.eumycetozoa.com</i></li>

    <li>Herrera Bachiller, Alfonso - <i>WoRMS Nemertea</i></li>

    <li>Hine, Adrian. <i>Natural History Museum, London, United Kingdom</i> - <i>Global Lepidoptera Index</i></li>

    <li>Ho, Ju-shey - <i>WoRMS Copepoda</i></li>

    <li>Hodda, Mike - <i>WoRMS Nematoda</i></li>

    <li>Hoeksema, Bert <a href="https://orcid.org/0000-0001-8259-3783"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Scleractinia</i></li>

    <li>Hoenemann, Mario - <i>WoRMS Remipedia</i></li>

    <li>Holovachov, Oleksandr <a href="https://orcid.org/0000-0002-4285-0754"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Nematoda</i></li>

    <li>Holstein, J. - <i>GloBIS (GART)</i></li>

    <li>Hooge, Matthew - <i>WoRMS Turbellarians</i></li>

    <li>Hooper, John <a href="https://orcid.org/0000-0003-1722-5954"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Porifera</i></li>

    <li>Hopcroft, Russell - <i>WoRMS Appendicularia</i></li>

    <li>Hopkins, Heidi. <i>University of Illinois, Ithaca, NY, United States of America</i> - <i>SF Plecoptera; SF Embioptera; SF Psocodea; SF Dermaptera; SF Zoraptera; SF Mantophasmatodea; SF Grylloblattodea; SF Isoptera</i></li>

    <li>Horak, Ivan <a href="https://orcid.org/0000-0002-2200-6126"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>University of Pretoria, Pretoria, South Africa</i> - <i>TicksBase</i></li>

    <li>Horton, Tammy <a href="https://orcid.org/0000-0003-4250-1068"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Amphipoda</i></li>

    <li>Hosoya, Tsuyoshi <a href="https://orcid.org/0000-0001-5360-5677"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Department of Botany, National Museum of Nature and Science, Tokyo, Japan</i> - <i>Global Team</i></li>

    <li>Houart, Roland - <i>WoRMS Mollusca</i></li>

    <li>Hošek, Jirí - <i>ReptileDB</i></li>

    <li>Hughes, Lauren - <i>WoRMS Amphipoda</i></li>

    <li>Huijbers, Chantal <a href="https://orcid.org/0000-0001-5206-3415"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Naturalis Biodiversity Center, Leiden, Netherlands</i> - <i>Secretariat</i></li>

    <li>Häuser, C. - <i>GloBIS (GART)</i></li>

    <li>Iniesta, Luiz Felipe Moretti - <i>WoRMS Myriapoda</i></li>

    <li>Ivanenko, Slava - <i>WoRMS Copepoda</i></li>

    <li>Janssen, Ronald - <i>WoRMS Mollusca</i></li>

    <li>Janssens, F. - <i>Collembola.org</i></li>

    <li>Jarms, Gerhard - <i>WoRMS Cubozoa; WoRMS Scyphozoa</i></li>

    <li>Jaume, Damià <a href="https://orcid.org/0000-0002-1857-3005"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Thermosbaenacea; WoRMS Amphipoda</i></li>

    <li>Jazdzewski, Krzysztof - <i>WoRMS Amphipoda</i></li>

    <li>Johnson, Kevin P. <i>Illinois Natural History Survey, University of Illinois, Champaign, IL, United States of America</i> - <i>SF Psocodea</i></li>

    <li>Junglen, Sandra <a href="https://orcid.org/0000-0002-3799-6011"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Jóźwiak, Piotr - <i>WoRMS Tanaidacea</i></li>

    <li>Kabat, Alan - <i>WoRMS Mollusca</i></li>

    <li>Kamiński, Marcin Jan <a href="https://orcid.org/0000-0002-2915-0614"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Museum and Institute of Zoology, Polish Academy of Sciences, Warsaw, Poland</i> - <i>Sepidiini tribe</i></li>

    <li>Kanda, Kojun. <i>Nortern Arizona University, Flagstaff, AZ, United States of America</i> - <i>Sepidiini tribe</i></li>

    <li>Kantor, Yuri - <i>WoRMS Mollusca</i></li>

    <li>Karanovic, Ivana - <i>WoRMS Ostracoda</i></li>

    <li>Kathirithamby, Jeyaraney <a href="https://orcid.org/0000-0001-9907-4779"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Department of Zoology, University of oxford, United Kingdom</i> - <i>WoRMS Strepsiptera; Taxonomy Group</i></li>

    <li>Kelly, Michelle - <i>WoRMS Porifera</i></li>

    <li>Kim, Young-Hyo - <i>WoRMS Amphipoda</i></li>

    <li>King, Rachael <a href="https://orcid.org/0000-0001-8089-7599"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Amphipoda</i></li>

    <li>Kirk, Paul <a href="https://orcid.org/0000-0002-0658-7338"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Royal Botanic Gardens, Kew, London, United Kingdom</i> - <i>Microsporidia; Species Fungorum Plus</i></li>

    <li>Kitching, Ian <a href="https://orcid.org/0000-0003-4738-5967"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Natural History Museum, London, United Kingdom</i> - <i>Global Lepidoptera Index</i></li>

    <li>Klautau, Michelle - <i>WoRMS Porifera</i></li>

    <li>Knowles, Nick J. <a href="https://orcid.org/0000-0002-6696-1344"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Koenemann, Stefan - <i>WoRMS Remipedia</i></li>

    <li>Korovchinsky, N.M. - <i>FADA Cladocera</i></li>

    <li>Kotov, A. - <i>FADA Cladocera</i></li>

    <li>Kouwenberg, Juliana - <i>WoRMS Copepoda</i></li>

    <li>Kovács, Zoltan - <i>WoRMS Mollusca</i></li>

    <li>Krapf, Andrea - <i>WoRMS Echinoidea</i></li>

    <li>Krapp-Schickel, Traudl - <i>WoRMS Amphipoda</i></li>

    <li>Krishna, Kumar. <i>Division of Invertebrate Zoology, American Museum of Natural History, New York, New York, United States of America</i> - <i>SF Isoptera</i></li>

    <li>Krishna, Valerie. <i>Division of Invertebrate Zoology, American Museum of Natural History, New York, New York, United States of America</i> - <i>SF Isoptera</i></li>

    <li>Kristensen, Reinhardt Møbjerg <a href="https://orcid.org/0000-0001-9549-1188"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Loricifera</i></li>

    <li>Kroh, Andreas <a href="https://orcid.org/0000-0002-8566-8848"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Echinoidea</i></li>

    <li>Kroupa, A.S. - <i>GloBIS (GART); HymIS Crabronidae &amp; Rhopalosomatidae; HymIS Pompilidae</i></li>

    <li>Krupovic, Mart <a href="https://orcid.org/0000-0001-5486-0098"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Kuhn, Jens H. <a href="https://orcid.org/0000-0002-7800-6045"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Kury, Adriano B. <a href="https://orcid.org/0000-0002-8334-6204"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Departamento de Invertebrados, Museu Nacional, Universidade Federal do Rio de Janeiro, Rio de Janeiro, Brazil</i> - <i>WCO</i></li>

    <li>Kury, Milena S. <a href="https://orcid.org/0000-0003-0501-9440"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Departamento de Meteorologia, Fundação Cearense de Meteorologia e Recursos Hídricos (FUNCEME), Fortaleza, Brazil</i> - <i>WCO</i></li>

    <li>Kvaček, J. - <i>Fossil Ginkgoales</i></li>

    <li>Köhler, Frank - <i>WoRMS Mollusca</i></li>

    <li>Lado, Carlos <a href="https://orcid.org/0000-0002-6135-2873"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Real Jardín Botánico, Spanish National Research Council, Madrid, Spain</i> - <i>nomen.eumycetozoa.com</i></li>

    <li>Lambert, Amy J. <a href="https://orcid.org/0000-0002-7571-3529"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Lambert, Gretchen - <i>WoRMS Ascidiacea</i></li>

    <li>Lazarus, David - <i>WoRMS Polycystina</i></li>

    <li>Le Coze, François - <i>WoRMS Foraminifera</i></li>

    <li>LeCroy, Sara - <i>WoRMS Amphipoda</i></li>

    <li>Leduc, Daniel <a href="https://orcid.org/0000-0001-5310-9198"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Nematoda</i></li>

    <li>Lefkowitz, Elliot J. <a href="https://orcid.org/0000-0002-4748-4925"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Li-Qiang, Ji <a href="https://orcid.org/0000-0002-1703-5723"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Key Laboratory of Animal Ecology and Conservation Biology, Chinese Academy of Sciences, Beijing, China</i> - <i>Global Team</i></li>

    <li>Lichtwardt, Robert (†) - <i>Trichomycetes</i></li>

    <li>Lobanov, A. - <i>Parhost</i></li>

    <li>Lohrmann, V. - <i>HymIS Crabronidae &amp; Rhopalosomatidae</i></li>

    <li>Londoño-Mesa, Mario - <i>WoRMS Polychaeta</i></li>

    <li>Longhorn, Stuart J. <a href="https://orcid.org/0000-0002-1819-3010"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>, United Kingdom</i> - <i>WCO</i></li>

    <li>Lorenz, Wolfgang <a href="https://orcid.org/0000-0003-2894-2166"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>CarabCat; CarabCat</i></li>

    <li>Lowry, Jim - <i>WoRMS Amphipoda</i></li>

    <li>Lujan-Toro, Beatriz E. <a href="https://orcid.org/0000-0003-3533-3318"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>Brassicaceae</i></li>

    <li>Lumen, Ryan. <i>Nortern Arizona University, Flagstaff, AZ, United States of America</i> - <i>Sepidiini tribe</i></li>

    <li>Lyal, Chris HC <a href="https://orcid.org/0000-0003-3647-6222"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Natural History Museum, London, United Kingdom</i> - <i>WTaxa; Global Lepidoptera Index</i></li>

    <li>Lyangouzov, I. - <i>Parhost</i></li>

    <li>Lörz, Anne-Nina - <i>WoRMS Amphipoda</i></li>

    <li>Macklin, James A. <a href="https://orcid.org/0000-0001-9508-1349"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>Brassicaceae; Brassicaceae</i></li>

    <li>Madin, Larry - <i>WoRMS Thaliacea</i></li>

    <li>Maehr, Michael D <a href="https://orcid.org/0000-0003-4573-0536"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Illinois Natural History Survey, University of Illinois, Champaign, IL, United States of America</i> - <i>SF Plecoptera; SF Embioptera; SF Dermaptera; SF Zoraptera; SF Mantophasmatodea; SF Grylloblattodea; SF Coleorrhyncha</i></li>

    <li>Magill, Robert E. <i>Missouri Botanical Garden, St. Louis, United States of America</i> - <i>MOST</i></li>

    <li>Magnien, Philippe <a href="https://orcid.org/0000-0002-2637-7522"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Muséum National d'Histoire Naturelle, Paris, France</i> - <i>Tessaratomidae Database</i></li>

    <li>Mah, Christopher - <i>WoRMS Asteroidea</i></li>

    <li>Mal, Noel. <i>Royal Belgian Institute of Natural Sciences, Brussels, Belgium</i> - <i>Sepidiini tribe</i></li>

    <li>Mamo, Briony - <i>WoRMS Foraminifera</i></li>

    <li>Mamos, Tomasz <a href="https://orcid.org/0000-0002-0524-3015"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Amphipoda</i></li>

    <li>Manconi, Renata <a href="https://orcid.org/0000-0002-7619-8493"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Porifera</i></li>

    <li>Marek, Paul - <i>WoRMS Myriapoda</i></li>

    <li>Marshall, Bruce - <i>WoRMS Mollusca</i></li>

    <li>Martin, Jon H. <i>Natural History Museum, London, United Kingdom</i> - <i>The White-Files</i></li>

    <li>Martin, Sara L. <a href="https://orcid.org/0000-0003-2055-6498"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>Brassicaceae</i></li>

    <li>Martínez-Melo, Alejandra - <i>WoRMS Echinoidea</i></li>

    <li>Maslin, Bruce <a href="https://orcid.org/0000-0002-3039-0973"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Western Australian Herbarium, Perth, Australia</i> - <i>WWW</i></li>

    <li>McFadden, Catherine - <i>WoRMS Octocorallia</i></li>

    <li>McKamey, S. - <i>MOWD; 3i Auchenorrhyncha</i></li>

    <li>McMurtry, J.A. - <i>Mites GSD Phytoseiidae</i></li>

    <li>Medvedev, S. - <i>Parhost</i></li>

    <li>Mees, Jan <a href="https://orcid.org/0000-0001-5709-3816"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Leptostraca</i></li>

    <li>Mendes, Amanda C. <a href="https://orcid.org/0000-0002-7220-6396"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Departamento de Zoologia, Instituto de Biologia Roberto de Alcantara Gomes, Universidade do Estado do Rio de Janeiro (UERJ), Rio de Janeiro, Brazil</i> - <i>WCO</i></li>

    <li>Merrin, Kelly - <i>WoRMS Isopoda</i></li>

    <li>Mesa, N.C. - <i>Mites GSD Tenuipalpidae</i></li>

    <li>Messing, Charles - <i>WoRMS Crinoidea</i></li>

    <li>Migeon, Alain. <i>French National Institute for Agriculture, Food, and Environment (INRAE), Montpellier, France</i> - <i>SpmWeb</i></li>

    <li>Miller, Douglas R. <i>Systematic Entomology Laboratory, US Department of Agriculture, Beltsville, MD, United States of America</i> - <i>ScaleNet</i></li>

    <li>Mills, Claudia - <i>WoRMS Ctenophora; WoRMS Staurozoa</i></li>

    <li>Minelli, A. - <i>ChiloBase</i></li>

    <li>Miskelly, Ashley - <i>WoRMS Echinoidea</i></li>

    <li>Mokievsky, Vadim - <i>WoRMS Nematoda</i></li>

    <li>Molodtsova, Tina - <i>WoRMS Ceriantharia; WoRMS Antipatharia</i></li>

    <li>Mongiardino Koch, Nicolas - <i>WoRMS Echinoidea</i></li>

    <li>Mooi, Richard - <i>WoRMS Echinoidea</i></li>

    <li>Morandini, André - <i>WoRMS Scyphozoa</i></li>

    <li>Moreira da Rocha, Rosana - <i>WoRMS Ascidiacea</i></li>

    <li>Morrow, Christine - <i>WoRMS Porifera</i></li>

    <li>Mushegian, Arcady R. <a href="https://orcid.org/0000-0002-6809-9225"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Narita, J.P.Z. - <i>Mites GSD Phytoseiidae</i></li>

    <li>Nealova, Lenka - <i>WoRMS Polychaeta</i></li>

    <li>Nery, Davi Galvão - <i>WoRMS Ostracoda</i></li>

    <li>Neu-Becker, U. <i>Max Planck Institute, Munich, Germany</i> - <i>SF Plecoptera</i></li>

    <li>Neubauer, Thomas A. <a href="https://orcid.org/0000-0002-1398-9941"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Mollusca</i></li>

    <li>Neubert, Eike - <i>WoRMS Mollusca</i></li>

    <li>Neuhaus, Birger - <i>WoRMS Kinorhyncha</i></li>

    <li>Newton, Alfred <a href="https://orcid.org/0000-0001-9885-6306"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Field Museum of Natural History, Chicago, United States of America</i> - <i>StaphBase</i></li>

    <li>Ng Kee Lin, Peter - <i>WoRMS Brachyura</i></li>

    <li>Nguyen, Anh - <i>WoRMS Myriapoda</i></li>

    <li>Nibert, Max L. <a href="https://orcid.org/0000-0002-9703-9328"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Nicolson, David <a href="https://orcid.org/0000-0002-7987-0679"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>US Geological Survey / Smithsonian Institution, Washington, D.C., United States of America</i> - <i>Global Team, Taxonomy Group</i></li>

    <li>Nijhof, Ard <a href="https://orcid.org/0000-0001-7373-2243"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Institut für Parasitologie und Tropenveterinärmedizin, Freie Universität Berlin, Berlin, Germany</i> - <i>TicksBase</i></li>

    <li>Nishikawa, Teruaki - <i>WoRMS Cephalochordata</i></li>

    <li>Norenburg, Jon <a href="https://orcid.org/0000-0001-7776-1527"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Nemertea</i></li>

    <li>Novoselova, M. - <i>ILDIS</i></li>

    <li>Noyes, John. <i>Natural History Museum, London, United Kingdom</i> - <i>UCD</i></li>

    <li>O'Hara, Tim <a href="https://orcid.org/0000-0003-0885-6578"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Ophiuroidea</i></li>

    <li>Ochoa, R. - <i>Mites GSD Tenuipalpidae</i></li>

    <li>Oksanen, Hanna M. <a href="https://orcid.org/0000-0003-3047-8294"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Ollerenshaw, Justin. <i>Natural History Museum, London, United Kingdom</i> - <i>Global Lepidoptera Index</i></li>

    <li>Oosterbroek, P. - <i>CCW</i></li>

    <li>Opresko, Dennis - <i>WoRMS Antipatharia</i></li>

    <li>Orton, Richard J. <a href="https://orcid.org/0000-0002-3389-4325"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Osborne, Roy - <i>The World List of Cycads</i></li>

    <li>Osigus, Hans-Jürgen - <i>WoRMS Placozoa</i></li>

    <li>Oswald, J.D. - <i>LDL Neuropterida</i></li>

    <li>Ota, Yuzo - <i>WoRMS Isopoda</i></li>

    <li>Otte, Daniel. <i>The Academy of Natural Sciences of Philadelphia, Drexel University, Philadelphia, PA, United States of America</i> - <i>SF Orthoptera; SF Mantodea</i></li>

    <li>Ouvrard, David <a href="https://orcid.org/0000-0003-2931-6116"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Muséum National d’Histoire Naturelle, Paris, France</i> - <i>Psyllist; The White-Files</i></li>

    <li>Paleobiology Database contributors - <i>PaleoBioDB</i></li>

    <li>Pape, Thomas <a href="https://orcid.org/0000-0001-6609-0609"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Natural History Museum of Denmark, Natural History Museum of Denmark, Copenhagen, Denmark</i> - <i>Systema Dipterorum; Global Team, Taxonomy Group</i></li>

    <li>Paulay, Gustav - <i>WoRMS Holothuroidea; WoRMS Priapulida</i></li>

    <li>Pauly, Daniel. <i>Fisheries Centre, University of British Columbia, Vancouver, Canada</i> - <i>FishBase</i></li>

    <li>Paxton, Hannelore - <i>WoRMS Polychaeta</i></li>

    <li>Petrusek, A. - <i>FADA Cladocera</i></li>

    <li>Peña Santiago, Reyes <a href="https://orcid.org/0000-0003-1125-5490"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Nematoda</i></li>

    <li>Picton, Bernard <a href="https://orcid.org/0000-0002-1500-2215"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Mollusca</i></li>

    <li>Pierrot-Bults, Annelies - <i>WoRMS Chaetognatha</i></li>

    <li>Pisera, Andrzej - <i>WoRMS Porifera</i></li>

    <li>Pitkin, Brian. <i>Natural History Museum, London, United Kingdom</i> - <i>Global Lepidoptera Index</i></li>

    <li>Poore, Gary - <i>WoRMS Thermosbaenacea; WoRMS Isopoda</i></li>

    <li>Pulawski, W.J. - <i>HymIS Crabronidae &amp; Rhopalosomatidae</i></li>

    <li>Pyle, Richard <a href="https://orcid.org/0000-0003-0768-1286"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Bernice Pauahi Bishop Museum, Honolulu, Hawaii, United States of America</i> - <i>Global Team, Taxonomy Group</i></li>

    <li>Páll-Gergely, Barna - <i>WoRMS Mollusca</i></li>

    <li>Pérez-García, José Andrés <a href="https://orcid.org/0000-0001-8061-5870"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Nematoda</i></li>

    <li>Rainer, H. - <i>AnnonBase</i></li>

    <li>Raz, Lauren. <i>National University of Colombia, Bogotá, Colombia</i> - <i>Global Team, Taxonomy Group</i></li>

    <li>Read, Geoffrey - <i>WoRMS Polychaeta</i></li>

    <li>Rees, Tony <a href="https://orcid.org/0000-0003-1887-5211"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>compiler; IRMNG; Taxonomy Group</i></li>

    <li>Reimer, James Davis - <i>WoRMS Zoantharia</i></li>

    <li>Rein, Jan Ove <a href="https://orcid.org/0000-0002-9976-358X"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Medicine and Health Library, Norwegian University of Science &amp; Technology, Trondheim, Norway</i> - <i>The Scorpion Files</i></li>

    <li>Reip, Hans - <i>WoRMS Myriapoda</i></li>

    <li>Reuscher, Michael - <i>WoRMS Polychaeta</i></li>

    <li>Richling, Ira - <i>WoRMS Mollusca</i></li>

    <li>Rius, Marc <a href="https://orcid.org/0000-0002-2195-6605"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Ascidiacea</i></li>

    <li>Robertson, David L. <a href="https://orcid.org/0000-0001-6338-0221"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Robertson, Tim <a href="https://orcid.org/0000-0001-6215-3617"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>GBIF, Copenhagen, Denmark</i> - <i>Information Systems Group</i></li>

    <li>Robinson, Gaden. <i>Natural History Museum, London, United Kingdom</i> - <i>Global Lepidoptera Index</i></li>

    <li>Robinson, Gaden S (†) - <i>Tineidae NHM</i></li>

    <li>Rodríguez, Estefania - <i>WoRMS Actiniaria</i></li>

    <li>Rogacheva, Antonina - <i>WoRMS Holothuroidea</i></li>

    <li>Romani, Luigi - <i>WoRMS Mollusca</i></li>

    <li>Rosenberg, Gary - <i>WoRMS Mollusca</i></li>

    <li>Rubino, Luisa <a href="https://orcid.org/0000-0002-2073-2415"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Ruggiero, Michael. <i>Smithsonian Institution, Washington, D.C., United States of America</i> - <i>Taxonomy Group</i></li>

    <li>Ríos, Pilar - <i>WoRMS Porifera</i></li>

    <li>Rützler, Klaus - <i>WoRMS Porifera</i></li>

    <li>Sabanadzovic, Sead <a href="https://orcid.org/0000-0002-2995-2633"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Salazar-Vallejo, Sergio - <i>WoRMS Polychaeta</i></li>

    <li>Sanborn, A. - <i>3i Auchenorrhyncha</i></li>

    <li>Sartori, M. - <i>FADA Ephemeroptera</i></li>

    <li>Sattler, Klaus. <i>Natural History Museum, London, United Kingdom</i> - <i>Gelechiidae</i></li>

    <li>Saucède, Thomas - <i>WoRMS Echinoidea</i></li>

    <li>Schierwater, Bernd - <i>WoRMS Placozoa</i></li>

    <li>Schilling, Steve - <i>WoRMS Turbellarians</i></li>

    <li>Schmid-Egger, C. - <i>HymIS Crabronidae &amp; Rhopalosomatidae; HymIS Pompilidae</i></li>

    <li>Schmidt-Rhaesa, A. - <i>FADA Nematomorpha</i></li>

    <li>Schneider, Simon - <i>WoRMS Mollusca</i></li>

    <li>Schoolmeesters, Paul <a href="https://orcid.org/0000-0002-0721-6002"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Schoolmeesters P.</i> - <i>Scarabs</i></li>

    <li>Schotte, Marilyn - <i>WoRMS Isopoda</i></li>

    <li>Schuchert, Peter - <i>WoRMS Hydrozoa</i></li>

    <li>Schuh, R.T. - <i>PBI Plant Bug</i></li>

    <li>Schönberg, Christine <a href="https://orcid.org/0000-0001-6286-8196"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Porifera</i></li>

    <li>Scoble, Malcolm. <i>Natural History Museum, London, United Kingdom</i> - <i>Global Lepidoptera Index</i></li>

    <li>Segers, H. - <i>FADA Rotifera</i></li>

    <li>Senna, André - <i>WoRMS Amphipoda</i></li>

    <li>Serejo, Cristiana <a href="https://orcid.org/0000-0001-9132-5537"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Amphipoda</i></li>

    <li>Sforzi, A. - <i>Brentids</i></li>

    <li>Sharma, Jyotsna - <i>WoRMS Nematoda</i></li>

    <li>Shear, William - <i>WoRMS Myriapoda</i></li>

    <li>Shenkar, Noa - <i>WoRMS Ascidiacea</i></li>

    <li>Short, Megan - <i>WoRMS Myriapoda</i></li>

    <li>Siciński, Jacek - <i>WoRMS Polychaeta</i></li>

    <li>Siddell, Stuart G. <a href="https://orcid.org/0000-0002-8702-7868"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Siegel, Volker - <i>WoRMS Euphausiacea</i></li>

    <li>Sierwald, Petra <a href="https://orcid.org/0000-0003-2592-1298"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Myriapoda</i></li>

    <li>Silva, E.S. - <i>Mites GSD Ologamasidae</i></li>

    <li>Simmonds, Peter <a href="https://orcid.org/0000-0002-7964-4700"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Simmons, Elizabeth - <i>WoRMS Myriapoda</i></li>

    <li>Simonsen, Thomas <a href="https://orcid.org/0000-0001-9857-9564"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Natural History Museum, London, United Kingdom</i> - <i>Global Lepidoptera Index</i></li>

    <li>Sinniger, Frederic - <i>WoRMS Zoantharia</i></li>

    <li>Sket, Boris <a href="https://orcid.org/0000-0002-7398-5483"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Amphipoda</i></li>

    <li>Smith, Aaron D. <a href="https://orcid.org/0000-0002-1286-950X"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Nortern Arizona University, Flagstaff, AZ, United States of America</i> - <i>Sepidiini tribe</i></li>

    <li>Smith, Donald B. <a href="https://orcid.org/0000-0002-2876-5318"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Smith, Vincent S <a href="https://orcid.org/0000-0001-5297-7452"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>The Natural History Museum, London, United Kingdom</i> - <i>SF Psocodea</i></li>

    <li>Smol, Nicole - <i>WoRMS Nematoda</i></li>

    <li>Soulier-Perkins, A. - <i>COOL</i></li>

    <li>South, Eric J. <a href="https://orcid.org/0000-0001-7894-7219"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Lyon College, Batesville, AR, United States of America</i> - <i>SF Isoptera</i></li>

    <li>Souza-Filho, Jesser F. <a href="https://orcid.org/0000-0001-5248-2134"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Amphipoda</i></li>

    <li>Spearman, Lauren. <i>Department of Ecology and Evolution, Rutgers University, New Brunswick, NJ, United States of America</i> - <i>SF Mantodea</i></li>

    <li>Spelda, Jörg - <i>WoRMS Myriapoda</i></li>

    <li>Stampar, Sérgio - <i>WoRMS Ceriantharia</i></li>

    <li>Steger, Jan - <i>WoRMS Echinoidea</i></li>

    <li>Steiner, A. - <i>GloBIS (GART)</i></li>

    <li>Stemme, Torben - <i>WoRMS Remipedia</i></li>

    <li>Sterrer, Wolfgang - <i>WoRMS Gnathostomulida</i></li>

    <li>Stevenson, Dennis <a href="https://orcid.org/0000-0002-2986-7076"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>New York Botanical Garden, New York, United States of America</i> - <i>The World List of Cycads</i></li>

    <li>Stiewe, Martin B D. <i>The Natural History Museum, London, United Kingdom</i> - <i>SF Mantodea</i></li>

    <li>Stjernegaard Jeppesen, Thomas <a href="https://orcid.org/0000-0003-1691-239X"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>GBIF, Copenhagen, Denmark</i> - <i>Secretariat</i></li>

    <li>Stoev, Pavel - <i>WoRMS Myriapoda</i></li>

    <li>Strand, Malin <a href="https://orcid.org/0000-0002-8144-9592"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Nemertea</i></li>

    <li>Stueber, G. <i>Max Planck Institute, Munich, Germany</i> - <i>SF Plecoptera</i></li>

    <li>Stöhr, Sabine <a href="https://orcid.org/0000-0002-2586-7239"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Ophiuroidea</i></li>

    <li>Suzuki, Nobuhiro <a href="https://orcid.org/0000-0003-0097-9856"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Suárez-Morales, Eduardo - <i>WoRMS Copepoda</i></li>

    <li>Swalla, Billie <a href="https://orcid.org/0000-0002-4200-2544"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Ascidiacea</i></li>

    <li>Swedo, Jacek. <i>University of Gdansk, Poland</i> - <i>Global Team, Taxonomy Group</i></li>

    <li>Szumik, Claudia <a href="https://orcid.org/0000-0001-5361-0580"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Unidad Ejecutora Lillo CONICET - FML: San Miguel de Tucumán, Argentina</i> - <i>SF Embioptera</i></li>

    <li>Sánchez-Ruiz, M. - <i>WTaxa</i></li>

    <li>Söderström, L. - <i>ELPT</i></li>

    <li>Taiti, Stefano - <i>WoRMS Isopoda</i></li>

    <li>Takiya, D.M. - <i>3i Auchenorrhyncha</i></li>

    <li>Tandberg, Anne Helene <a href="https://orcid.org/0000-0003-3470-587X"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Amphipoda</i></li>

    <li>Tang, Danny - <i>WoRMS Copepoda</i></li>

    <li>Tavakilian, Gerard <a href="https://orcid.org/0000-0002-3310-1903"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Muséum national d'Histoire naturelle (MNHN), Paris, France</i> - <i>TITAN</i></li>

    <li>Taylor, John - <i>WoRMS Mollusca</i></li>

    <li>Tchesunov, Alexei - <i>WoRMS Nematoda</i></li>

    <li>Thessen, A. - <i>Gymnodinium</i></li>

    <li>Thomas, James Darwin - <i>WoRMS Amphipoda</i></li>

    <li>Thomas, P. - <i>Conifer Database</i></li>

    <li>ThripsWiki - <i>ThripsWiki</i></li>

    <li>Thuesen, Erik - <i>WoRMS Chaetognatha</i></li>

    <li>Thurston, Mike - <i>WoRMS Amphipoda</i></li>

    <li>Thuy, Ben - <i>WoRMS Ophiuroidea</i></li>

    <li>Timm, Tarmo - <i>WoRMS Oligochaeta</i></li>

    <li>Todaro, Antonio <a href="https://orcid.org/0000-0002-6353-7281"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Gastrotricha</i></li>

    <li>Turiault, M. - <i>GloBIS (GART)</i></li>

    <li>Turon, Xavier <a href="https://orcid.org/0000-0002-9229-5541"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Ascidiacea</i></li>

    <li>Tyler, Seth - <i>WoRMS Cestoda; WoRMS Turbellarians</i></li>

    <li>Uetz, Peter <a href="https://orcid.org/0000-0001-6194-4927"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Virginia Commonwealth University, Richmond, VA, United States of America</i> - <i>ReptileDB</i></li>

    <li>Ulmer, Jonah M. <a href="https://orcid.org/0000-0002-9185-6378"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Pennsylvania State University, PA, United States of America</i> - <i>Sepidiini tribe</i></li>

    <li>Uribe-Palomino, Julian - <i>WoRMS Copepoda</i></li>

    <li>Vacelet, Jean - <i>WoRMS Porifera</i></li>

    <li>Vachard, Daniel - <i>WoRMS Foraminifera</i></li>

    <li>Vader, Wim - <i>WoRMS Amphipoda</i></li>

    <li>Van Dooerslaer, Koenraad <a href="https://orcid.org/0000-0002-2985-0733"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Vandamme, Anne-Mieke <a href="https://orcid.org/0000-0002-6594-2766"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Vanhoorne, Bart. <i>Vlaams Instituut voor de Zee (VLIZ), Oostende, Belgium</i> - <i>Information Systems Group</i></li>

    <li>Vanreusel, Ann <a href="https://orcid.org/0000-0003-2983-9523"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Nematoda</i></li>

    <li>Varsani, Arvind <a href="https://orcid.org/0000-0003-4111-2415"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Venekey, Virág - <i>WoRMS Nematoda</i></li>

    <li>Vinarski, Maxim - <i>WoRMS Mollusca</i></li>

    <li>Vonk, Ronald - <i>WoRMS Amphipoda</i></li>

    <li>Vos, Chris - <i>WoRMS Mollusca</i></li>

    <li>Väinölä, Risto - <i>WoRMS Amphipoda</i></li>

    <li>Walker, Peter J. <a href="https://orcid.org/0000-0003-1851-642X"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Walker-Smith, Genefor - <i>WoRMS Leptostraca</i></li>

    <li>Walter, T. Chad - <i>WoRMS Copepoda</i></li>

    <li>Wambiji, Nina <a href="https://orcid.org/0000-0002-7775-5300"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Kenya Marine and Fisheries Research Institute, Mombasa, Kenya</i> - <i>Global Team</i></li>

    <li>Warwick, Suzanne - <i>Brassicaceae</i></li>

    <li>Watling, Les - <i>WoRMS Cumacea</i></li>

    <li>Weaver, Haylee. <i>Department of the Environment, Australian Government, Canberra, Australia</i> - <i>Global Team</i></li>

    <li>Webb, J. - <i>FADA Ephemeroptera</i></li>

    <li>Welbourn, W.C. - <i>Mites GSD Tenuipalpidae</i></li>

    <li>Wesener, Thomas - <i>WoRMS Myriapoda</i></li>

    <li>Whipps, Christopher - <i>WoRMS Myxozoa</i></li>

    <li>White, Kristine - <i>WoRMS Amphipoda</i></li>

    <li>Wieneke, Ulrich - <i>WoRMS Mollusca</i></li>

    <li>Wilson, George D.F. - <i>WoRMS Isopoda</i></li>

    <li>Wilson, Robin - <i>WoRMS Polychaeta</i></li>

    <li>Wing, Peter <a href="https://orcid.org/0000-0002-8634-8790"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> <i>Natural History Museum, London, United Kingdom</i> - <i>Global Lepidoptera Index</i></li>

    <li>Wirth, Christopher C. <i>Nortern Arizona University, Flagstaff, AZ, United States of America</i> - <i>Sepidiini tribe</i></li>

    <li>World Spider Catalog - <i>WSC</i></li>

    <li>Yesson, C. - <i>Droseraceae Database</i></li>

    <li>Yoder, Mathew. <i>Illinois Natural History Survey, Champaign, IL, United States of America</i> - <i>TaxonWorks liaison</i></li>

    <li>Yu, Dicky Sick Ki. <i>Taxapad project</i> - <i>Taxapad Ichneumonoidea</i></li>

    <li>Yunakov, N. - <i>3i Curculio</i></li>

    <li>Zahniser, J. - <i>3i Auchenorrhyncha</i></li>

    <li>Zanol, Joana - <i>WoRMS Polychaeta</i></li>

    <li>Zarucchi, J. - <i>ILDIS</i></li>

    <li>Zeidler, Wolfgang - <i>WoRMS Amphipoda</i></li>

    <li>Zerbini, Francisco Murilo <a href="https://orcid.org/0000-0001-8617-0200"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li>Zhang, Z.Q. - <i>Animal Biodiversity</i></li>

    <li>Zhao, Zeng - <i>WoRMS Nematoda</i></li>

    <li>Ziegler, Alexander - <i>WoRMS Echinoidea</i></li>

    <li>Zinetti, F. - <i>Brentids</i></li>

    <li>d'Hondt, Jean-Loup - <i>WoRMS Gastrotricha</i></li>

    <li>de Moraes, G.J. - <i>Mites GSD Ologamasidae; Mites GSD Rhodacaridae; Mites GSD Phytoseiidae; Mites GSD Tenuipalpidae</i></li>

    <li>de Voogd, Nicole <a href="https://orcid.org/0000-0002-7985-5604"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>WoRMS Porifera</i></li>

    <li>ten Hove, Harry - <i>WoRMS Polychaeta</i></li>

    <li>ter Poorten, Jan Johan - <i>WoRMS Mollusca</i></li>

    <li>van Nieukerken, E.J. - <i>Nepticuloidea</i></li>

    <li>van Soest, Rob - <i>WoRMS Porifera; WoRMS Thaliacea</i></li>

    <li>van Tol, J. - <i>Odonata</i></li>

    <li>von Konrat, M. - <i>ELPT</i></li>

    <li>Łobocka, Małgorzata <a href="https://orcid.org/0000-0003-0679-5193"><img alt="ORCID logo" src="https://info.orcid.org/wp-content/uploads/2019/11/orcid_16x16.png" width="16" height="16" /></a> - <i>ICTV MSL</i></li>

    <li> <i>ITIS</i> - <i>ITIS</i></li>

    <li> <i>International Committee on Taxonomy of Viruses (ICTV)</i> - <i>ICTV MSL</i></li>

    <li> <i>The Royal Botanic Gardens, Kew, London, United Kingdom</i> - <i>WCVP</i></li>

  </ul>
</div>

<h3 id="publisher">Publisher</h3>
<p><i>Catalogue of Life, Leiden, Netherlands</i></p>


      <div class='spacing'></div>
    </div>

  </div>
  <div class='four spacing'></div>
</div>
    </div>

    <style>
    #cookie-notice {z-index: 80; padding: 0.5rem 1rem; font-size: 18px; min-height: 70px; display: none; text-align: center; position: fixed; bottom: 0; width: 100%; background: #FF8C00; color: rgba(255,255,255,0.8);}
    #cookie-notice a {display: inline-block; cursor: pointer; margin-left: 0.5rem;}
    @media (max-width: 767px) {
        #cookie-notice span {display: block; padding-top: 3px; margin-bottom: 1rem;}
        #cookie-notice a {position: relative; bottom: 4px;}
    }
</style>
<div id="cookie-notice"><div>We would like to use cookies to measure usage and report on the value of this website.</div><a id="cookie-notice-accept" class="btn btn-primary btn-sm">Approve</a><!--<a href="/privacy" class="btn btn-primary btn-sm">More info</a> --></div>
<script>
    function createCookie(name,value,days) {
        var expires = "";
        if (days) {
            var date = new Date();
            date.setTime(date.getTime() + (days*24*60*60*1000));
            expires = "; expires=" + date.toUTCString();
        }
        document.cookie = name + "=" + value + expires + "; path=/";
    }
    function readCookie(name) {
        var nameEQ = name + "=";
        var ca = document.cookie.split(';');
        for(var i=0;i < ca.length;i++) {
            var c = ca[i];
            while (c.charAt(0)==' ') c = c.substring(1,c.length);
            if (c.indexOf(nameEQ) == 0) return c.substring(nameEQ.length,c.length);
        }
        return null;
    }
    function eraseCookie(name) {
        createCookie(name,"",-1);
    }

    if(readCookie('cookie-notice-dismissed')=='true') {

        var head = document.getElementsByTagName('head')[0];
var js = document.createElement("script");
js.type = "text/javascript";
js.async = true;
js.src = "https://www.googletagmanager.com/gtag/js?id=G-6MXW7NJY01 ";
head.appendChild(js);
window.dataLayer = window.dataLayer || [];
function gtag(){dataLayer.push(arguments);}
gtag('js', new Date());
gtag('config', 'G-6MXW7NJY01');
gtag('event', 'page_view', {
    page_title: document.title,
    page_location: window.location.href,
    page_path: window.location.pathname
  })

    } else {
        document.getElementById('cookie-notice').style.display = 'block';
    }
    document.getElementById('cookie-notice-accept').addEventListener("click",function() {
        createCookie('cookie-notice-dismissed','true',31);
        document.getElementById('cookie-notice').style.display = 'none';
        location.reload();
    });

</script>

<div id='footer'>
  <div class='spacing'></div>
  <div class='row'>
    <div class='large-3 medium-3 columns'>
      <h1>
        <a href='/index.html'>
          <img alt="" src="https://www.catalogueoflife.org/images/col_square_logo.jpg" />
        </a>
      </h1>

    </div>
    <div class='large-3 medium-3 columns'>
      <div class='links'>
        <h4>Links</h4>
        <ul>
          <li><a href="/about/colusage#col-api">COL API</a></li>
          <li><a href="/data/browse">Browse the COL Checklist</a></li>
          <li><a href="https://www.checklistbank.org/">ChecklistBank</a></li>
        </ul>
      <div class='spacing'></div>
      <ul class='socials'>
        <li>
          <a href='https://www.facebook.com/CatalogueOfLife/'>
            <i class='fa fa-facebook'></i>
          </a>
        </li>
        <li>
          <a href='https://twitter.com/catalogueoflife'>
            <i class='fa fa-twitter'></i>
          </a>
        </li>
      </ul>
      </div>
      <div class='spacing'></div>
    </div>
    <div class='large-3 medium-3 columns'>
      <div class='links'>
        <h4>Recent posts</h4>
        <ul>

            <li><a href="/2022/08/15/editor-vacancy">Vacancy - Global species catalogue editor</a></li>

            <li><a href="/2022/08/15/archive-repository">Data repository</a></li>

            <li><a href="/2022/07/12/release">Monthly Release July 2022</a></li>

            <li><a href="/2022/06/23/release">Monthly Release June 2022</a></li>

            <li><a href="/2022/05/20/release">Monthly Release May 2022</a></li>

        </ul>
      </div>
      <div class='spacing'></div>
    </div>
    <div class='large-3 medium-3 columns'>
      <h4>Contact us</h4>
      <ul>
        <li>Phone: <a href="tel:+31 (0)71 7519 362">+31 (0)71 7519 362</a></li>
        <li>Email: <a href="mailto:contact@catalogueoflife.org">contact@catalogueoflife.org</a></li>
        <li>Species 2000 Secretariat, Naturalis, Darwinweg 2, 2333 CR Leiden, The Netherlands</li>
      </ul>
      <div class='spacing'></div>
    </div>
  </div>
  <div class='creativecommons'>
    <p>COL Checklist 2022-07-12  <a href="https://doi.org/10.48580/dfpz">doi:10.48580/dfpz</a><br>
      © 2020, Species 2000. This online database is copyrighted by Species 2000 on behalf of the Catalogue of Life partners.<br>
      Unless otherwise indicated, all other content offered under <a rel="license" href="http://creativecommons.org/licenses/by/4.0/">Creative Commons Attribution 4.0 International License</a>,
      Catalogue of Life, <a href="/data/metadata">2022-07-12</a>.
    </p>
  </div>
  <div class='spacing'></div>
  <div class='creativecommons'>
    <h4>Disclaimer</h4>
    <p>
      The Catalogue of Life cannot guarantee the accuracy or completeness of the information in the COL Checklist. <br>Be aware that the COL Checklist is still incomplete and undoubtedly contains errors. <br>Neither Catalogue of Life, Species 2000 nor any contributing database can be made liable for any direct or indirect damage arising out of the use of Catalogue of Life services.
    </p>
  </div>
  <div class='spacing'></div>
</div>
<script src="/javascripts/jquery.countTo.js" type="text/javascript"></script>
<script src="/javascripts/jquery.appear.js" type="text/javascript"></script>
<script src="/javascripts/jquery.validate.js" type="text/javascript"></script>
<script src="/javascripts/jquery.sequence-min.js" type="text/javascript"></script>
<script src="/javascripts/jquery.easing.1.3.js" type="text/javascript"></script>
<script src="/javascripts/app.js" type="text/javascript"></script>


  </body>

</html>
