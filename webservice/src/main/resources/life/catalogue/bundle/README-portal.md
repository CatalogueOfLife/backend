
## The mini portal

This bundle also ships a small Catalogue of Life website for browsing this one release:

  http://localhost/

Browse the classification, search, read taxon and source dataset pages, see the metrics, and match a
CSV or TSV of names against this release - all served by the API next to it, with no call to
checklistbank.org for the taxonomic data. Set `CLB_PORTAL_PORT` if port 80 is already taken, and
`CLB_PORTAL_IMAGE` to run a self built portal image.

The portal loads its UI components and the distribution map's basemap from public CDNs, so those
parts need internet even though the data does not. Everything else works offline.
