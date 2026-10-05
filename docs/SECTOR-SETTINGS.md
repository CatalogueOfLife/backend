# Sector settings and profiles

A sector's settings steer what a sync copies from its source and how. They can be set on the sector itself or,
for any number of sectors at once, in a **sector profile** of the project. The design record is
[2026-10-05-sector-profiles.md](2026-10-05-sector-profiles.md).

## The settings

| Setting | Type | What it does | Combines | Modes |
|---|---|---|---|---|
| `ranks` | ranks | Only accepted names of these ranks are synced (synonyms below species always) | nearest | all |
| `entities` | entity types | Which entities are synced | nearest | all |
| `nameTypes` | name types | Only names of these types are synced | nearest | all |
| `nameFilter` | regex | Only names whose scientific name fully matches are synced | nearest | all |
| `extinctFilter` | boolean | `true` syncs extinct taxa only, `false` extant ones only | nearest | all |
| `copyAccordingTo` | boolean | Keep the accordingTo of synced usages | nearest | all |
| `removeOrdinals` | boolean | Remove the ordinals of synced taxa | nearest | all |
| `createImplicitNames` | boolean | Create implicit genera and species | nearest | all |
| `code` | nom. code | Force this code onto every synced name | nearest | all |
| `authorshipUpdate` | NONE, MISSING, ALWAYS | Copy the source authorship onto matched names | nearest | HIERARCHY |
| `nameStatusExclusion` | nom. status | Names with these statuses are not synced | union | all |
| `issueExclusion` | issues | Names flagged with these issues are not merged | union | MERGE |
| `blockedNames` | names | Names never merged, with or without authorship, case insensitive | union | MERGE |
| `blockedNamePatterns` | regex | Case insensitive patterns searched in the name label, never merged | union | MERGE |

The three MERGE-only blocklists add to those of the XRelease config, which apply to every merge of a release.

Settings that only make sense for one sector stay on the sector: `mode`, `subject`, `target`, `placeholderRank`,
`useXRelease`, `priority` and `note`.

## Levels

A sync resolves every setting from three levels, lowest first:

1. **Built-in defaults**:
   - all entities;
   - ranks FAMILY to FORM for MERGE sectors, all ranks for the other modes;
   - `createImplicitNames` true;
   - `copyAccordingTo` and `removeOrdinals` false;
   - `authorshipUpdate` NONE;
   - no filters, no code.
2. **The matching profiles**, in ascending `position`, ties broken by id.
3. **The sector.**

**Nearest** means the highest level that sets a value wins. Null, and an empty list, mean "not set", so a level
cannot set "no entities at all". **Union** means the lists of all levels add up, so no level can lift a block set
below it.

## Profiles

A profile has a `title`, a `description`, a `position`, a `selector` and `settings`, the latter holding any of the
settings above. The selector decides which sectors of the project it applies to:

| Selector field | Matches sectors |
|---|---|
| `modes` | of these modes |
| `datasetTypes` | whose source dataset has one of these types, e.g. `ARTICLE` |
| `publisherKeys` | whose source dataset is published by one of these GBIF publishers |
| `anySectorPublisher` | whose source dataset is published by any of the project's sector publishers |
| `subjectDatasetKeys` | from these source datasets |
| `sectorKeys` | these very sectors |

Fields combine with AND, the values within one field with OR. An empty field places no restriction, so an empty
selector matches every sector, which is how the project-wide defaults are kept.

Membership is evaluated whenever a sector is synced. A sector created later, e.g. by an XRelease for a new dataset of
a sector publisher, joins every profile it matches. Publisher and type are read from the source dataset as it is now.

## API

| Request | |
|---|---|
| `GET /dataset/{key}/sector/profile` | The profiles of a project or release, in cascade order |
| `POST /dataset/{key}/sector/profile` | Create a profile (editors). A regex that does not compile is rejected |
| `GET`, `PUT`, `DELETE /dataset/{key}/sector/profile/{id}` | Read, update, delete one profile |
| `GET /dataset/{key}/sector/profile/{id}/sector` | The sectors the profile selects right now |
| `GET /dataset/{key}/sector?profileKey={id}` | The same, combined with any other sector search filter |
| `GET /dataset/{key}/sector/{id}/settings` | The settings a sync of the sector uses, and where each comes from |

The publisher sectors profile:

```json
{
  "title": "Publisher sectors",
  "position": 2,
  "selector": {"modes": ["merge"], "anySectorPublisher": true},
  "settings": {"ranks": ["genus", "species", "subspecies", "variety", "form"]}
}
```

The effective settings of one sector, abbreviated:

```json
{
  "settings": {"ranks": ["genus", "species", "subspecies", "variety", "form"], "createImplicitNames": false, "...": "..."},
  "sources": {"ranks": "profile:3", "createImplicitNames": "profile:1", "code": "sector", "extinctFilter": "default",
              "issueExclusion": "profile:1,sector"}
}
```

## Releases

A release copies its project's profiles along with the sectors. A release sector's effective settings are resolved
against the profiles of its own release, i.e. as they were when the release was made.
