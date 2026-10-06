# Sector profiles

Date: 2026-10-05
Status: implemented on branch `feat/sector-profiles`, not yet merged or deployed. Current behaviour is described in
[SECTOR-SETTINGS.md](SECTOR-SETTINGS.md).

## Why

A sector's sync behaviour is configured on the sector itself, with a few project-wide fallbacks in the
`SECTOR_*` dataset settings. That works for a hand-curated checklist of a hundred sources. It does not
work for a project that merges whole publishers.

Measured on COL (project 3) in prod on 2026-10-05:

- 63,034 sectors from 62,576 source datasets: 62,450 merge, 581 attach, 3 union.
- **62,349 merge sectors (98.9%) carry identical settings.** Each holds its own copy of the publisher ranks
  GENUS..FORM, written by `SectorDao.createMissingMergeSectorsFromPublisher`, and nothing else differs.
  56,109 are Plazi; the rest come from 33 other sector publishers (EJT 1,702, ZooKeys 1,180,
  BDJ 573, PhytoKeys 497, ...). 62,068 of them are dataset type ARTICLE.
- **About 100 hand-curated merge sectors carry all the real variation.**
  - `code` (61) and `ranks` (101) are set per subtree: 12 WoRMS sectors and 4 IUCN sectors each have their own
    code and ranks.
  - `entities` (8) marks the extension-only sources: reference-only TPL, BHL, IPNI Literature, BioNames;
    vernacular-only iNat, Wikidata, Wikispecies.
  - `nameTypes` (8) widens or narrows the name types: iBOL and UNITE add formula and identifier names; ICN, GRIN,
    ZooBank, iNat and Rosa keep scientific names only.
- **Attach sectors** only ever set `code` (78) or `ranks` (10).
- **Never set on any sector**: `placeholderRank`, `useXRelease=false`, `nameStatusExclusion`, `nameFilter`,
  `extinctFilter`, `authorshipUpdate`.
- **Project settings**: COL sets `SECTOR_ENTITIES`, `SECTOR_NAME_TYPES` and `SECTOR_CREATE_IMPLICIT_NAMES`.

So changing a setting for "all Plazi sectors" means rewriting tens of thousands of rows, and there is no bulk
update. Two groups would cover almost everything: the publisher sectors, and a handful of extension-only sources.

### What was wrong with the current resolution

`SectorRunnable.loadSectorAndUpdateDatasetImport` merges project settings into the sector in memory, and every
field follows its own rule:

- `entities`, `nameTypes`, `nameStatusExclusion`: an empty sector value means inherit.
- `ranks`: MERGE sectors use the hard-coded `MERGE_RANKS_DEFAULT` and silently ignore `SECTOR_RANKS`.
- `copyAccordingTo`, `removeOrdinals`, `createImplicitNames`: the project setting always wins. These fields exist
  on `Sector` and in the API, but have no column and no mapper entry. Whatever a client sends is dropped.
- `code`, `extinctFilter`, `nameFilter`, `authorshipUpdate`: no project default at all.

Not part of this: `Setting.NOMENCLATURAL_CODE` is a source-dataset *import* setting (`NameInterpreter`). The sync
applies only `sector.code` (`TreeBaseHandler.processCommon`).

## Goals

- One place to configure the sync behaviour of an arbitrarily large set of sectors.
- Groups that follow new sectors without anyone assigning them, above all the merge sectors XRelease creates per
  publisher.
- One shared settings object and one resolution rule for every level, replacing the per-field special cases.
- Make it visible, for any sector, where each effective value comes from.

## Non-goals

- Sharing profiles across projects. A profile belongs to one project and is copied into its releases.
- Curator tags on sectors or sources. Hand-picked lists cover the eight extension-only sources; a tag can be
  added later as one more selector field.
- Profile-level priority. Merge order stays a per-sector `priority`.
- Release-wide merge behaviour that is not about one source record: `enforceUnique`, `protectedGroups`,
  `basionymExclusions`, `incertaeSedis` and the `decisions` stay in `XReleaseConfig`.
- New record defaults such as a default environment. The shared object makes them cheap to add once wanted.

## Design

### SectorSettings

A value object in `api` with every field nullable. Null means "not set at this level".

| Group | Fields | Combining rule |
|---|---|---|
| Sync filters | `ranks`, `entities`, `nameTypes`, `extinctFilter`, `nameFilter` | nearest wins |
| Sync flags | `copyAccordingTo`, `removeOrdinals`, `createImplicitNames` | nearest wins |
| Record defaults | `code`, `authorshipUpdate` | nearest wins |
| Blocklists | `nameStatusExclusion`, `issueExclusion`, `blockedNames`, `blockedNamePatterns` | union |

- **Nearest wins** for scalars and allow-lists. An empty set counts as "not set", as it does today, so a level
  cannot say "no entities at all".
- **Blocklists are unioned** over all levels. A profile or sector can only add restrictions, never lift one set
  further up.
- `TreeMergeHandler` additionally unions the release-wide `XReleaseConfig` blocklists, which remain a release
  concern and outside the resolver.
- **Known limit**: a null `extinctFilter` cannot reset a profile's filter back to "all". No sector uses it.

`Sector` holds its settings as `@JsonUnwrapped SectorSettings`, so the JSON API keeps its flat shape. The
existing getters and setters delegate to it. Fields that only make sense for one sector stay on `Sector`:
`mode`, `subject`, `target`, `placeholderRank`, `useXRelease`, `priority`, `note`.

### Profiles

A `SectorProfile` is project scoped and has `title`, `description`, `position`, a `SectorSelector` and a
`SectorSettings`. It is stored in `sector_profile`.

**The selector** is ANDed across fields and ORed within a field. An empty selector matches every sector of the
project. Fields:

- `modes`
- `datasetTypes`
- `publisherKeys`
- `anySectorPublisher`: matches the publishers in the project's `sector_publisher` table, so it follows new
  publishers.
- `subjectDatasetKeys`
- `sectorKeys`

Publisher and type come from the live `dataset` row of the subject, the join `SectorSearchRequest.publisherKey`
already uses. The selector is stored as typed array columns so it can be matched in SQL. Settings are jsonb, so
`SectorSettings` can grow without DDL.

**Membership is evaluated live** at sync time, never stored.

### Levels

Lowest to highest:

1. **Built-in defaults per mode**, in code:
   - entities: all;
   - ranks: `MERGE_RANKS_DEFAULT` for MERGE, all ranks otherwise;
   - `createImplicitNames`: true;
   - `authorshipUpdate`: NONE;
   - the other flags: false.
2. **Every matching profile, in ascending `position`**. A later profile overrides an earlier one field by field.
   The project defaults are just a profile with an empty selector at the bottom: the `SECTOR_*` settings migrate
   into one and are removed from `Setting`.
3. **The sector.**

`SectorSettingsResolver` (in `dao`) turns a sector and its matching profiles into the effective settings, plus the
provenance of every field: `default`, `profile:<id>` or `sector`. `SectorRunnable` calls it in place of today's
merge code. The handlers keep reading `sector.getX()`.

`GET /dataset/{key}/sector/{id}/settings` serves the result, so the UI can show "inherited from Plazi".

### API

- `/dataset/{key}/sector/profile`: CRUD for editors, plus `GET {id}/sector` listing the members of a profile.
- `SectorSearchRequest.profileKey` does the same for the sector search.

### Releases

`AbstractProjectCopy` copies `sector_profile` next to `sector_publisher`. A release therefore resolves its sectors
exactly as the project did when it was made. An XRelease syncs its merge sectors against the project key, so the
profiles of the project apply there.

## Migration

1. **DDL**:
   - new table `sector_profile`;
   - nullable sector columns `copy_according_to`, `remove_ordinals`, `create_implicit_names`,
     `issue_exclusion`, `blocked_names`, `blocked_name_patterns`;
   - `authorship_update` loses its NOT NULL and default;
   - the `ranks` default becomes NULL.
2. **Project defaults**: every project with `SECTOR_*` settings gets a "Project defaults" profile built from them.
   - `SECTOR_RANKS` goes into its own profile restricted to `ATTACH`, `UNION` and `HIERARCHY`. That keeps today's
     behaviour that MERGE sectors ignore it.
   - The keys are then removed from `dataset.settings`.
3. **Publisher sectors**: every project with `sector_publisher` rows gets a "Publisher sectors" profile:
   `anySectorPublisher`, mode MERGE, ranks GENUS..FORM.
   - Sectors it covers whose ranks are exactly GENUS..FORM lose their copy. That is 62,332 rows in COL.
   - The 17 whose publisher is no longer a sector publisher keep their explicit ranks.
   - `createMissingMergeSectorsFromPublisher` stops writing ranks.
4. **COL, by hand and optional**: an "Extension-only sources" profile for the reference-only sources.

The new sector flag columns start NULL everywhere. That is lossless, because no sector value of those flags was
ever persisted.

**Verification on test**, which holds a full prod copy: dump every COL sector's effective settings with the old
code before migrating, compare them with the new endpoint afterwards, and expect no difference.

## Rejected alternatives

- **Bulk patch** (`PATCH /sector` with search filters): cheap, but it keeps writing copies into every sector. The
  publisher sectors created next month would not get the change, and the sectors drift apart again.
- **Settings on `SectorPublisher` only**: the smallest step and covers 99% of COL. It cannot express the
  extension-only sources, or a group by dataset type across publishers.
- **Explicit profile assignment** (`sector.profile_key`): predictable, but every new sector needs assigning, and
  a sector can be in only one group.
- **Materialised membership** (a link table filled from rules): easy to query, but it has to be kept in step
  whenever a dataset changes publisher or type. The live SQL match is cheap at sync time.
- **Profiles in the project YAML** (next to `XReleaseConfig`): versioned and reviewable, but not editable in the
  UI, and only read when a release starts, while sector syncs in the project need them too.
- **First matching profile wins**: easier to display, but overlapping groups would each have to repeat the
  shared settings.
- **Nearest wins for blocklists too**: one rule for everything, but a profile setting `issueExclusion` would
  silently lift the project's exclusions.

## Outcome

Implemented as designed. These are the deviations, and what the implementation found.

- **No `@JsonUnwrapped`.** `Sector` and `SectorSettings` share the `SyncSettings` interface instead. Jackson drops an
  incoming property that the parent marks `@JsonIgnore` before an unwrapped child sees it, and `Sector`'s delegating
  getters would have needed exactly that. The JSON stays flat either way.
- **The `ranks` column default stays `'{}'`.** The array type handlers write an empty array for null anyway, and an
  empty list already means inherit.
- **Unknown dataset settings keys are ignored with a warning.** Before, a single stale key made a dataset's settings
  unreadable for every reader. Removing the `SECTOR_*` settings would have turned that into a hard ordering
  constraint between the migration and the deploy.
- **The migration SQL needs explicit `::SECTOR_MODE[]` and `::JSONB` casts.** Untyped literals in an
  `INSERT … SELECT` list resolve to text. This was found by running the logged SQL against the real schema before
  writing it down for prod.
- **No resource tests.** The repo has no Jersey resource tests, so the DAOs carry them.
  `docs/SECTOR-SETTINGS.md` takes the place of an `API.md` section, because `API.md` is a three-line stub.
- **`XReleaseIT` never applied the `SECTOR_*` settings it set.** `SectorSyncMergeIT.setupProject` replaced the whole
  settings map right afterwards. The dead lines were removed without any change in behaviour.
- **Blocklists from profiles and sectors also apply to merges inside a project.** Before, the release config
  blocklists only applied during an XRelease.
- **The prod migration runs in two steps** (`dbschema.md`, 2026-10-05).
  - **Before the deploy:** it turns all `SECTOR_*` settings, ranks included, into a "Project defaults" profile.
    Where `SECTOR_RANKS` was set, a "Merge ranks" profile limited to MERGE puts back the FAMILY..FORM that merge
    sectors always used instead. That replaces the planned separate ranks profile for the other modes: one profile
    then holds every migrated project setting, and the merge rule becomes visible and editable. It creates a
    "Publisher sectors" profile per project with sector publishers. Hand-curated merge sectors on a publisher's
    dataset get their FAMILY..FORM pinned.
  - **After the switch:** the publisher sectors drop their copied ranks and the old settings keys go.
  - Until step 2, both apps of a blue-green deploy behave the same and a rollback loses nothing. This came out of the
    final review.
- **The "Publisher sectors" profile is ensured, not just migrated.** XRelease creates it when it adds publisher
  sectors and no profile selects `anySectorPublisher`, e.g. on a freshly built dev database or for a new project.
  Otherwise those sectors would quietly merge from family down.
- **An XRelease carries the project's profiles, not its base release's**, because its merge syncs resolve against the
  project's.
- **A sector's own regexes are validated on save**, like a profile's. The set type handlers skip nulls instead of
  failing every read.
