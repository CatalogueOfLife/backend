# The person registry

Authors of scientific names as persons, keyed by the identifiers authorities give them: Wikidata Q-ids, IPNI author
ids and ZooBank author ids. The registry lives in Postgres and is served by the API: a person by any of its ids, and
author citations or whole authorships matched to persons. The person based author comparison that reads it is measured
against the string comparison and not used in production. Design records:
[2026-09-23-person-author-comparison.md](2026-09-23-person-author-comparison.md) and
[2026-09-24-person-registry-service.md](2026-09-24-person-registry-service.md).

The model and its vocabularies are in `api` (`Person`, `PersonName`, `PersonRelation`, `PersonNameKind`,
`PersonFormCode`, `PersonRelationType`, `PersonSource`), storage and lookup in `dao` (package
`life.catalogue.matching.person`), resolving, comparing, matching and the harvest in `core`, the resources in
`webservice`.

## The registry

Four tables, global rather than per dataset: `person`, `person_id` (every id a person answers to - its own, its former
ids and its prefixed authority ids - with its own id), `person_name` (its forms, each with the key it is looked up by)
and `person_relation`. `GET /person/export` writes them as three files in a zip, the format
`POST /admin/persons/import` reads and the corpus tools load (`PersonFiles`). The files are tab delimited with a header
that is verified on reading, lists are pipe separated, an empty cell is null, and every file is written sorted so two
exports diff line by line. The persons file of phase 3, without the last two columns, still reads.

| file | columns |
|---|---|
| `persons.tsv` | `id wikidata ipni zoobank formerIds family given suffix born died activeFrom activeTo groups source retired successor` |
| `names.tsv` | `person form kind code source` |
| `relations.tsv` | `person relation other source` |

- **`id`** is the best identifier a person has, prefixed: `wd:Q…` with a Wikidata id, else `ipni:…`, else `zb:…`, else a
  curated `clb:N`. A person that gains a better id - an IPNI author Wikidata links later - keeps the old one in
  `formerIds`, so every line that refers to it keeps resolving. A second id an authority gives the person is a former id
  too: Wikidata lists two IPNI ids for IPNI's duplicate records of one author, and the IPNI records of both make one
  person. Lines may refer to a person by any of its ids, its former ids or a prefixed authority id.
- **`family`, `given`, `suffix`** are the structured name as an authority records it: IPNI's surname and forename (the
  filius `f.` from the standard form), or Wikidata's family and given names (P734, P735) put in the order of the
  label, with a trailing `I` to `IV`, `Jr.` or `Sr.` of the label as suffix. They are never split off a full name: the
  last word is not the surname in `Geoffroy Saint-Hilaire` or `Ruiz López`. Wikidata's several family names of one
  person are alternatives - a maiden and a married name, a latinised one - so only those its label holds are kept, none
  that is part of another it holds (`Pickard` of `Pickard-Cambridge`), else the first.
- **Years** only: `born`, `died`, and the active years `activeFrom`/`activeTo` for a floruit. A single floruit year
  is both. A Wikidata date less precise than a year is no year: `+2000-00-00` at century precision is the 20th century,
  not 2000, and is left out.
- **`groups`**: `TaxGroup` values from what an authority records, never mined from our own names. IPNI's taxon groups
  map `Spermatophytes` to angiosperms and gymnosperms, `Mycology` to fungi and the rest by their name; `Fossils` and
  `Pre-Linnaean` are no group. Wikidata's field of work (P101) maps by its English label, `botany` to plants and
  fungi, `malacology` to molluscs and so on (`Groups` holds the table).
- **`kind`**: `STANDARD` for a botanical standard form (IPNI, Wikidata P428), `CITATION` for a zoological author citation
  (Wikidata P835, ZooBank), `FULL` for the full name, `VARIANT` for any other spelling or alias. `DERIVED` rows exist
  only in the table: the forms `PersonForms` derives, see below.
- **`code`**: `BOT`, `ZOO` or `ANY`, meaning what it means in `authormap.txt`. A zoological name is cited by `ZOO` and
  `ANY` forms, any other by `BOT` and `ANY` forms: `Sw.` is Swartz in botany and nobody in zoology.
- **`relation`**: `PARENT`, where `person PARENT other` says that other is a parent of person, or `SIBLING`.
- **`source`**: `wikidata`, `ipni`, `zoobank` or `curated`.

- **`retired`**: the day a harvest found no source that has the person, or none that names it, any more. A retired
  person keeps its row and its ids and is found by them; its harvested forms went with its source and it derives no
  forms, so a citation only finds it through a curated form. A source naming it again ends the retirement.
- **`successor`**: the id of the person a retired one was joined into, where a curator knows it. A harvest sets none: a
  person it joins into another disappears into it, its id a former id of the other, so the old id finds the survivor.

Every registry is checked before it is written, by the harvest and the import alike (`MemoryPersonStore.problems()`):
every reference resolves, ids and former ids are unique, every id matches the authority ids of its person, nobody is
born after they died or active before they were born, every successor exists, and every person that is not retired has
a name form. `PersonRegistryFilesTest` runs the same checks on the files in `core/src/test/resources/authorship/persons/`,
which the tests and the corpus tools read.

## Looking up a citation

Everything resolves through a `PersonStore`: `PgPersonStore` on the servers, a `MemoryPersonStore` built from the
files in tests and the corpus tools. `candidates(citation, code)` folds the citation to its key and returns every person
with a form under that key whose code applies. The key (`PersonKeys.key`) is `AuthorshipNormalizer.normalize`, the key
citations are compared by everywhere, repeated until it no longer changes: normalizing twice can change a key
(`McQueen`, `Saeed`, `Đinh`), and the comparator hands over authors normalized already. It is computed in Java when the
registry is written and when it is read, never in SQL. `PersonForms` derives forms per person with a family name, which
the writer stores as `DERIVED` rows: the initials of the
given names with family name and suffix (`g b sowerby ii`), the same of every `FULL` or `VARIANT` form that ends with
the family name and suffix, the family name with its suffix (`hooker filius`) and the bare family name (`sowerby`). The
full names and variants count because Wikidata's given names often hold fewer names than its label - G. B. Sowerby II
has only `George` - and a variant may hold another spelling: `Karel Bořivoj Presl` gives `K. B. Presl`. Nobiliary
particles, which IPNI puts at the end of the forename, stay words: `Augustin Pyramus de` gives `A. P. de Candolle`.
Alternatives IPNI lists in brackets, `Carl (Karl, Carel, Carolus) Bořivoj`, give no initials. A bare surname therefore proposes every person of
that name; the registry only ever proposes candidates, it decides nothing.
`get(anyId)` resolves any id of a person, `byKeys` many keys at once and `relatives(person)` gives parents, children
and siblings.

`PgPersonStore` keeps two bounded caches, key and code to person ids and any id to person, 100,000 and 50,000 entries,
each kept an hour at most (`persons` in the config: `keyCacheSize`, `personCacheSize`, `cacheExpireMinutes`). A miss is
one indexed select. A harvest or an import publishes `PersonsChanged` through the event broker, which reaches every
live server, and each clears its caches.

## Comparing authors as persons

`PersonAuthorMatcher` is an `AuthorMatcher`, the part of `AuthorComparator` that decides whether two author teams name
the same authors; the comparator keeps the years and the name structure. It is measured on the author corpus and not
used in production ([AUTHOR-CORPUS.md](AUTHOR-CORPUS.md)).

- `PersonResolver` resolves each author of a team, normalized as the comparator hands it over, to the registry's
  candidates under the name's code. It then narrows them. The name's year rules out a
  person when it lies before `born + minAge` or after `died + posthumous`; without life dates the active years do the
  same with `activeSlack` on either side. `Margins.DEFAULT` is 10, 20 and 15 years. An imprecise year such as `184?`
  narrows nothing. The name's group rules out a person whose groups are all disparate to it. A person without years
  or groups is never ruled out, and a citation whose candidates are all ruled out is unresolved.
- Two resolved authors are `EQUAL` when they share a person and `DIFFERENT` when they name unrelated persons. Relatives
  - a parent, a child, a sibling - get the relatives policy, `UNKNOWN` or `DIFFERENT`. The comparator combines that
  with the years: under `UNKNOWN`, relatives citing the same year end `EQUAL`, a few years apart `DIFFERENT`, and without
  years `UNKNOWN`.
- An author that resolves to nobody goes to the string comparison, against itself or, if the other side resolved,
  against every form of the other side's persons the string comparison can read. `L. Cox` thus meets the `L. R. Cox`
  derived for the person `Cox` names. Comma forms such as `gaimard, j p` and forms of initials only such as `j t s` are
  left out: they parse to a one letter surname, which the comparison takes for the start of any surname. Only this
  fallback depends on the comparator's mode.
- Teams keep the rule of the string comparison: any author of one that is any author of the other makes them `EQUAL`.
- `explain` returns what every author pair resolved to and which rule decided it: `IDENTICAL` teams, `IDENTITY`,
  `RELATIVES` or `FALLBACK`. A listener given to the matcher sees every explanation.

## The API

Read only, on both servers:

- `GET /person/{id}` - a person with its forms and relations. `id` is any id the person answers to: `wd:Q157501`,
  `ipni:4084-1` or a former id. The forms are those the sources and curators gave, not the derived ones. 404 for none.
- `GET /person/match?q=Hook.f.&code=botanical&year=1867&group=Angiosperms` - one author citation; everything but `q`
  is optional. The answer holds the citation, the key it was looked up by, a status and the candidates: `RESOLVED`
  with one person, `AMBIGUOUS` with several, `UNKNOWN` with none, and `RULED_OUT` with the persons the year or the
  group excluded, narrowed as `PersonResolver` narrows them.
- `GET /person/match/authorship?q=(L.) Mill. ex DC.&code=botanical` - a whole authorship, split by the name parser.
  The answer holds one citation match per author of `combination`, `combinationEx`, `basionym`, `basionymEx` and
  `sanctioning`; the combination and its ex authors are narrowed by the year of the combination, the basionym and its
  ex authors by the year of the basionym. An authorship the parser cannot read has the status `UNPARSABLE` and no
  matches.
- `GET /person/export` - the registry as a zip of its three files.

`code` takes what the rest of the API takes for a nomenclatural code, `group` a `TaxGroup`; an unknown value, or a
`year` that is no number, is a 400.

## Harvesting

`POST /admin/persons/harvest` starts a `PersonHarvestJob`. With `persons.harvestIntervalDays` set, the cron executor
checks once a day and starts one when the last successful harvest finished that many days ago: a deploy restarts every
schedule, so the job history decides, not the time the server started. It runs in the default lane, one at a time.

1. **Fetch.** It reads Wikidata and IPNI with no database session open, serially, pausing 1 s between Wikidata and
   250 ms between IPNI requests and retrying with a growing pause. An answer that is no complete JSON object, or that
   is an API error, counts as a failed request: the query service sometimes answers 200 with a body cut off. Such an
   answer is retried and never cached. Every other answer is cached in the `cache` folder of `persons.harvestDir`: a
   run that fails while reading leaves it for the next to resume from, and once every source has been read it is
   deleted, so no later harvest replays an earlier one's answers, even one that failed afterwards. Items of the registry
   that no record carries any more are asked the query service for a redirect.
2. **Rebuild.** In one short transaction that keeps other writers out - readers see the old registry until it
   commits - it reads the registry, merges the harvest into it and checks the result.
3. **Write.** A consistent result replaces every row by COPY, derived forms and the any id index included, and
   `PersonsChanged` is published. An inconsistent one writes nothing and fails the job. So does a harvest that would
   retire more persons than `persons.maxRetired` (1000): a source may have answered too little. For a real cleanup of a
   source, `POST /admin/persons/harvest?force=true` writes it anyway.
4. **Report.** Either way the job's download is a zip holding `report.md`.

**Wikidata** gives everyone with a botanist author abbreviation (P428, `STANDARD`/`BOT`), a zoologist author citation
(P835, `CITATION`/`ZOO`), an IPNI author id (P586) or a ZooBank author id (P2006), paged one property at a time
through the query service. Everything else comes from the Wikidata API (`wbgetentities`, 50 items at a time,
serially and without `maxlag`, which counts the lag of the query service and refused every request while it was
loaded): the English label (`FULL`) and aliases (`VARIANT`) of every person and its statements - family and given
names (P734, P735), birth and death (P569, P570), active years (P2031, P2032, P1317), field of work (P101), parents
(P22, P25) and siblings (P3373), without deprecated ones - and then the labels of the name and field items. An item
without an English label is asked again for its `mul` label, Wikidata's label for all languages, which many name items
and persons carry instead. The query
service took half a minute to a minute for the statements or labels of a hundred persons, the API answers fifty in a
few seconds. The cached API answers take a gigabyte or two. Items of the registry that disappeared are asked the query
service for a redirect, which moves the person to the new Q-id.

**IPNI** has no bulk download, but its author search pages with a cursor and stops after 10,000 records per query. It
is asked by surname prefix, A to Z, splitting a prefix with more authors into longer ones. It gives the standard form
(`STANDARD`, `BOT`), forename and surname (`FULL`), alternative names (`VARIANT`), dates and taxon groups. Suppressed
records are skipped. Authors whose surname starts with no letter A to Z are not asked for: the report compares IPNI's
total with what was harvested.

**Merging** follows the sources. Records and persons are joined on shared authority ids only, never on names;
Wikidata's P586 and P2006 are what link a Wikidata item to an IPNI or ZooBank author. An authority is always right about
its own id: a record joins the person holding its own id, whatever else it links to, and a linked id another person
holds is reported, not taken. Every harvested value, form and relation is what the sources say now: a changed year,
name or group is updated, a form or relation no source gives any more goes. Where the records of one run disagree,
IPNI wins for a person with an IPNI and no ZooBank id, ZooBank for one with a ZooBank and no IPNI id, Wikidata
otherwise; years within 2 of each other agree. Years that would make a person impossible - born after death, active
before birth - are all left out and reported: unknown beats wrong. Two items of one authority that claim the same id of
another stay two persons. A new person without any name form is not written. The registry of before only carries ids
on: a person no source has any more, or that no source names, is kept and retired, and a retired person a source names
again is no longer retired.

Two persons become one when a record links them or a Wikidata redirect moves one onto the item of the other: the
person holding the record's own id, or the redirect's target, stays, and every differing value of the other is
reported as dropped. The other's id becomes a former id of the one that stays, so it still finds it.

**The report** is a diff against the registry of before - every value changed old to new, every person added, joined,
retired or back, every form and relation added or removed - next to what needs a person: the curated values the
sources give otherwise, the disagreements between sources, the ids claimed twice, what the sources could not map
(fields of work, IPNI taxon groups, dates) and the rows of `authormap.txt` no person resolves. An inconsistent result
lists its problems at the top.

## Curating by hand

A line with `source` `curated` is the way to add what no authority has: an alias the report shows missing, a relation
the authorities lack, or a person with no authority id at all, who gets a `clb:N` id, N one above the highest in use.
Curated lines win: a harvest never removes a curated form or relation, keeps a curated person's values as they are -
empty ones included - and reports what the sources say otherwise, and never joins a curated person with another. Its
ids still follow the authorities: a Wikidata redirect or a newly linked authority id changes `wikidata` and `id`, the
old id going to `formerIds`, and curated lines keep resolving through it.

Until curator tooling exists, curating is a round trip: `GET /person/export`, edit the files, zip them and
`POST /admin/persons/import` the zip. The import runs the checks above, writes nothing for an inconsistent registry (a
400 listing its problems), and publishes `PersonsChanged` once it has written.

## ZooBank

ZooBank has no bulk access. Author dumps have been requested, and `ZooBankDumpSource` is the placeholder for them: reading
it throws until the reader for the dump's format is written, and the harvest job does not read it yet. What it maps to
is fixed: the author UUID becomes the `zb:` id and the `zoobank` column, the names ZooBank cites the author
by become `CITATION`/`ZOO` forms and other name records of the author `VARIANT` forms, the lifespan becomes `born` and
`died`, and a Wikidata, IPNI or ORCID link in the dump is a join key. Until then ZooBank ids come through Wikidata's
P2006 only.
