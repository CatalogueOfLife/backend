/* Taxon detail. withRouting reads the id from /taxon/{id}. */
window.colBundle.mount('#taxon', ColBrowser.Taxon, 'taxon', {
  pageTitleTemplate: '__taxon__ | Catalogue of Life',
  showDistributionMap: true,
  // COL identifiers under the GBIF backbone checklist, so the occurrence overlay
  // resolves. Needs the internet; the map degrades to the polygons without it.
  gbifChecklistKey: '7ddf754f-d193-4cc9-b351-99906754a03b'
});
