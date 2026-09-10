/* Source dataset detail. withRouting reads the key from /dataset/{key}. */
(function () {
  'use strict';
  var b = window.colBundle;

  b.mount('#dataset', ColBrowser.SourceDataset, 'source', {
    pageTitleTemplate: '__dataset__ | Catalogue of Life'
  });

  // The BibTex icon needs the same source key, which withRouting derives from the path.
  b.mount('#bibtex', ColBrowser.BibTex, 'bibtex', {});
})();
