<!DOCTYPE html>
<html>

  <head>
  <meta charset="utf-8">
  <meta http-equiv="X-UA-Compatible" content="IE=edge">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="DC.identifier" scheme="DCTERMS.URI" content="urn:ISSN:2405-8858" />

    <!-- Begin Jekyll SEO tag v2.6.1 -->
<title>Not found | COL</title>
<meta name="generator" content="Jekyll v4.1.1" />
<meta property="og:title" content="Not found" />
<meta property="og:locale" content="en_US" />
<meta name="description" content="The Catalogue of Life" />
<meta property="og:description" content="The Catalogue of Life" />
<link rel="canonical" href="http://localhost:4000/404" />
<meta property="og:url" content="http://localhost:4000/404" />
<meta property="og:site_name" content="COL" />
<meta name="twitter:card" content="summary" />
<meta property="twitter:title" content="Not found" />
<meta name="twitter:site" content="@catalogueoflife" />
<script type="application/ld+json">
{"url":"http://localhost:4000/404","headline":"Not found","description":"The Catalogue of Life","publisher":{"@type":"Organization","logo":{"@type":"ImageObject","url":"http://localhost:4000/images/col_square_logo.jpg"}},"@type":"WebPage","@context":"https://schema.org"}</script>
<!-- End Jekyll SEO tag -->




  <meta name="robots" content="noindex">


  <link rel="stylesheet" href="/css/foundation.css">
  <link rel="stylesheet" href="/css/style.css">
  <link rel="stylesheet" href="/css/fontello.css">
  <!--
  <link rel="stylesheet" href="/css/font-awesome.css">
  -->
  <link rel="stylesheet" href="/css/custom.css">

  <link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/CatalogueOfLife/portal-components@v1.1.0/umd/main.css">
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
  <link rel="alternate" type="application/rss+xml" title="COL" href="http://localhost:4000/feed.xml" />
  <script src="https://unpkg.com/react@16/umd/react.production.min.js" ></script>
  <script src="https://unpkg.com/react-dom@16/umd/react-dom.production.min.js" ></script>
  <script src="https://cdn.jsdelivr.net/gh/CatalogueOfLife/portal-components@v1.1.0/umd/col-browser.min.js" ></script>
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





            <li class="has-dropdown ">
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
xhr.open('GET', 'https://api.catalogueoflife.org/admin/settings');
xhr.send();
}
getHealth()
setInterval(getHealth, interval);

</script>

      <div class='full parallax' style='position: relative; background-image: url(/images/species/Raphus_cucullatus.jpg); color: #fff;'>
  <div class='row'>
    <div class='large-12 columns'>
      <div class="mod modSectionHeader big">
  <div class="special-title centered-text">
    <h2 style="color: #fff;">Not found</h2>
  </div>

    <h3 class='centered-text' style='color: #fff'>This page never existed or was removed</h3>

</div>
    </div>
  </div>
  <div class='four spacing'></div>
  <div class="caption caption-absolute">

      <p><em>Raphus cucullatus</em> (Linnaeus, 1758) - <a href="https://mczbase.mcz.harvard.edu/MediaSet.cfm?media_id=74781">Photo CC By Museum of Comparative Zoology, Harvard University</a></p>

    </div>
</div>


<div class='full'>
  <div class='row'>

    <div class='large-12 columns prose'>

      <div class="row" style="padding-top: 40px;">
  <div class="small-2 large-4 columns dodo" style="text-align: right">
      <img src="/images/dodo.png" alt="Dodo" style="width: 160px" />
  </div>

  <div class="small-4 large-4 columns">
   	<p style="padding-top: 60px;">This page might have existed once but we can no longer find it.
       Please try our <a href="/data/search">search</a> to find the taxa you are looking for.
	   You are always welcome to contact us at <a href="mailto:support@catalogueoflife.org">support@catalogueoflife.org</a>.
   	</p>

  </div>

  <div class="small-6 large-4 columns">

  </div>
</div>


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
          <img alt="" src="http://localhost:4000/images/col_square_logo.jpg" />
        </a>
      </h1>

    </div>
    <div class='large-3 medium-3 columns'>
      <div class='links'>
        <h4>Links</h4>
        <ul>
          <li><a href="/about/colusage#col-api">COL API</a></li>
          <li><a href="/data/browse">Browse the COL Checklist</a></li>
          <li><a href="https://data.catalogueoflife.org/">COL ChecklistBank</a></li>
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

            <li><a href="/2021/11/09/release">Monthly Release November 2021</a></li>

            <li><a href="/2021/11/04/20years">20 years Catalogue of Life</a></li>

            <li><a href="/2021/10/18/release">Monthly Release October 2021</a></li>

            <li><a href="/2021/09/21/release">Monthly Release September 2021</a></li>

            <li><a href="/2021/08/25/release">Monthly Release August 2021</a></li>

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
    <p>COL Checklist 2021-11-09  <a href="https://doi.org/10.48580/d4t4">doi:10.48580/d4t4</a><br>
      © 2020, Species 2000. This online database is copyrighted by Species 2000 on behalf of the Catalogue of Life partners.<br>
      Unless otherwise indicated, all other content offered under <a rel="license" href="http://creativecommons.org/licenses/by/4.0/">Creative Commons Attribution 4.0 International License</a>,
      Catalogue of Life, <a href="/data/metadata">2021-11-09</a>.
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
