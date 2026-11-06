# Facets in the query context, and the ES client that replayed slow searches

Date: 2026-11-06
Status: implemented on `feat/es-facets`, not yet deployed. `es.socketTimeout` in the prod read only config and the
Varnish, ES replica and client changes listed under non goals go with it.

## Problem

On 2026-11-05 and 2026-11-06 Nagios flapped on the COL API health check. Every Elasticsearch backed request (search,
suggest) took 30 to 120 s or failed with a 500 while Postgres backed requests answered in 60 ms. The cluster
`col-prod-es9` was green but CPU bound: every data node had its search pool fully active, two of them had queues of
168 and 62 and 455 search tasks were in flight. Hot threads were all in `GlobalAggregator`.

The `api_logs` table showed the trigger: a crawler rotating Windows and Mac Chrome user agents, rendering ChecklistBank
name search and taxon pages in a headless browser, which is how it walks through the go-away challenge. In every hour
it sent more than about 12k faceted searches ES collapsed; in every hour it stayed under 9k there were no errors, even
with the steady headless `X11 Linux Chrome` client at 13k an hour and the portal SSR route on top. The retired annual
checklist links redirected by Varnish to `www.checklistbank.org/dataset/<key>/names?q=…` and `/taxon/<id>` feed it:
the name search page fires a search with rank, issue and status facets, the taxon page a `TAXON_ID` rank facet.

Three things in the backend turned that traffic into a collapse.

1. **Every facet was a global aggregation.** `FacetsTranslator` wrapped all facets in `global -> filter(context) ->
   filter(facet) -> terms`, whatever the request. A global aggregation visits every document of every shard, 24M on
   each of the 16 `clbprod` shards, before the context filter throws most of them away. The class comment called a
   facet without its own filter an unlikely edge case that wilfully pays for a uniform response shape. It is the only
   request shape the taxon pages send.
2. **The ES client replayed slow searches.** `EsClientFactory` wired `EsConfig.socketTimeout` (900 s) to the
   *response* timeout only, so the rest5 client's 30 s default socket timeout stayed in force. After 30 s without a
   byte the client marks the node dead and replays the request on the next node, which is why durations clustered at
   30, 60 and 121 s, why the load doubled under pressure and why two nodes carried all the queue while the others had
   none.
3. **A pool of 30 connections** (`maxConnPerRoute 10`, `maxConnTotal 30`) caps the searches in flight. The 650 other
   Jetty threads waited for a lease, the health check's `_count` among them. This one stays: it is the only back
   pressure left in front of ES.

## Decision

- A facet is aggregated in the query context, as a top level terms aggregation named after the facet parameter, unless
  the request filters on that same parameter and does not set `facetIncludeSelf`. Only then does a facet have to ignore
  its own filter, which needs the global context, and then all facets of the request keep the old global shape.
  `SearchResponseConverter` reads both shapes. The facet counts are identical either way; the integration test
  `NameUsageSearchServiceEsIT` asserts them for the taxon page request and for `facetIncludeSelf`.
- `EsClientFactory.configureConnection` sets the socket timeout from `EsConfig.socketTimeout` next to the connect
  timeout; `configureRequest` keeps it as the response timeout. The read only server can run a shorter value than the
  main server, which also bulk indexes (`es.socketTimeout: 120000` in `configs/prod/config-ro.yml`).

## Non goals, handled outside this change

- Blocking or throttling the crawler in Varnish, and a generic per address throttle on `nameusage/(search|suggest)`.
- The missing `clbprod` replica (`numReplicas: 1` in the config, 0 on the cluster after the last reindex).
- The clients: portal-components and the ChecklistBank taxon page should read `/dataset/{key}/taxon/{id}/metrics`
  instead of asking ES to count a subtree by rank; the portal SSR taxon route fires an exact name search per page view.
- The health check still does a `_count`, so it keeps reflecting a saturated search pool. That is what Nagios should
  see.

## Rejected

- Raising the connection pool. It only moves the queue from Jetty onto ES, which is the component that is out of CPU.
- An ES side search `timeout`. It returns partial results with `timed_out: true`, which the API would serve as wrong
  counts without saying so.
- Dropping the global context altogether. Multi select facets on the name search page rely on counts that ignore the
  facet's own filter; those requests are rare and human driven.
