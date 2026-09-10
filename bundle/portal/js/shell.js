/*
 * Shared chrome and col-browser wiring for the CLB bundle mini portal.
 *
 * Every page loads col-browser's UMD bundle, then this file, then its own small
 * mount script. The chrome (header, footer) is rendered here rather than copied
 * into eight HTML files - there is no SEO to serve on a localhost bundle, so
 * building it client side costs nothing and keeps it in one place.
 *
 * The release this bundle serves is discovered at runtime: SingleDatasetRewriteFilter
 * rewrites the keyless `/dataset` to `/dataset/{releaseKey}`, so one call yields the
 * dataset key every component needs plus the metadata the footer and /about show.
 * That is what keeps these files release agnostic and shippable as one image.
 */
(function () {
  'use strict';

  var API = '/api/';
  // Where the components' internal links point. Flat at the root: nginx proxies
  // only /api/, so a /taxon/{id} page can never collide with the /taxon/{id} API.
  var PATHS = { taxon: '/taxon/', tree: '/', search: '/search', source: '/dataset/' };
  // antd token override, same as the real portal (near square corners).
  var THEME = { token: { borderRadius: 2 } };

  var NAV = [
    { href: '/', label: 'Browse' },
    { href: '/search', label: 'Search' },
    { href: '/about', label: 'About' },
    { href: '/sources', label: 'Sources' },
    { href: '/metrics', label: 'Metrics' },
    { href: '/matching', label: 'Matching' }
  ];

  var COL_SITE = 'https://www.catalogueoflife.org';
  var CLB_SITE = 'https://www.checklistbank.org';

  function el(html) {
    var t = document.createElement('template');
    t.innerHTML = html.trim();
    return t.content.firstChild;
  }

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  /* ---------------------------------------------------------------- chrome */

  function renderHeader() {
    var active = document.body.getAttribute('data-page') || '/';
    var items = NAV.map(function (n) {
      var cls = n.href === active ? ' class="active"' : '';
      return '<li' + cls + '><a href="' + n.href + '">' + n.label + '</a></li>';
    }).join('');

    var host = document.getElementById('site-header');
    if (!host) return;
    host.appendChild(el(
      '<div class="contain-to-grid sticky">' +
        '<nav class="top-bar" data-options="sticky_on: large" data-topbar="">' +
          '<ul class="title-area">' +
            '<li class="name"><h1><a href="/">' +
              '<img alt="Catalogue of Life" class="col-header-logo" src="/images/logos/col_logo.svg" />' +
            '</a></h1></li>' +
            '<li class="toggle-topbar menu-icon"><a href="#">Menu</a></li>' +
          '</ul>' +
          '<section class="top-bar-section"><ul class="right">' + items + '</ul></section>' +
        '</nav>' +
      '</div>'
    ));

    // Foundation's own top-bar JS is not loaded; desktop needs none (CSS hover)
    // and this is the small-screen toggle the portal also hand rolls.
    document.addEventListener('click', function (event) {
      if (event.target.closest && event.target.closest('.toggle-topbar')) {
        event.preventDefault();
        var bar = document.querySelector('.top-bar');
        if (bar) bar.classList.toggle('expanded');
      }
    });
  }

  function renderFooter() {
    var host = document.getElementById('site-footer');
    if (!host) return;
    host.appendChild(el(
      '<div id="footer">' +
        '<div class="footer-main">' +
          '<div class="footer-col footer-version">' +
            '<h4>Version</h4>' +
            '<ul>' +
              '<li><a href="/about" id="footer-version">&nbsp;</a></li>' +
              '<li>Issued: <i id="footer-issued"></i></li>' +
              '<li id="footer-doi-row" hidden>DOI: <a id="footer-doi"></a></li>' +
              '<li>ChecklistBank: <a id="footer-key"></a></li>' +
            '</ul>' +
          '</div>' +
          '<a href="/" class="footer-logo-link" aria-label="Catalogue of Life - home">' +
            '<img class="footer-square-logo" alt="Catalogue of Life" src="/images/logos/col_square_logo.jpg" />' +
          '</a>' +
          '<div class="footer-col footer-brand">' +
            '<h4>Catalogue of Life</h4>' +
            '<ul class="footer-links">' +
              '<li><a href="' + COL_SITE + '">catalogueoflife.org</a></li>' +
              '<li><a href="' + CLB_SITE + '">checklistbank.org</a></li>' +
            '</ul>' +
            '<div id="GCBR">' +
              '<img alt="" src="/images/logos/GCBR-Logo-White.svg" class="cbdr-logo" />' +
              '<div class="gcbr-text">is a Global Core Biodata Resource</div>' +
            '</div>' +
          '</div>' +
        '</div>' +
        '<div class="creativecommons"><p>' +
          '&copy; ' + new Date().getFullYear() + ', Catalogue of Life.<br />' +
          'Unless otherwise indicated, all other content offered under ' +
          '<a rel="license" href="http://creativecommons.org/licenses/by/4.0/">' +
          'Creative Commons Attribution 4.0 International License</a>.' +
        '</p></div>' +
        '<div class="creativecommons footer-disclaimer">' +
          '<h4>Disclaimer</h4>' +
          '<p>The Catalogue of Life cannot guarantee the accuracy or completeness of the information in the ' +
          'Catalogue of Life.<br />Be aware that the Catalogue of Life is still incomplete and undoubtedly ' +
          'contains errors.<br />Catalogue of Life, nor any contributing database can be made liable for any ' +
          'direct or indirect damage arising out of the use of Catalogue of Life services.</p>' +
        '</div>' +
      '</div>'
    ));
  }

  function fillFooter(d) {
    function set(id, text) {
      var n = document.getElementById(id);
      if (n) n.textContent = text || '';
    }
    set('footer-version', d.version || d.alias || String(d.key));
    set('footer-issued', d.issued || '');
    var keyLink = document.getElementById('footer-key');
    if (keyLink) {
      keyLink.textContent = String(d.key);
      keyLink.setAttribute('href', CLB_SITE + '/dataset/' + d.key + '/about');
    }
    if (d.doi) {
      var row = document.getElementById('footer-doi-row');
      var doi = document.getElementById('footer-doi');
      if (row) row.hidden = false;
      if (doi) {
        doi.textContent = d.doi;
        doi.setAttribute('href', 'https://doi.org/' + d.doi);
      }
    }
  }

  /* ------------------------------------------------------------- bootstrap */

  ColBrowser.configure({ dataApi: API });

  var release = fetch(API + 'dataset', { headers: { Accept: 'application/json' } })
    .then(function (r) {
      if (!r.ok) throw new Error('GET /api/dataset returned ' + r.status);
      return r.json();
    });

  function fail(selector, err) {
    var host = document.querySelector(selector);
    if (host) {
      host.innerHTML = '<div class="bundle-error"><h4>This page could not load</h4><p>' +
        esc(err && err.message ? err.message : String(err)) +
        '</p><p>Is the bundle API reachable at <code>/api/</code>?</p></div>';
    }
    if (window.console) console.error(err);
  }

  window.colBundle = {
    api: API,
    paths: PATHS,
    theme: THEME,
    release: release,
    escape: esc,

    /**
     * Mount one col-browser component, once the release key is known.
     * `kind` is a withRouting kind, or null for a component that needs no URL wiring.
     */
    mount: function (selector, Component, kind, props) {
      return release.then(function (d) {
        var C = kind
          ? ColBrowser.withRouting(Component, {
              kind: kind,
              mode: 'path',
              // Static multi page host: every cross page link must actually load
              // the new page, not just push a history entry.
              navigation: 'reload',
              paths: PATHS
            })
          : Component;
        var all = Object.assign({ datasetKey: String(d.key), theme: THEME }, props || {});
        ColBrowser.ReactDOM
          .createRoot(document.querySelector(selector))
          .render(ColBrowser.React.createElement(C, all));
        return d;
      }).catch(function (e) {
        fail(selector, e);
        throw e;
      });
    },

    fail: fail
  };

  renderHeader();
  renderFooter();
  release.then(fillFooter).catch(function (e) {
    if (window.console) console.error('Could not read the bundled release', e);
  });
})();
