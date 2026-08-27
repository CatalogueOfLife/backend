# Sector metadata

Date: 2026-08-27
Status: in progress. The model, the resolver, the release freeze and the API have landed; the ColDP
importer that creates `SOURCE` sectors from `sourceID` has not.

Issue: [#1273](https://github.com/CatalogueOfLife/backend/issues/1273)

## Why

ChecklistBank renders a page per **source dataset**, built from `dataset_source` — the frozen
per-release copy of each source's metadata. A sector, the unit that actually contributes the data,
could say nothing about itself beyond a free-text `note`.

That hurts most where a source is itself an aggregation. Measured against COL in August 2026:

- **67 of COL's 160 source datasets are WoRMS subsets** — `WoRMS Porifera` is *World Porifera
  Database*, `WoRMS Mollusca` is *MolluscaBase* — each registered as its own CLB dataset largely so it
  could own a metadata page, a DOI and an editor list.
- Each holds a CoL-minted concept DOI (`1130` → `10.48580/d3cz`) and its own editorial board
  (Porifera: 23 editors).
- Each is attached to COL by exactly one `attach` sector rooted at the matching taxon.

The intended end state is that WoRMS, WFO and ITIS **declare metadata for parts of their own data**,
COL absorbs those declarations as sector metadata, and the 67 stand-in datasets collapse into a single
WoRMS dataset. Editors keep the final say through an override they should rarely need.

## Decisions

**Inheritance, not restatement.** A sector's metadata is sparse. Whatever it does not say is inherited
from its source dataset, so the ~62,972 sectors that say nothing keep rendering exactly what they
render today and cost nothing.

**No DOIs are minted per sector.** `sector_metadata` does keep a `doi` column, but only to record a
sub-source's *existing* DOI — which is what lets a WoRMS subset keep its `10.48580/…` when it stops
being a stand-in dataset. Nothing in the code mints one.

**The container is the release.** A sector cites as a chapter in the release, exactly as a source
dataset does. This keeps the consolidation citation-neutral: MolluscaBase must cite identically before
and after it becomes a sector. Provisional, pending a wider discussion.

**Releases are read-only.** There is no `PUT` to correct a frozen release's sector metadata, unlike
`DatasetSourceResource`.

## Model

Sector metadata has the same three-stage life as source dataset metadata, but in **one table** rather
than three:

| Stage | Dataset level | Sector level |
|---|---|---|
| Declared by the publisher, refreshed on every import | `dataset` columns | `sector_metadata` on the **EXTERNAL** dataset |
| Editor override in the project | `dataset_patch` | `sector_metadata` on the **PROJECT** |
| Frozen into a release | `dataset_source` | `sector_metadata` on the **(X)RELEASE** |

The dataset level needs `dataset_patch` and `dataset_source` separate only because `dataset_source`
keeps columns the patch drops (`attempt`, `origin`, `type`, `gbif_key`, `version_doi`, …) and makes
`title` NOT NULL again. Sector metadata has no such column difference, and the key
`(dataset_key, sector_id)` already separates a project's rows from a release's. So `sector_metadata`
means one thing everywhere: *what this dataset says about this sector*. Declaration and override are
the same operation once the base is inherited.

`sector_metadata` is a `LIKE dataset_patch` clone, so the canonical metadata column list still lives
once, as the `SELECT_NO_KEY` / `COLS` / `PROPS` fragments in `DatasetPatchMapper.xml`, and cannot
drift. `sector_citation` mirrors `dataset_citation` for `Dataset.source`.

The Java carrier is `Dataset` itself, exactly as it is for `dataset_patch`, which has never had a model
of its own. `Dataset.applyPatch` is the merge.

Two additions to `sector` tie it together:

- **`Sector.Mode.SOURCE`** — a sector an EXTERNAL dataset declares about a part of its own data. Pure
  provenance, never an assembly instruction.
- **`sector.subject_sector_id`** — the link from a COL sector to the source sector it absorbs.

### `sector.id` is the ColDP `sourceID`

For a `SOURCE` sector the id is not drawn from a sequence — it *is* the integer `sourceID`. So
`name_usage.sector_key` equals the record's `sourceID`, the whole subject stays NULL, there is no id
mapping in the per-row import path, and the ids are stable across re-imports, which is what makes
`subject_sector_id` a safe link. CLB therefore requires integer `sourceID`s to import sector metadata;
the ColDP spec keeps `sourceID` free-form, and our own exports already satisfy this.

### Resolution

In a project:

```
base = dataset(subjectDatasetKey), dataset_patch applied     # existing behaviour
    + sector_metadata(subjectDatasetKey, subjectSectorId)    # publisher declared
    + sector_metadata(datasetKey, id)                        # editor override, usually absent
```

In a release the two sector-level layers were merged when the release was built, so it collapses to
`dataset_source` (already frozen) plus the one frozen row.

## What a release does

`sector_metadata` is the one sector-scoped table a release **resolves rather than copies**. Everything
else — `sector`, `decision`, `estimate`, `sector_publisher` — is copied verbatim in
`AbstractProjectCopy.copyData()`. Sector metadata cannot be, because the publisher-declared layer lives
in the EXTERNAL source dataset and is rewritten on every import; a release has to freeze the *merge*.

`ProjectRelease.finalWork()` does it, after the source-dataset archiving loop. Doing it there rather
than in `copyData()` also means `SectorMapper.deleteOrphans` has already settled which sectors the
release really has, and an XRelease's tmp-project round trip is over — so there is exactly one write
path for both release types and none of the copy machinery needed changing.

`AuthorlistGenerator` also walks the frozen rows. Without that, consolidating the 67 WoRMS subsets
would silently drop ~67 editorial boards from COL's own citation.

## Traps worth knowing

- **`Dataset.PATCH_PROPS` excludes `license`** (marked "required"), so `applyPatch` never carries it.
  A sub-source routinely licenses differently from its umbrella, so `SectorMetadataDao` applies it
  explicitly.
- **Empty collections are not null.** `applyPatch` copied every non-null value, and collections read
  back from a sparse patch are empty rather than null, so a patch with no keywords or citations wiped
  the ones it inherited. Fixed in `applyPatch` itself: an empty collection means "says nothing", and
  clearing a list is what the `NULL_TYPES` sentinels are for. `dataset_patch` got the same fix.
- **The first `ON DELETE CASCADE` in the schema**, and it is load-bearing. Sector rows are deleted from
  seven call sites; without it `deleteOrphans` and `DatasetDao.deleteKeptReleaseData` raise foreign key
  violations, failing the release job and making dataset deletion impossible.
- **The `subject_sector_id` foreign key scopes `SET NULL` to that one column** (PG 15+). A plain
  composite `SET NULL` would also wipe `subject_dataset_key` and cut the sector loose from its source.
- **`SectorMetadataMapper` is deliberately not a `DatasetProcessable`**, and must never join
  `SectorProcessable.MAPPERS`. The former enrols it in `DatasetDao`'s bulk delete loop, which runs
  before the sector-retention decision; the latter is the wipe-before-resync set that `SectorSync`
  iterates, so every sync would delete the editorial metadata.
- **A nested unqualified `<include refid>` resolves against the *including* namespace.** Splitting
  `DatasetPatchMapper.SELECT` broke `DatasetMapper`'s include chain until the inner refid was fully
  qualified.

## Not built

- The ColDP importer: reading `source/{id}.yaml`, creating `SOURCE` sectors and binding records by
  `sourceID`. `ColdpInterpreter` ignores `sourceID` entirely today. The reader hook is
  `ColdpReader.discoverMoreSchemas()`, which already handles a `treatments/` subfolder the same way.
  Two constraints it will have to meet: sectors must be written before any data, because `name`,
  `reference`, `name_usage`, `name_rel` and `type_material` all carry
  `FOREIGN KEY (dataset_key, sector_key) REFERENCES sector`; and re-import must **upsert**, since a
  delete-and-recreate would cascade away the metadata and trip `subject_sector_id`'s `SET NULL` on
  every import.
- Exporting sector metadata, which is what would close the export → import round trip. Deferred with
  the importer rather than done now, because it needs an id namespace decision that is not obvious:
  the exporter writes `source/{datasetKey}.yaml` and sets `sourceID` to the source *dataset* key today,
  while an importable sector declaration needs `sourceID` to be the *sector* id. Both are plain
  integers in one flat namespace, so a sector id and a dataset key can collide. Emitting sector ids
  uniformly would resolve it and round-trip cleanly, but changes what `sourceID` means for existing
  consumers and would emit one file per sector — COL has 62,972.
- Per-attempt archiving of the publisher-declared layer. `DatasetSourceMapper` picks archived vs live
  source metadata by comparing `sector.dataset_attempt` to the source's current attempt, backed by
  `dataset_archive`. There is no `sector_archive`, so a project synced against an older source version
  resolves its sector metadata from the current import. Accepted: the release freezes it either way.
- A sector-level `logo` is stored as a URL but not archived; `ProjectRelease.finalWork` archives images
  for dataset sources only.

## Explicitly not done

No tooling to migrate the 67 WoRMS subsets. Editors will do it one at a time, by hand, once a complete
WoRMS dataset exists.
