/* Metrics: a rank breakdown of the release plus a few headline totals.
   The totals come from search facets rather than dataset_import, because a
   release carries its metrics under the project key and its own import row
   need not exist in the bundled database. */
(function () {
  'use strict';
  var b = window.colBundle;

  var TOTALS = [
    { label: 'Accepted species', query: 'rank=species&status=accepted&status=provisionally%20accepted' },
    { label: 'Genera',           query: 'rank=genus&status=accepted&status=provisionally%20accepted' },
    { label: 'Families',         query: 'rank=family&status=accepted&status=provisionally%20accepted' },
    { label: 'All names',        query: '' }
  ];

  b.mount('#breakdown', ColBrowser.TaxonBreakdown, 'taxonBreakdown', {
    taxonId: 'CS5HF', // Eukaryota, as on the portal's metrics page
    level: 2,
    showLevelSwitch: true
  });

  b.release.then(function (d) {
    var host = document.getElementById('totals');
    if (!host) return;

    host.className = 'stat-row';
    TOTALS.forEach(function (t) {
      var col = document.createElement('div');
      col.className = 'stat-tile';
      col.innerHTML = '<span class="stat-value"></span><span class="stat-label">' +
        b.escape(t.label) + '</span>';
      host.appendChild(col);

      var n = col.querySelector('.stat-value');
      n.textContent = '…';
      fetch(b.api + 'dataset/' + d.key + '/nameusage/search?limit=0&' + t.query,
            { headers: { Accept: 'application/json' } })
        .then(function (r) { return r.ok ? r.json() : null; })
        .then(function (j) {
          n.textContent = j && typeof j.total === 'number' ? j.total.toLocaleString() : '–';
        })
        .catch(function () { n.textContent = '–'; });
    });
  });
})();
