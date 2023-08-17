<!DOCTYPE html>
<html>

  <head>
  <meta charset="utf-8">
  <meta http-equiv="X-UA-Compatible" content="IE=edge">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="DC.identifier" scheme="DCTERMS.URI" content="urn:ISSN:2405-8858" />

    <!-- Begin Jekyll SEO tag v2.8.0 -->
<title>Deleted | COL</title>
<meta name="generator" content="Jekyll v4.2.2" />
<meta property="og:title" content="Deleted" />
<meta property="og:locale" content="en_US" />
<meta name="description" content="The Catalogue of Life" />
<meta property="og:description" content="The Catalogue of Life" />
<link rel="canonical" href="https://www.catalogueoflife.org/data/tombstone" />
<meta property="og:url" content="https://www.catalogueoflife.org/data/tombstone" />
<meta property="og:site_name" content="COL" />
<meta property="og:type" content="website" />
<meta name="twitter:card" content="summary" />
<meta property="twitter:title" content="Deleted" />
<meta name="twitter:site" content="@catalogueoflife" />
<script type="application/ld+json">
{"@context":"https://schema.org","@type":"WebPage","description":"The Catalogue of Life","headline":"Deleted","publisher":{"@type":"Organization","logo":{"@type":"ImageObject","url":"https://www.catalogueoflife.org/images/col_square_logo.jpg"}},"url":"https://www.catalogueoflife.org/data/tombstone"}</script>
<!-- End Jekyll SEO tag -->




  <meta name="robots" content="noindex">


  <link rel="stylesheet" href="/css/foundation.css">
  <link rel="stylesheet" href="/css/style.css">
  <link rel="stylesheet" href="/css/fontello.css">
  <!--
  <link rel="stylesheet" href="/css/font-awesome.css">
  -->
  <link rel="stylesheet" href="/css/custom.css">

  <link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/CatalogueOfLife/portal-components@v1.2.11/umd/main.css">
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
  <script src="https://cdn.jsdelivr.net/gh/CatalogueOfLife/portal-components@v1.2.11/umd/col-browser.min.js" ></script>
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

      <div class="row" style="padding-top: 40px;">
<div id="taxon">
  <div class="catalogue-of-life" style="padding: 24px; min-height: 280px; margin: 16px 0px; font-size: 12px;">
    <div class="ant-row">
      <div class="ant-col"><h1 style="font-size: 30px; font-weight: 400; padding-left: 10px; display: inline-block; text-transform: none;">${usage.labelHtml}</h1></div>
    </div>
    <div class="ant-row Component-formItem-0-2-1">
      <div class="ant-col Component-smallMargin-0-2-6 ant-col-sm-24 ant-col-md-5" style="">
        <div>
          <dt class="Component-label-0-2-2">COL Identifier</dt>
        </div>
      </div>
      <div class="ant-col Component-smallMargin-0-2-6 ant-col-sm-24 ant-col-md-19" style="">
        <span class="Component-content-0-2-3">${usage.getId()}</span>
      </div>
    </div>
    <div class="ant-row Component-formItem-0-2-1">
      <div class="ant-col Component-smallMargin-0-2-6 ant-col-sm-24 ant-col-md-5" style="">
        <div>
          <dt class="Component-label-0-2-2">History</dt>
        </div>
      </div>
      <div class="ant-col Component-smallMargin-0-2-6 ant-col-sm-24 ant-col-md-19" style="">
        <span class="Component-content-0-2-3">First issued in release <a href="https://www.checklistbank.org/dataset/${first.key?c}/taxon/${usage.getId()}">${first.alias!first.version}</a>.
        Last used in release <a href="https://www.checklistbank.org/dataset/${last.key?c}/taxon/${usage.getId()}">${last.alias!last.version}</a></span>
      </div>
    </div>
    <div class="ant-row Component-formItem-0-2-1">
      <div class="ant-col Component-smallMargin-0-2-6 ant-col-sm-24 ant-col-md-5" style="">
        <div>
          <dt class="Component-label-0-2-2">Name</dt>
        </div>
      </div>
      <div class="ant-col Component-smallMargin-0-2-6 ant-col-sm-24 ant-col-md-19" style="">
        <span class="Component-content-0-2-3">${usage.labelHtml}</span>
      </div>
    </div>
    <div class="ant-row Component-formItem-0-2-1">
      <div class="ant-col Component-smallMargin-0-2-6 ant-col-sm-24 ant-col-md-5" style="">
        <div>
          <dt class="Component-label-0-2-2">Checklist status</dt>
        </div>
      </div>
      <div class="ant-col Component-smallMargin-0-2-6 ant-col-sm-24 ant-col-md-19" style="">
        <span class="Component-content-0-2-3">${usage.status} ${usage.rank}</span>
      </div>
    </div>
    <div class="ant-row Component-formItem-0-2-1">
      <div class="ant-col Component-smallMargin-0-2-6 ant-col-sm-24 ant-col-md-5" style="">
        <div>
          <dt class="Component-label-0-2-2">Classification</dt>
        </div>
      </div>
      <div class="ant-col Component-smallMargin-0-2-6 ant-col-sm-24 ant-col-md-19" style="">
        <span class="Component-content-0-2-3">
          <div style="margin-top: -3px; margin-left: -3px;">
            <#list usage.classification as cl>
            <div style="float: left; margin-right: 3px;">
              <span style="color: rgba(0, 0, 0, 0.45); font-size: 11px;">${cl.rank}: </span>
              <a href="https://www.checklistbank.org/dataset/${last.key?c}/taxon/${cl.id}">${cl.name}</a> &gt;
            </div>
            </#list>
          </div>
        </span>
      </div>
    </div>
   <#if source??>
    <div class="ant-row Component-formItem-0-2-1">
      <div class="ant-col Component-smallMargin-0-2-6 ant-col-sm-24 ant-col-md-5" style="">
        <div>
          <dt class="Component-label-0-2-2">Source dataset</dt>
        </div>
      </div>
      <div class="ant-col Component-smallMargin-0-2-6 ant-col-sm-24 ant-col-md-19" style="">
        <span class="Component-content-0-2-3"><a href="https://www.checklistbank.org/dataset/${source.key}">${source.title}</a></span>
      </div>
    </div>
   </#if>
   <#if usage.link??>
    <div class="ant-row Component-formItem-0-2-1">
      <div class="ant-col Component-smallMargin-0-2-6 ant-col-sm-24 ant-col-md-5" style="">
        <div>
          <dt class="Component-label-0-2-2">Link to original resource</dt>
        </div>
      </div>
      <div class="ant-col Component-smallMargin-0-2-6 ant-col-sm-24 ant-col-md-19" style="">
        <span class="Component-content-0-2-3"><a href="${usage.link}">${usage.link}</a></span>
      </div>
    </div>
   </#if>
  </div>
</div>
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

            <li><a href="/2023/07/18/release">Monthly Release July 2023</a></li>

            <li><a href="/2023/06/27/release">Annual Release 2023</a></li>

            <li><a href="/2023/05/15/release">Monthly Release May 2023</a></li>

            <li><a href="/2023/04/19/release">Monthly Release April 2023</a></li>

            <li><a href="/2023/03/09/release">Monthly Release March 2023</a></li>

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
    <p>COL Checklist 2023-07-18  <a href="https://doi.org/10.48580/dfsy">doi:10.48580/dfsy</a><br>
      © 2020, Species 2000. This online database is copyrighted by Species 2000 on behalf of the Catalogue of Life partners.<br>
      Unless otherwise indicated, all other content offered under <a rel="license" href="http://creativecommons.org/licenses/by/4.0/">Creative Commons Attribution 4.0 International License</a>,
      Catalogue of Life, <a href="/data/metadata">2023-07-18</a>.
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