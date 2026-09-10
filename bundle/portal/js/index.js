/* Homepage: kingdom tiles, the classification tree and the release version panel. */
(function () {
  'use strict';

  // Kingdom tiles, copied from the portal's src/data/milestones.ts. These taxon ids
  // are stable COL ids - sound here because the mini portal only ever ships with a
  // COL release (BundleBuildCmd omits it for any other project).
  var SPECIES = 'limit=0&rank=species&status=accepted&status=provisionally%20accepted';
  var MILESTONES = [
    { image: 'Podarcis.jpg',     title: 'Animalia',       bg: '0480b5', fg: 'ccedfa', id: 'N', query: 'TAXON_ID=N' },
    { image: 'Pultenaea.jpg',    title: 'Plantae',        bg: 'd0bd34', fg: 'fcfbe7', id: 'P', query: 'TAXON_ID=P' },
    { image: 'Teloschistes.jpg', title: 'Fungi',          bg: 'e83143', fg: 'feebed', id: 'F', query: 'TAXON_ID=F' },
    // "Other kingdoms" links to Chromista as its representative taxon (Protozoa's
    // page renders empty) but counts the whole remaining set.
    { image: 'Protozoa.jpg',     title: 'Other kingdoms', bg: '2db261', fg: 'ecf7ef', id: 'C',
      query: 'TAXON_ID=V&TAXON_ID=R&TAXON_ID=B&TAXON_ID=C&TAXON_ID=Z' }
  ];

  var b = window.colBundle;

  b.mount('#tree', ColBrowser.Tree, 'tree', {
    defaultTaxonKey: 'CS5HF', // Eukaryota
    showTreeOptions: true,
    linkToSpeciesPage: true,
    type: 'project'
  });

  b.release.then(function (d) {
    renderVersion(d);
    renderTiles(d);
  });

  function renderVersion(d) {
    var alias = document.getElementById('version-alias');
    if (alias && d.alias) alias.innerHTML = 'Release: <i>' + b.escape(d.alias) + '</i><br>';
    var issued = document.getElementById('version-issued');
    if (issued) issued.textContent = d.issued || '';
    // A release without a DOI hides the whole row rather than showing an empty link.
    var doi = document.getElementById('version-doi');
    var doiRow = document.getElementById('version-doi-row');
    if (doi && doiRow && d.doi) {
      doi.textContent = d.doi;
      doi.href = 'https://doi.org/' + d.doi;
      doiRow.hidden = false;
    }
    var bib = document.getElementById('version-bibtex');
    if (bib) bib.href = b.api + 'dataset/' + d.key + '.bib';
  }

  function renderTiles(d) {
    var host = document.getElementById('kingdoms');
    if (!host) return;

    MILESTONES.forEach(function (m) {
      var a = document.createElement('a');
      a.href = '/taxon/' + m.id;
      a.innerHTML =
        '<div class="small-3 small-3 columns" style="background-color: #fff;">' +
          '<div class="mod modMilestone" style="background-image: url(/images/kingdoms/' + m.image + '); background-size: cover;">' +
            '<div class="milestoneText" style="background-color: #' + m.bg + '; color: #' + m.fg + ';">' +
              '<div class="milestoneTitle">' + b.escape(m.title) + '</div>' +
              '<div class="milestoneCount"><span></span> species in COL</div>' +
            '</div>' +
          '</div>' +
        '</div>';
      host.appendChild(a);

      var count = a.querySelector('.milestoneCount span');
      // Counts come from the search totals, exactly as the portal computes them -
      // only at page load here rather than at build time.
      fetch(b.api + 'dataset/' + d.key + '/nameusage/search?' + m.query + '&' + SPECIES,
            { headers: { Accept: 'application/json' } })
        .then(function (r) { return r.ok ? r.json() : null; })
        .then(function (j) {
          if (j && typeof j.total === 'number') count.textContent = j.total.toLocaleString();
        })
        .catch(function () { /* a missing count must not break the tile */ });
    });
  }
})();
