/* Release metadata, rendered from the keyless /api/dataset the bundle rewrites
   to its single release - so this page needs no build time substitution. */
(function () {
  'use strict';
  var b = window.colBundle;

  function row(label, html) {
    return html ? '<tr><th>' + label + '</th><td>' + html + '</td></tr>' : '';
  }

  // A COL release credits thousands of creators. Listing them all buries every other
  // field on the page, so show a sample and say how many were left out.
  var MAX_PEOPLE = 12;

  function people(list) {
    if (!list || !list.length) return '';
    var names = list.map(function (p) {
      return b.escape(p.name || [p.given, p.family].filter(Boolean).join(' '));
    }).filter(Boolean);
    if (names.length <= MAX_PEOPLE) return names.join(', ');
    return names.slice(0, MAX_PEOPLE).join(', ') +
      ' <em>and ' + (names.length - MAX_PEOPLE).toLocaleString() + ' more</em>';
  }

  function link(href, text) {
    return '<a href="' + b.escape(href) + '">' + b.escape(text || href) + '</a>';
  }

  /* Dataset descriptions are markdown. Rather than pull in a parser for one field,
     turn the only construct COL actually uses - inline links - into anchors, and
     leave everything else as the plain text it already reads as. Runs on already
     escaped text, so the url is matched in its escaped form and re-escaped on the
     way out; only http(s) links become anchors. */
  function mdLinks(escaped) {
    return escaped.replace(/\[([^\]]+)\]\((https?:&#x2F;&#x2F;[^)\s]+|https?:\/\/[^)\s]+)\)/g,
      function (_, text, url) {
        return '<a href="' + url.replace(/&#x2F;/g, '/') + '">' + text + '</a>';
      });
  }

  b.release.then(function (d) {
    var html = '<table class="about-table"><tbody>' +
      row('Title', b.escape(d.title)) +
      row('Alias', b.escape(d.alias)) +
      row('Version', b.escape(d.version)) +
      row('Issued', b.escape(d.issued)) +
      row('DOI', d.doi ? link('https://doi.org/' + d.doi, d.doi) : '') +
      row('Dataset key', String(d.key)) +
      row('License', b.escape(d.license)) +
      row('Origin', b.escape(d.origin)) +
      row('Confidence', d.confidence != null ? String(d.confidence) : '') +
      row('Editors', people(d.editor)) +
      row('Creators', people(d.creator)) +
      row('Contact', d.contact ? b.escape(d.contact.name || d.contact.email || '') : '') +
      row('Homepage', d.url ? link(d.url) : '') +
      row('ChecklistBank', link('https://www.checklistbank.org/dataset/' + d.key + '/about',
                                'dataset ' + d.key + ' on checklistbank.org')) +
      '</tbody></table>' +
      (d.description ? '<h3>Description</h3><p>' + mdLinks(b.escape(d.description)) + '</p>' : '') +
      '<h3>Citation</h3><p>' + b.escape(d.citation || '') + '</p>' +
      '<p>' + link(b.api + 'dataset/' + d.key + '.bib', 'Download the BibTeX citation') + '</p>';

    document.getElementById('about').innerHTML = html;
    // d.title is "Catalogue of Life" itself, so the alias is what identifies the release
    document.title = 'About ' + (d.alias || d.version || 'this release') + ' | Catalogue of Life';
  }).catch(function (e) {
    b.fail('#about', e);
  });
})();
