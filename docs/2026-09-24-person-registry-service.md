# Author matching as a service: the person registry in Postgres

Date: 2026-09-24
Status: implemented on branch feat/person-author-comparison on 2026-09-24, not merged or deployed. First of several
sub-projects, see "Scope".

The person registry of [2026-09-23-person-author-comparison.md](2026-09-23-person-author-comparison.md) resolves
author citations to persons with Wikidata, IPNI and ZooBank ids. Phase 3 there measured it as a comparator: on the
corpus it is a wash against the string comparison, but it tells relatives apart and resolves 89.5% of citations to a
person. That second property is worth offering on its own. Author matching becomes a tool of its own and, later, part
of name matching, so that names link to the persons who authored them.

## Purpose

Everyone below should get persons out of it:
- API clients linking names, who want person ids for the authors of their names - to link to BHL, Bionomia or
  Wikidata;
- our own datasets and releases, whose names should carry their persons: stored, exported, shown;
- curators, who look up who a citation is, find ambiguous or unknown authors and grow the registry;
- the name matching itself, whose comparator can use persons once the registry is better.

## The current implementation, measured

The registry of phase 3 is three TSV files committed in `core` (20 MB) and loaded into each JVM:

| | heap | |
|---|---|---|
| registry, 87,141 persons and 283k forms | 111 MB retained, loaded in 1.7 s | about 1M strings (70 MB), hash map entries (14 MB), per key records (18 MB), lists (8 MB), persons (6 MB) |
| resolver cache after the corpus's 79,342 distinct citations | +28 MB | about 350 bytes a citation, unbounded |

A fixed ~110 MB would sit in every JVM: the read-only and the read-write server, twice during a blue-green deploy.
The cache has no bound, so production matching over millions of distinct citations would take hundreds of MB. Every
change of the registry is a code release, and no database row can reference a person.

## Goals

- The registry lives in Postgres, is the single source of truth and costs every JVM only a bounded cache.
- Any id a person answers to - its own, a former id, a prefixed Wikidata, IPNI or ZooBank id - finds it in one
  indexed lookup.
- A single citation, or a whole authorship, can be matched over the API, narrowed by code, year and group.
- A server job keeps the registry in step with Wikidata and IPNI, following their corrections, while curated data
  always wins.

## Non-goals

- Persons in name matching results and jobs (sub-project 2).
- Persons stored for the names of our datasets and releases (sub-project 3).
- Curator tooling: worklists, editing persons, forms and relations over the API, overriding a single harvested value
  (sub-project 4).
- Switching the comparator to persons. The experiment keeps running on the in-memory store until the registry is
  better.
- A batch matching endpoint. Bulk linking belongs to the name matching jobs of sub-project 2.
- A ZooBank reader. The placeholder stays until a dump exists.

## Scope

Author matching splits into sub-projects that build on each other. This record designs the first:

1. **The registry in production and standalone author matching** - storage, loading, updates, the endpoints.
2. Persons in name matching: single and bulk match results carry the persons of each author slot.
3. Persons on our data: names of datasets and releases get their persons stored, exported and shown.
4. Curator tooling.
5. The comparator, which moves from the in-memory to the production registry once 1 exists.

## Design

### 1. Data model

Four tables, global rather than per dataset, mirroring today's files:

| table | columns |
|---|---|
| `person` | `id` text primary key (`wd:Q…`, `ipni:…`, `zb:…`, `clb:N`), `wikidata`, `ipni`, `zoobank`, `family`, `given`, `suffix`, `born`, `died`, `active_from`, `active_to`, `groups` (TaxGroup array), `source`, `retired` (date, null while a source has the person), `successor` (person id), `created`, `modified` |
| `person_id` | `any_id` text primary key, `person_id`: every id a person answers to - its own id, its former ids and its prefixed authority ids |
| `person_name` | `person_id`, `form`, `kind`, `code`, `source`, `key` (indexed) |
| `person_relation` | `person_id`, `relation`, `other_id`, `source`; primary key over the first three |

- `kind` is `STANDARD`, `CITATION`, `FULL`, `VARIANT` or the new `DERIVED`; `code` is `BOT`, `ZOO` or `ANY`, meaning
  what it means in `authormap.txt`; `relation` is `PARENT` or `SIBLING`; `source` is `WIKIDATA`, `IPNI`, `ZOOBANK` or
  `CURATED`. They become pg enums, in `dbschema.sql` and `dbschema.md` alike.
- `key` is the name form folded as citations are compared: `AuthorshipNormalizer.normalize`, repeated until it no
  longer changes (`PersonKeys.key`). The key is computed in Java on writing and on looking up, never in SQL.
- The derived forms - initials of the given names, of every full name or variant ending with the family name, the
  family name with its suffix, the bare family name - are stored as `DERIVED` rows. The writer computes them whenever a
  person or its forms change, so a lookup is a plain indexed select and nothing is derived on reading.
- A retired person keeps its row and its ids, so ids never vanish. Its harvested forms went with its source, so it is
  found by id and no longer by citation. `successor` names the person it was joined into, when a redirect or a join is
  known.

### 2. Where the code goes

The phase 3 registry sits in `core` only because it carried 20 MB of data, which no consumer of the `api` jar should
carry. With the data in Postgres the repository's pattern for an entity applies:

- `api`: the model `Person`, `PersonName`, `PersonRelation`, the enums as `PersonNameKind`, `PersonFormCode`,
  `PersonRelationType` and `PersonSource` (the plain names would be ambiguous next to the vocabularies already there),
  the event `PersonsChanged`.
- `dao`: the MyBatis `PersonMapper` and its XML, `PersonStore` with `PgPersonStore` and `MemoryPersonStore`,
  `PersonKeys`, the writer that derives forms, and the TSV reader and writer.
- `core`: `PersonResolver`, `PersonAuthorMatcher`, and the harvest, which moves from test scope to main.
- `webservice`: the resources and the admin endpoints.

### 3. Lookup

`PersonStore` is what everything resolves through:

- `get(anyId)` - one person by any id it answers to, through `person_id`;
- `byKey(key, code)` - the persons with a form under the key whose code applies;
- `byKeys(keys, code)` - the same for many keys in one `= ANY(?)` select, for bulk matching later;
- `relatives(person)`;
- `keys(person, code)` - the keys of a person's forms, for the comparator's fallback only.

`PgPersonStore` keeps two bounded Caffeine caches, key and code to person ids (about 100,000 entries) and id to person
(about 50,000). A miss is one indexed select; a hit costs microseconds. `MemoryPersonStore` is today's `PersonRegistry`
behind the same interface, built from TSVs; the tests, the relatives fixture and the corpus tools keep using it.

`PersonResolver` works on a `PersonStore`. Its narrowing by the year and group of a name is unchanged, its unbounded
cache goes: the store's caches replace it.

After a harvest or an import the writer publishes `PersonsChanged` through the event broker. The broker reaches every
live JVM - the read-only and the read-write server, both apps during a blue-green deploy - and each clears its caches.
The caches also expire an entry after an hour, in case an event is missed.

### 4. The API

Read only, on both servers:

- `GET /person/{id}` - a person with its forms and relations. `id` is any id the person answers to. 404 for none.
- `GET /person/match?q=Hook.f.&code=BOTANICAL&year=1867&group=Plants` - one citation; everything but `q` is
  optional. The answer holds the citation, its key, a status and the candidates, each with its ids, structured name,
  years and groups:
  - `RESOLVED` for one candidate, `AMBIGUOUS` for several, `UNKNOWN` for none;
  - `RULED_OUT` when there were candidates but the year or the group excluded all of them.
- `GET /person/match/authorship?q=(L.) Mill. ex DC., 1753&code=BOTANICAL&group=Plants` - a whole authorship. The
  name parser splits it; the answer holds one citation match, as above, for every author of `combination`,
  `combinationEx`, `basionym`, `basionymEx` and `sanctioning`, each slot narrowed by its own year. An authorship the
  parser cannot read is answered with the status `UNPARSABLE`.
- `GET /person/export` - the registry as a zip of the three TSVs.

An unknown `code` or `group` is a 400.

### 5. Updates: the harvest job

The harvest moves into `core` main as `PersonHarvestJob`, started by `POST /admin/persons/harvest`, optionally by a
cron that is off by default. It runs in the default lane under one serialization key, so two harvests never overlap.

1. **Fetch.** It reads Wikidata and IPNI as the phase 2 harvest does: serially, 1 s and 250 ms apart, retrying, the
   answers cached on disk. The cache lives in a directory of the run, so a failed run resumes while a later run never
   reads an earlier run's answers. No database session is open while it fetches: the 15 minute idle in transaction
   timeout would end a long transaction.
2. **Rebuild with the curated overlay.** In one short transaction that locks the tables against other writers it
   reads the registry and rebuilds it:
   - every harvested value, form and relation mirrors what its source says now: a changed year, name or group is
     updated, a form a source dropped goes;
   - curated persons, forms and relations are laid over it and always win;
   - the previous registry is only consulted for continuity of ids: former ids, Wikidata redirects, joins of two
     persons, and persons no source has any more, which are kept and retired; a retired person a source lists again is
     no longer retired;
   - the consistency checks of today's `problems()` must pass, or nothing is written and the job fails with its report.
3. **Write.** The rows are replaced by bulk copy, the derived forms and `person_id` included. Readers see the old
   registry until the commit. Then `PersonsChanged` is published.
4. **Report.** The review report is the job's download. It is a diff: every value changed old to new, every form added
   or removed, every person added, retired or joined, next to what the sources could not map and the rows of
   `authormap.txt` no person resolves.

This replaces the fill-only rule of phase 2, under which values froze at their first harvest and upstream corrections
only reached a report. A curated person still overrides a harvested one line by line; overriding a single harvested
value is left to the curator tooling.

The merge rules that stay: records and persons are joined on shared authority ids only, never on names; an authority
is always right about its own id; a second id an authority gives a person is a former id of it; where sources
disagree within a run IPNI wins for a person with an IPNI and no ZooBank id, ZooBank for one with a ZooBank and no IPNI
id, Wikidata otherwise; years that make a person impossible are left out.

### 6. Import and export

`POST /admin/persons/import` takes a zip of the three TSVs and writes it in the transaction of step 3, derived forms
and all. It is how the registry committed on the branch gets into the database. `GET /person/export` writes the
current registry back as the same three files, for review, backups and the in-memory store of the corpus tools.

### 7. Errors

- A bad `code` or `group` is a 400; an unparsable authorship is a status, not an error.
- A failed or inconsistent harvest writes nothing and leaves the registry as it was; the job fails with its report.
- With the database down the endpoints answer 503 like the rest of the API; the caches keep answering what they hold.

### 8. Testing

- `PgPersonStore` against Postgres, like the mapper tests: by key and code, by every kind of id, relatives, retired
  persons, and the caches cleared on `PersonsChanged`.
- The writer: derived forms and `person_id` rows from a person, recomputed when the person changes.
- The rebuild merge, as unit tests: an IPNI birth year changing, a form dropped, a person disappearing and being
  retired, a Wikidata redirect naming a successor, a curated line winning over a changed source.
- The resources: every status, the authorship slots, any id on `/person/{id}`, 400 and 404.
- Import and export as a round trip.
- The resolver, matcher and relatives fixture keep running on `MemoryPersonStore`.

### 9. Rollout

1. The schema migration in `dbschema.md`, then the deploy.
2. `POST /admin/persons/import` with the TSVs of the branch.
3. The TSVs come out of the repository; tests use small fixtures.
4. A first `POST /admin/persons/harvest`, its report read against the imported state.

## Rejected

- **The registry in memory from committed files**, as in phase 3: ~110 MB in every JVM, a release per change, no
  database references.
- **Committed files compiled into a memory mapped store** at startup: little heap, but still a release per change and
  no references.
- **Postgres with an in-memory key index** rebuilt at startup and after each harvest: the fastest lookups, but 40 to
  60 MB fixed per JVM again and a reload signal after every change.
- **Postgres with a sealed memory mapped key store** like the usage matcher's: near zero heap, but files handed between
  the two apps of a blue-green deploy, which the names index already has to work around.
- **A hand run harvest with an import job**: refreshing would need a developer.
- **Fill-only updates**: the registry would freeze at its first harvest.
- **A reviewed changeset before applying a harvest**: a curator step for every run, before any tooling exists for it.
- **A batch endpoint now**: bulk linking belongs to the name matching jobs.

## Outcome

Implemented as designed, with these decisions the design left open:

- Joins keep working through former ids: a person a harvest joins into another disappears into it and its id becomes a
  former id of the survivor, so the old id answers the survivor directly. `successor` is carried by files, tables and
  import and checked to resolve, but only curator tooling will set it.
- A curated person wins as a whole line: its empty cells are no longer filled from the sources, what they say
  otherwise is reported.
- A person a source still lists but no source names is retired too, or a single odd record would fail the
  consistency check of a whole harvest.
- Impossible years chosen from the sources leave all four years out.
- `/person/{id}` shows the forms sources and curators gave, not the derived ones; `RULED_OUT` lists the candidates the
  year or group excluded.
- The lookups select with `IN (...)` lists rather than `= ANY(?)`, as the other mappers do.
- `created` and `modified` survive the delete-and-copy rewrite: a person keeps `created`, and `modified` unless its row
  changed.
- The TSVs moved to `core/src/test/resources/authorship/persons/`, read by tests and the corpus tools only; removing
  them from the repository waits for the prod import.
- The harvest caches in the `cache` folder of `persons.harvestDir`, kept when a run fails reading the sources and deleted
  as soon as every source has been read. The import writes synchronously in its request.
