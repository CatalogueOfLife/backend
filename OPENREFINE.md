# OpenRefine integration

ChecklistBank exposes its **name matcher** and **name parsers** to
[OpenRefine](https://openrefine.org) so that taxonomists can clean and enrich tabular name data
without writing code.

OpenRefine has two native extension points, and CoL maps onto both:

| OpenRefine feature | CoL backend | What you get |
|---|---|---|
| **Reconciliation Service** | the name matcher (`UsageMatcher` / name index) | match a column of scientific names to CoL taxa, with scores and auto-match |
| **Data Extension** | the matched taxon + its classification | pull authorship, rank, status, names-index id and higher ranks as new columns |
| *Add column by fetching URLs* | the `/parser/*` REST endpoints | run any CoL parser (name, date, rank, country, …) over a column |

## 1. Reconciling scientific names

The reconciliation endpoint speaks the
[Reconciliation Service API v0.2](https://reconciliation-api.github.io/specs/0.2/). It is driven by
the name matcher (not autocomplete).

| Endpoint | Purpose |
|---|---|
| `GET  /dataset/{key}/reconcile` | service manifest (what OpenRefine fetches first) |
| `POST /dataset/{key}/reconcile` | batch reconciliation (`queries` form field or JSON body) |
| `POST /dataset/{key}/reconcile/extend` | data extension |
| `GET  /dataset/{key}/reconcile/extend/propose` | list of extension properties |
| `GET  /dataset/{key}/reconcile/suggest/entity?prefix=` | taxon autocomplete |
| `GET  /dataset/{key}/reconcile/suggest/property?prefix=` | extension-property autocomplete |
| `…/reconcile` (no `/dataset/{key}`) | same service against the **COL backbone** default |

CORS is enabled, so modern OpenRefine (3.x+) talks to it directly — no JSONP needed.

### Add the service in OpenRefine

1. On a column choose **Reconcile → Start reconciling… → Add Standard Service…**
2. Paste the service URL:
   - COL backbone: `https://api.checklistbank.org/reconcile`
     (always the latest COL extended release, resolved per request)
   - a specific dataset/release: `https://api.checklistbank.org/dataset/{key}/reconcile`

   `{key}` accepts the usual dataset-key aliases, e.g. `3LXR` (latest COL extended release),
   `3LR` (latest normal release), `COL2024`, or `gbif-<uuid>` — these are rewritten to the real
   release key automatically.
3. Reconcile the column. Exact hits (`matchType=EXACT`) auto-match; ambiguous/variant/canonical
   hits are offered as a candidate list to pick from.

### Improving matches with property hints

When configuring reconciliation you can map other columns to **properties** that are passed to the
matcher as hints. Recognised property ids:

- `authorship`, `rank`, `code` (nomenclatural code)
- any rank name used as a classification hint, e.g. `kingdom`, `family`, `genus`

### Pulling extra columns (data extension)

After reconciling, use **Edit column → Add columns from reconciled values…** and pick from:

`scientificName`, `authorship`, `rank`, `status`, `nidx` (names-index id),
`kingdom`, `phylum`, `class`, `order`, `family`, `genus`.

### Quick smoke test with curl

```bash
# manifest
curl 'https://api.checklistbank.org/reconcile'

# reconcile a batch (form encoded, as OpenRefine sends it)
curl -X POST 'https://api.checklistbank.org/reconcile' \
  --data-urlencode 'queries={"q0":{"query":"Puma concolor"},"q1":{"query":"Aus bus","properties":[{"pid":"rank","v":"species"}]}}'

# data extension
curl -X POST 'https://api.checklistbank.org/reconcile/extend' \
  -H 'Content-Type: application/json' \
  -d '{"ids":["<usageId>"],"properties":[{"id":"authorship"},{"id":"family"}]}'
```

## 2. Running parsers over a column

Every CoL parser is already a plain REST endpoint, so OpenRefine can call it per cell with
**Edit column → Add column by fetching URLs…**.

### Scientific names

GREL URL expression (URL-encode the cell value):

```
"https://api.checklistbank.org/parser/name?q=" + escape(value, "url")
```

The response is a JSON array; parse the first element and extract atoms, e.g. in a follow-up
**Add column based on this column** with GREL:

```
value.parseJson()[0].name.specificEpithet
```

Useful fields under `name`: `genus`, `specificEpithet`, `infraspecificEpithet`, `rank`,
`combinationAuthorship`, `basionymAuthorship`; plus `issues` on the array element.

### Typed value parsers

`GET /parser/{type}?q={{value}}` returns `[{"original":…, "parsed":…, "parsable":…}]`. Available
types include:

`boolean`, `country`, `datasettype`, `date`, `distributionstatus`, `gazetteer`, `geotime`,
`integer`, `language`, `license`, `lifezone`, `mediatype`, `nomcode`, `nomreltype`, `nomstatus`,
`rank`, `referencetype`, `sex`, `taxonomicstatus`, `treatmentformat`, `typestatus`, `uri`.

Example — normalise a free-text rank column:

```
"https://api.checklistbank.org/parser/rank?q=" + escape(value, "url")
```

then extract with `value.parseJson()[0].parsed`.

`GET /parser` returns the full list of parser type names for discovery.

> Tip: URL-fetching is one request per row. For large tables, set a throttle delay in the
> *Add column by fetching URLs* dialog. For names specifically, prefer **reconciliation + data
> extension** (one batched request per ~10 rows) over per-row parsing.

## Implementation notes

- Adapter code: `webservice/.../resources/matching/openrefine/`
  (`OpenRefineModel`, `OpenRefineMapper`, `AbstractReconciliationResource`,
  `ReconciliationResource`, `DefaultReconciliationResource`).
- Reconciliation reuses the exact matcher path of `/dataset/{key}/match/nameusage`
  (`AbstractMatchingJob.interpretAndMatch`); only the request/response shape differs.
- Registered next to `NameUsageMatchingResource` in `WsServer`. Exposing it on the dedicated
  read-only / matching server is a possible follow-up.
