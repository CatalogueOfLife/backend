# The person registry

Authors of scientific names as persons, keyed by the identifiers authorities give them: Wikidata Q-ids, IPNI author
ids and ZooBank author ids. Nothing in production uses it yet. The person based author comparison that is meant to use
it, and to be measured against the string comparison first, is designed in
[2026-09-23-person-author-comparison.md](2026-09-23-person-author-comparison.md).

Code in `core`, package `life.catalogue.matching.person`. Files in `core/src/main/resources/authorship/persons/`.
The harvest that grows them is test scope, in `life.catalogue.matching.person.harvest`.

## The files

Three tab delimited files with a header that is verified on reading. Lists are pipe separated, an empty cell is null,
and every file is written sorted so a harvest diffs line by line.

| file | columns |
|---|---|
| `persons.tsv` | `id wikidata ipni zoobank formerIds family given suffix born died activeFrom activeTo groups source` |
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
  (Wikidata P835, ZooBank), `FULL` for the full name, `VARIANT` for any other spelling or alias.
- **`code`**: `BOT`, `ZOO` or `ANY`, meaning what it means in `authormap.txt`. A zoological name is cited by `ZOO` and
  `ANY` forms, any other by `BOT` and `ANY` forms: `Sw.` is Swartz in botany and nobody in zoology.
- **`relation`**: `PARENT`, where `person PARENT other` says that other is a parent of person, or `SIBLING`.
- **`source`**: `wikidata`, `ipni`, `zoobank` or `curated`.

`PersonRegistryFilesTest` guards the committed files: every reference resolves, ids and former ids are unique, every
id matches the authority ids of its person, nobody is born after they died or active before they were born, and every
person has a name form.

## Looking up a citation

`PersonRegistry.get()` loads the files once, on first use. `candidates(citation, code)` folds the citation with
`AuthorshipNormalizer.normalize`, the key citations are compared by everywhere, and returns every person with a form
under that key whose code applies. Keys are normalized until they no longer change, on the forms as on the citations:
normalizing twice can change a key (`McQueen`, `Saeed`, `Đinh`), and the comparator hands over authors normalized
already. Forms are derived per person with a family name when loading: the initials of the
given names with family name and suffix (`g b sowerby ii`), the same of every `FULL` or `VARIANT` form that ends with
the family name and suffix, the family name with its suffix (`hooker filius`) and the bare family name (`sowerby`). The
full names and variants count because Wikidata's given names often hold fewer names than its label - G. B. Sowerby II
has only `George` - and a variant may hold another spelling: `Karel Bořivoj Presl` gives `K. B. Presl`. Nobiliary
particles, which IPNI puts at the end of the forename, stay words: `Augustin Pyramus de` gives `A. P. de Candolle`.
Alternatives IPNI lists in brackets, `Carl (Karl, Carel, Carolus) Bořivoj`, give no initials. A bare surname therefore proposes every person of
that name; the registry only ever proposes candidates, it decides nothing.
`get(anyId)` resolves any id of a person and `relatives(person)` gives parents, children and siblings.

## Comparing authors as persons

`PersonAuthorMatcher` is an `AuthorMatcher`, the part of `AuthorComparator` that decides whether two author teams name
the same authors; the comparator keeps the years and the name structure. It is measured on the author corpus and not
used in production ([AUTHOR-CORPUS.md](AUTHOR-CORPUS.md)).

- `PersonResolver` resolves each author of a team, normalized as the comparator hands it over, to the registry's
  candidates under the name's code, cached per citation and code. It then narrows them. The name's year rules out a
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

## Harvesting

    mvn -q -pl dao -am install -DskipTests
    cd core
    mvn -q test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
      -Dexec.args="-Xmx4g -cp %classpath life.catalogue.matching.person.harvest.PersonHarvest src/main/resources/authorship/persons target/person-harvest"

It reads the three files, reads every source, merges, and writes the files back only if the result passes the same
checks as `PersonRegistryFilesTest`. Every answer of Wikidata and IPNI is cached in `target/person-harvest/cache`, so a
run that dies resumes where it stopped; delete the cache for a fresh harvest. It pauses 1 s between Wikidata and
250 ms between IPNI requests and retries with a growing pause. An answer that is no complete JSON object, or that is
an API error, counts as a failed request: the query service sometimes answers 200 with a body cut
off. Such an answer is retried and never cached.

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
few seconds. The cached API answers take a gigabyte or two. Items of the files that disappeared are asked the query
service for a redirect, which moves the person to the new Q-id.

**IPNI** has no bulk download, but its author search pages with a cursor and stops after 10,000 records per query. It
is asked by surname prefix, A to Z, splitting a prefix with more authors into longer ones. It gives the standard form
(`STANDARD`, `BOT`), forename and surname (`FULL`), alternative names (`VARIANT`), dates and taxon groups. Suppressed
records are skipped. Authors whose surname starts with no letter A to Z are not asked for: the report compares IPNI's
total with what was harvested.

**Merging** joins records and persons on shared authority ids only, never on names; Wikidata's P586 and P2006 are what
link a Wikidata item to an IPNI or ZooBank author. An authority is always right about its own id: a record joins the
person holding its own id, whatever else it links to, and a linked id another person holds is reported, not taken. It
fills empty cells and adds lines, it never overwrites a value. Where the records of one run disagree, IPNI wins for a
person with an IPNI and no ZooBank id, ZooBank for one with a ZooBank and no IPNI id, Wikidata otherwise; years within 2
of each other agree. Years that would make a person impossible - born after death, active before birth - are left out
and reported: unknown beats wrong. Two items of one authority that claim the same id of another stay two persons. A new
person without any name form is not written.

Two persons of the files become one when a record links them or a Wikidata redirect moves one onto the item of the
other: the person holding the record's own id, or the redirect's target, keeps its values, and every differing value
of the other is reported as dropped. Its line goes, its id becomes a former id and its name lines follow it. A curated
person is never joined with another; the link is reported instead.

**The report**, `target/person-harvest/report.txt`, lists what needs a person: the disagreements between sources, the
values of the files a source now gives otherwise, the ids claimed twice, the persons of the files no source has any
more, what the sources could not map (fields of work, IPNI taxon groups, dates) and the rows of `authormap.txt` no
person resolves. As a harvest never overwrites a value, the second list is the only place an upstream correction shows
up.

## Curating by hand

A line with `source` `curated` is the way to add what no authority has: an alias the report shows missing, a relation
the authorities lack, or a person with no authority id at all, who gets a `clb:N` id, N one above the highest in use.
A harvest never removes a curated line nor changes its values, and never joins a curated person with another. Its ids
still follow the authorities: a Wikidata redirect or a newly linked authority id changes `wikidata` and `id`, the old id
going to `formerIds`, and curated name and relation lines keep resolving through it. Run `PersonRegistryFilesTest`
after editing.

## ZooBank

ZooBank has no bulk access. Author dumps have been requested, and `ZooBankDumpSource` is the placeholder for them: the
harvest takes `--zoobank <dump>` and refuses to run with it until the reader for the dump's format is written. What
it maps to is fixed: the author UUID becomes the `zb:` id and the `zoobank` column, the names ZooBank cites the author
by become `CITATION`/`ZOO` forms and other name records of the author `VARIANT` forms, the lifespan becomes `born` and
`died`, and a Wikidata, IPNI or ORCID link in the dump is a join key. Until then ZooBank ids come through Wikidata's
P2006 only.
