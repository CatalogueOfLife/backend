# Slim reusable modules — dependency reduction design

Date: 2026-07-10
Branch: `worktree-slim-reader` (from `master`)
Status: design, pending implementation plan

## Goal

Reduce the transitive dependency footprint of the modules that external projects
reuse — primarily `coldp`, `reader`, `metadata`, and the model in `api` — so they
can be published to Maven Central without dragging in heavyweight libraries. The
four named offenders to quarantine are **jackson (databind)**, **httpclient5**,
**mapdb**, and **kryo**, plus **citeproc-java / jbibtex** as a follow-on.

Acceptance bar: a slim reusable tier whose only compile dependencies beyond the
COL/GBIF format libraries are annotation-level (jackson-annotations,
jakarta.validation-api) and slf4j.

## Current state (findings)

`coldp` (dwc-api only) and `vocab` (name-parser-api + coldp) are already slim and
stay untouched. All the weight is in **`api`**, and because `reader` and
`metadata` both depend on `api`, they inherit everything it drags in.

The heavy dependencies are unusually well-isolated inside `api`:

| Heavy dep | Location in `api` | Real consumers (repo-wide) |
|---|---|---|
| mapdb + kryo | entirely within `common/kryo/**` (18 files) | core, dao, importer, parser |
| httpclient5 | a single file: `common/io/DownloadUtil.java` | dao, importer, webservice (0 uses in `api` itself) |
| citeproc-java | `common/csl/**` + a `CSLType` field on `CslData`/`Citation` + `toCSL()` converters | core, dao, importer, webservice, metadata |
| jbibtex | only `common/csl/{CslUtil,CslDataConverter}` | (via csl) |
| jackson databind | `api/jackson/**` (20 files) + inline serde in 4 model files | inherent to the JSON API model |

Decisive facts verified against the code:

- **`reader` uses no jackson at all** in its own source — it only inherits it via `api`.
- **`api`'s only reference to `DownloadUtil` is the file itself** — zero other usages,
  so httpclient can leave `api` entirely.
- **citeproc is *structural* in exactly one place**: the `CSLType` enum used as a
  field type on `CslData.type` and `Citation.type`. `CslName` is a COL POJO
  (String fields only); its citeproc import is only for a conversion method.
  Everything else is behavioral (`toCSL*()` methods) or downstream (the formatter).
- **`metadata` genuinely uses databind (7 files) and citeproc** — it is inherently
  heavier and will keep depending on the serde + reference tiers; it can still shed
  kryo and mapdb.
- **`core` depends on `dao`** (not the reverse), so `DownloadUtil` cannot live in
  `core` — `dao` is the lowest module upstream of all real consumers.

## Target module graph

```
  SLIM / PUBLISHABLE TIER
  ┌──────────┐   ┌────────┐
  │  coldp   │   │ vocab  │        (unchanged)
  └────┬─────┘   └───┬────┘
       └──────┬──────┘
         ┌────▼──────┐
         │    api    │  slim POJO model, jackson-ANNOTATIONS only
         └────┬──────┘  + coldp, vocab, name-parser-api, dwc-api,
              │           jakarta.validation-api, slf4j  (+ commons-lang3/guava/
              │           fastutil/commons-io — reviewed to minimise)
  ────────────┼──────────────────────────────────────  publish line
              │
  FAT / INTERNAL TIER
   ┌──────────┼───────────┬───────────────┐
   ▼          ▼           ▼               │
┌──────────┐┌──────────┐┌───────────┐     │
│api-jackson││reference ││   kryo    │     │  (DownloadUtil + httpclient → dao)
│ databind ││citeproc  ││kryo+mapdb │     │
│ yaml/310 ││+jbibtex  │└───────────┘     │
└──────────┘└──────────┘                  ▼
                                    dao → core → importer → webservice
```

### Module-by-module

**`api` (reshaped → slim published model)**
- Contents: `api/model`, `api/search`, `api/vocab`, `api/exception`, `api/util`,
  `api/constraints`, `common/{collection,func,lang,text,util,tax,date,id}`,
  `common/io` **minus** `DownloadUtil`.
- Dependencies: coldp, vocab, name-parser-api, dwc-api, **jackson-annotations**,
  jakarta.validation-api, slf4j, plus commons-lang3, guava, fastutil, commons-io
  kept as-is this pass. **Trimming guava/fastutil/commons from the model is a
  follow-up**, not part of this change.
- Removes: jackson-databind/yaml/jsr310/blackbird, kryo, mapdb, httpclient5,
  httpcore5, citeproc-java, jbibtex, locales, antlr4-runtime, metrics-core,
  jsoup, univocity, commons-compress, jena (test).
- Constraint: model classes **must not reference custom databind serializer
  classes**. The inline serde in `Identifier`, `DOI`, `DatasetSettings`, `Citation`
  moves to type-registered serializers in `api-jackson`'s `ApiModule`
  (`SimpleModule.addSerializer(Type.class, …)`), leaving the model annotation-only.

**`api-jackson` (new — serde layer)**
- Contents: `api/jackson/**` (`ApiModule`, custom serializers, `CSLTypeSerde`,
  `PermissiveEnumSerde`, …) + the relocated serde from the 4 model files.
- Dependencies: `api`, jackson-databind, jackson-dataformat-yaml,
  jackson-datatype-jsr310, jackson-module-blackbird.
- Consumers: every internal module that (de)serialises COL JSON — dao, core,
  importer, webservice, metadata, doi, and tests. `reader` does **not** depend on it.
- Note: `CSLTypeSerde`/`PermissiveEnumSerde` currently import citeproc `CSLType`;
  after the enum swap (below) they serialise the COL-owned enum, so `api-jackson`
  does not pull citeproc.

**`reference` (new — citation formatting)**
- Contents: `common/csl/**` (`CslFormatter`, `CslUtil`, `CslDataConverter`) + the
  `toCSL*()` converter methods relocated out of `Citation`, `Dataset`, `Agent`,
  `CslName`, and `FuzzyDate`, plus the relocated `Dataset` self-citation formatting.
- Dependencies: `api`, citeproc-java, jbibtex, locales; `api-jackson` only if a
  converter needs databind (confirm during implementation).
- Consumers: core, dao, importer, webservice, metadata (the current in-`api`
  callers of CSL become `reference` callers).

**`kryo` (new — binary serde)**
- Contents: `common/kryo/**` (incl. `map/` mapdb serializers and `jdk/`).
- Dependencies: `api`, kryo, mapdb, fastutil; `api-jackson` only if
  `JsonObjSerializer`/`ApiKryoPool` need databind (confirm during implementation).
- Consumers: core, dao, importer, parser.
- Note: `ApiKryoPool` currently registers a serializer for citeproc `CSLType`;
  after the enum swap it registers the COL-owned enum, so `kryo` does not pull
  citeproc.

**`dao` (gains DownloadUtil)**
- `common/io/DownloadUtil.java` moves here; `dao` declares httpclient5 + httpcore5
  directly. All real consumers (dao, importer, webservice) are `dao`-or-downstream.

### Effect on the named artifacts

- **`reader`**: switch its dependency from `api` (fat) to `api` (slim) — no
  `api-jackson` needed. Loses jackson, httpclient, kryo, mapdb, citeproc; keeps
  univocity, woodstox, guava, commons-lang3/text.
- **`metadata`**: depends on slim `api` + `api-jackson` + `reference` + parser.
  Loses kryo and mapdb. Databind and citeproc remain (inherent to its job).
- **`coldp`**: unchanged.

## The citeproc surgery (the one real knot)

Feasible, in rising difficulty:

1. **jbibtex** — trivial; confined to `common/csl`, moves whole to `reference`.
2. **`CSLType` field** on `CslData`/`Citation` — replace citeproc
   `de.undercouch.citeproc.csl.CSLType` with a **COL-owned `CSLType` enum**
   (a stable CSL vocabulary: article, book, chapter, dataset, …). This severs the
   only structural citeproc coupling from the model. `ApiKryoPool`,
   `CSLTypeSerde`, `PermissiveEnumSerde`, and `DataPackageBuilder` update to the
   COL enum. `reference`'s converters map COL enum ↔ citeproc enum at the boundary.
3. **`Dataset` self-citation (thin hook)** — `Dataset.getCitation()`/
   `getCitationText()` currently call `CslUtil.buildCitation(toCSL())` internally
   (`Dataset.java` ~lines 1011/1019). A slim model cannot reach the formatter, but
   we keep the call sites stable via a **hook**: define a minimal
   `CitationFormatter` interface in slim `api` (e.g. `String citation(Dataset)` /
   `String citationHtml(Dataset)`) with a static holder on `Dataset` that defaults
   to a no-op (returns null / a plain fallback). The `reference` module provides the
   citeproc-backed implementation and registers it once at application startup
   (in `WsServer` init, alongside the existing `ApiModule`/pool setup). `Dataset`'s
   lazy getters delegate to the registered hook, so **no `Dataset` caller changes**.
   Trade-off: introduces a static service-locator style hook on `Dataset`; acceptable
   to keep the model surface unchanged. Tests must set the hook (or assert the
   no-op fallback) explicitly.

The `toCSL*()` converters on `Citation`, `Dataset`, `Agent`, `CslName`, and
`FuzzyDate.toCSLDate()` move to `reference` as static/utility converters taking the
model object and returning citeproc objects.

## Execution

Single branch, one large change (user preference), but internal ordering to keep the
build green at each step:

1. Extract `common/kryo/**` → `kryo` module; repoint core/dao/importer/parser.
2. Move `DownloadUtil` → `dao`; add httpclient to `dao`; repoint importer/webservice.
3. Introduce COL-owned `CSLType`; swap the two field types and the serde/kryo/
   datapackage references.
4. Extract `common/csl/**` + `toCSL*()` converters + `Dataset` self-citation →
   `reference`; repoint callers.
5. Extract `api/jackson/**` + the 4 model files' serde → `api-jackson`; strip
   databind from `api`; repoint every serde consumer.
6. Slim `api`'s pom to annotations-only (drop databind/kryo/mapdb/httpclient/
   citeproc/jbibtex); leave commons/guava/fastutil in place (follow-up).
7. Repoint `reader` at slim `api`; verify it no longer resolves jackson/httpclient/
   kryo/mapdb/citeproc (`mvn dependency:tree`).
8. Repoint `metadata` at slim `api` + `api-jackson` + `reference`; verify kryo/mapdb
   gone.

Reactor module order becomes:
`coldp, vocab, api, api-jackson, reference, kryo, parser, dao, core, importer,
reader, reader-xls, metadata, doi, pgcopy, webservice` (Maven resolves the exact
order from the dependency graph).

## Risks & mitigations

- **`Dataset` self-citation hook** (highest): a static `CitationFormatter` hook
  replaces the inline formatter call. Mitigation: default to a safe no-op; register
  the citeproc implementation once at startup; add tests pinning citation output
  (with the hook set) and asserting the fallback (hook unset), so a forgotten
  registration fails loudly rather than silently returning null.
- **COL `CSLType` drift from citeproc**: the enum must map 1:1 to
  `de.undercouch.citeproc.csl.CSLType`. Mitigation: a boundary mapping in
  `reference` with a test asserting every value round-trips.
- **Split packages across artifacts** (`life.catalogue.api.*` in both `api` and
  `api-jackson`): fine on the classpath since the project uses no JPMS
  `module-info`; do not add one.
- **Missed transitive serde need**: some module may rely on databind only
  transitively via `api` today. Mitigation: after slimming, a full `mvn clean
  install` surfaces every missing `api-jackson` edge; add them explicitly.
- **`kryo`/`reference` needing `api-jackson`**: confirm `JsonObjSerializer` and the
  CSL converters' databind use during implementation; add the edge if required
  (both are internal-tier, so this does not affect the slim tier).

## Testing / verification

- `mvn clean install` green across all modules.
- `mvn dependency:tree` for `reader`: no jackson, httpclient, kryo, mapdb, citeproc,
  jbibtex.
- `mvn dependency:tree` for `metadata`: no kryo, mapdb.
- `mvn dependency:tree` for slim `api`: no databind, kryo, mapdb, httpclient,
  citeproc, jbibtex.
- Citation-output regression tests (pre/post) for `Dataset` and `Citation`.
- `CSLType` round-trip mapping test.
- Build from the worktree uses
  `-Dmaven.gitcommitid.skip=true -Dgit.skip=true`.

## Out of scope

- Publishing configuration / Maven Central release mechanics (separate task).
- Trimming guava / fastutil / commons from the slim `api` model (follow-up pass).
- Reducing GBIF `dwc-api` / `name-parser-api` footprint.
- Any `checklistbank` UI or `portal` changes.
