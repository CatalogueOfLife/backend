# OpenRefine reconciliation services for parsers

Date: 2026-06-24
Status: approved design, ready for implementation plan

## 1. Goal

ChecklistBank already exposes an OpenRefine Reconciliation Service API (spec 0.2) over the
name matcher (`life.catalogue.resources.matching.openrefine`). This project adds equivalent
reconciliation services over the **parsers** that the API exposes through
`ParserResource`, `NameParserResource`, `TaxGroupResource` and the `area` parser, so OpenRefine
users can clean and enrich columns of vocabulary terms, scientific names, geochronological
units, taxonomic groups and area identifiers.

Two distinct values for the user:

- **Reconcile** messy free-text cells to canonical CoL/GBIF values (enum constants, gazetteer
  CURIEs, geotime units, taxonomic groups), with auto-match on a clean parse.
- **Data extension** to pull structured components out of a reconciled cell as new columns
  (parsed name parts, geotime intervals, area metadata, taxgroup classification), with
  per-extend context (`code`, `rank`) where it matters.

## 2. Scope

In scope — reconciliation services for:

- All **controlled-vocabulary parsers** registered in `ParserResource`
  (rank, country, language, license, nomcode, nomstatus, nomreltype, sex, taxonomicstatus,
  typestatus, distributionstatus, mediatype, referencetype, treatmentformat, datasettype,
  gazetteer, …). These are served by the generic resource; `suggest/entity` is offered only for
  the `EnumParser`-backed ones (those whose values are enumerable), while non-enum controlled
  vocabularies still reconcile (id = canonical value) without suggest.
- The **name** parser (`NameParserResource` / `NameParser`).
- The **geotime** parser (`GeoTimeParser` / `GeoTime`).
- The **taxgroup** analyzer (`TaxGroupResource` / `TaxGroupAnalyzer` / `TaxGroup`).
- The **area** parser (`AreaParser` / `Area`).

Out of scope:

- The pure scalar / normalizing parsers (`boolean`, `integer`, `date`, `uri`). Reconciliation
  semantics (an entity catalogue, suggest, stable ids) do not apply; they can be added later if
  ever wanted.
- Any change to the existing taxon reconciliation behaviour. We only extend its shared
  protocol DTOs in backward-compatible ways and factor out a shared query-builder.

## 3. Background: how OpenRefine consumes a reconciliation service

A user registers a service once by its manifest URL. A service has a single `identifierSpace`,
a set of reconciliation **types**, one **suggest/entity** service and one **extend** service.
Data extension only operates on already-reconciled cells: OpenRefine first reconciles a column
(each cell gets a candidate **id**), then calls `/extend` with those ids + chosen properties.

Consequence for parsers: even where the real payload is the extension (name, geotime, area),
each cell must first be reconciled to obtain an id. The extend call only echoes ids back and
carries no other per-cell state, so **the id must be sufficient to reproduce the extension**.

## 4. Endpoint structure

Per-parser services (each `{type}` URL looks to OpenRefine like an independent single-purpose
service), implemented as one generic templated resource plus four dedicated resources for the
parsers that need bespoke reconcile/suggest/extend behaviour.

| Endpoint | Kind | reconcile candidate id | suggest/entity | extend |
|---|---|---|---|---|
| `GET/POST /parser/{type}/reconcile` | generic enum vocabularies | `enum.name()` (e.g. `SPECIES`) | enum constants by prefix | — |
| `GET/POST /parser/name/reconcile` | name parser | raw input string | — | parsed name fields; `code`+`rank` context |
| `GET/POST /parser/geotime/reconcile` | GeoTime | geotime `name` | `GeoTime.TIMES` by prefix | `name, type, start, end` |
| `GET/POST /parser/taxgroup/reconcile` | TaxGroup analyzer | `TaxGroup.name()` | TaxGroup values by prefix | `parent, codes, description, icon` |
| `GET/POST /parser/area/reconcile` | Area | `globalId` CURIE (e.g. `tdwg:14`) | — | `gazetteer, id, name, globalId, link` |

Routing: the literal paths (`name`, `geotime`, `taxgroup`, `area`) win over the templated
`{type}` route by JAX-RS specificity. The generic resource rejects those reserved names and any
unknown type with `404`.

Each resource follows the existing pattern: `GET` with no `queries` returns the manifest; `GET`
with `queries`, `POST` form-urlencoded `queries`, and `POST` JSON (`{"queries": …}` or bare)
run reconciliation. `extend`, `extend/propose`, `suggest/entity` mirror the existing sub-paths.

## 5. Resource behaviour

### 5.1 Generic vocabulary resource — `/parser/{type}/reconcile`

- Resolves `{type}` against a **shared parser registry** (see §6). Unknown/reserved type → 404.
- Manifest: single type = the parser name; `identifierSpace`/`view` point at the CLB vocab page
  (`{clbBase}/vocabulary/{type}`); declares `suggest/entity` only when the parser is
  `EnumParser`-backed.
- Reconcile: `SafeParser.parse(parser, cell)`. On a parsable result, return exactly one
  candidate, auto-matched (`score = 100`, `match = true`):
  - id = `enum.name()` when the value is an enum, else its canonical `toString()`.
  - name = a human label (enum `toString()`/lower-cased name).
  Unparsable → empty `result` array (spec requires the field present).
- suggest/entity: for `EnumParser` types, list enum constants whose name/label starts with the
  prefix (case-insensitive), capped at a small limit. Non-enum types omit the suggest service.
- No data extension.

### 5.2 Name parser resource — `/parser/name/reconcile`

- Manifest: type = `Name`; declares `extend` with `property_settings` for `code` and `rank`
  (selects, default = auto); no suggest service.
- Reconcile (thin): parse the cell with `NameParser` honouring optional `?code=`/`?rank=` query
  params (and per-query `properties` hints `code`/`rank` if present). Parsable → one
  auto-matched candidate, `id = raw input string` (guarantees identical re-parse at extend),
  `name = reconstructed canonical label`, `score = 100`. Unparsable → empty result.
- Extend: re-parse each id and emit cells for the requested properties. Property catalogue:
  `label` (`Name.getLabel()`, full name + authorship), `labelHtml` (`Name.getLabelHtml()`),
  `scientificName` (canonical, no authorship), `authorship`, `rank`, `code`, `type`
  (SCIENTIFIC/VIRUS/HYBRID_FORMULA/…), `uninomial`, `genus`, `infragenericEpithet`,
  `specificEpithet`, `infraspecificEpithet`, `cultivarEpithet`, `combinationAuthorship`,
  `combinationYear`, `basionymAuthorship`, `basionymYear`, `nomenclaturalNote`, `taxonomicNote`,
  `extinct`, `parsed`.

### 5.3 GeoTime resource — `/parser/geotime/reconcile`

- Manifest: type = `GeoTime`; declares suggest/entity and extend (no settings).
- Reconcile: `GeoTimeParser.parse` → candidate `id = GeoTime.getName()`, `name = getName()`
  (optionally suffixed with the type), `score = 100`.
- suggest/entity: enumerate `GeoTime.TIMES` values by prefix.
- Extend: `GeoTime.byName(id)` → cells `name`, `type` (`GeoTimeType`), `start` (Ma),
  `end` (Ma).

### 5.4 TaxGroup resource — `/parser/taxgroup/reconcile`

- The query cell is a **scientific name**, not a vocabulary string. Optional `rank`, `code` and
  classification property hints are mapped into a `SimpleNameClassified` using the shared
  query-builder (§6) and fed to `TaxGroupAnalyzer.analyze(name, classification)`.
- Manifest: type = `TaxGroup`; declares suggest/entity and extend.
- Reconcile: analyze → if a non-other group results, one auto-matched candidate
  `id = TaxGroup.name()`, `name = group label`, `score = 100`. No group / `isOther()` → empty.
- suggest/entity: enumerate `TaxGroup` values by prefix.
- Extend: from `TaxGroup.valueOf(id)` emit `parent` (`getPrimaryParent()`), `codes`
  (`getCodes()`, joined), `description` (`getDescription()`), `icon` (`getIconSVG()`/`getIcon()`).

### 5.5 Area resource — `/parser/area/reconcile`

- Uses the shared `AreaParser.PARSER` (already label-lookup-wired at server startup via
  `areaLookup`).
- Manifest: type = `Area`; declares extend; no suggest (mrgid/wdpa/iso/realm spaces are not
  enumerable).
- Reconcile: parse the cell → if it resolves to a real gazetteer area (`getGlobalId() != null`,
  e.g. `tdwg:14`, `mrgid:14123`, `iso:Germany`→`iso:DE`), one auto-matched candidate
  `id = globalId`, `name = getName()`, `score = 100`. Free-text areas (`Gazetteer.TEXT`, no
  id/globalId) → no candidate (not a known reconcilable entity).
- Extend: `AreaParser.PARSER.parse(globalId)` → cells `gazetteer`, `id`, `name`, `globalId`,
  `link` (`getLink()`).

## 6. Shared components

- **Protocol DTOs** (`OpenRefineModel`, reused): add two backward-compatible fields —
  `ExtendService.property_settings` (list of `{name, label, type, default, choices[]}`) and
  `ExtendProperty.settings` (`Map<String, JsonNode>` on the request side). The existing taxon
  reconciliation does not set these and is unaffected. A small `PropertySetting`/`Choice` DTO is
  added for the manifest side.
- **`ParserOpenRefineMapper`** (new, in `life.catalogue.resources.parser.openrefine`): pure,
  HTTP/persistence-free mapping helpers mirroring `OpenRefineMapper` — vocab/geotime/taxgroup/
  area candidates, manifests per type, name & geotime & taxgroup & area extend, suggest
  responses, and `code`/`rank` setting/param resolution.
- **Shared parser registry**: extract the `name → Parser<?>` map currently built in
  `ParserResource`'s constructor into a small reusable holder (e.g. `Parsers`/`ParserRegistry`),
  used by both `ParserResource` and `VocabReconciliationResource` so the exposed list stays in
  sync. `ParserResource` is refactored to consume it (no behaviour change).
- **Shared query-builder**: factor the OpenRefine `Query` → `SimpleNameClassified` logic
  currently private in `AbstractReconciliationResource` into a shared helper, reused by the
  taxgroup resource (and the existing taxon reconciliation).

New package `life.catalogue.resources.parser.openrefine` with:
`VocabReconciliationResource`, `NameReconciliationResource`, `GeoTimeReconciliationResource`,
`TaxGroupReconciliationResource`, `AreaReconciliationResource`, `ParserOpenRefineMapper`.

## 7. Context resolution (`code`, `rank`) — name parser

Both accepted two ways. Resolution precedence at extend time:

1. Per-extend property `settings` (`{"id":"genus","settings":{"code":"ICZN","rank":"species"}}`),
   rendered by OpenRefine from the manifest `property_settings`.
2. Service-URL query params `?code=…&rank=…` (the default when no setting is sent; flow to both
   reconcile and extend because they share the service base URL).
3. None → code/rank-agnostic parse.

Reconcile also honours the query params (affects parsability and the cosmetic candidate label).
Values are parsed with `NomCodeParser`/`RankParser`; unparsable context is ignored.

## 8. Wiring

Register the five resources in `WsROServer.registerReadOnlyResources` next to the existing
`ParserResource`, `NameParserResource` and `TaxGroupResource` (read-only server). They need
`cfg.getApiUri()` and `cfg.clbURI`; the area resource relies on the `areaLookup` already wired
into `AreaParser.PARSER` there. Where the name parser is also exposed standalone
(`WsMatchingServer`), register `NameReconciliationResource` alongside it for parity.

## 9. Testing

- **`ParserOpenRefineMapperTest`** (unit, mirrors `OpenRefineMapperTest`): vocab candidate
  (enum id + label), geotime/taxgroup/area candidates, name & geotime & taxgroup & area extend
  with and without `code`/`rank`, `property_settings` round-trip and `settings` parsing,
  manifest shape per type (suggest present only where applicable), free-text area → no
  candidate.
- **Resource/integration tests** mirroring the existing OpenRefine resource tests: manifest GET,
  reconcile via GET/POST(form)/POST(json), extend, suggest/entity; assert JAX-RS routing of the
  literal paths vs the templated `{type}` path, and 404 for unknown/reserved vocab types.

## 10. Risks / notes

- Round-trip parsing in extend relies on the id being re-parseable: raw string (name), `name`
  (geotime), `globalId` (area), enum/`TaxGroup` constant. Parsing is cheap and cached.
- `property_settings` per-property repetition is a minor OpenRefine UX wart; the `?code/rank`
  service-URL default mitigates it for whole-project use.
- Taxgroup reconcile depends on classification hints for accuracy; with only a bare name the
  analyzer may return a coarse or "other" group (→ no candidate). This matches the existing
  `TaxGroupResource` behaviour.
