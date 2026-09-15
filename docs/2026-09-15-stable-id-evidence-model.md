# Stable id generation for COL (x)releases

Date: 2026-09-15
Status: shipped, except the HTTP resolution of a superseded id and the loose-spelling fallback (see Not done).

## The problem

Every published release mints or reuses a stable usage identifier for each name usage. Those identifiers
are the public contract of ChecklistBank, so the only thing that matters is that the same name keeps the
same id.

Since the names index became canonical-only ([2026-07-06](2026-07-06-canonical-only-names-index.md)) the
candidate bucket is a pure canonical-name bucket, which left authorship, rank and status carrying *all*
the discriminating power — and they were compared far too crudely for that job.

`IdProvider.matchScore` added up points (rank +10, authorship +6, status +5, …) and any total above zero
won the id. Four things were wrong:

1. **It could not tell "these differ" from "we cannot tell."** Authorship was an exact string comparison
   after folding to ASCII, so `Mill.` and `Miller` were different authors; rank used `Objects.equals`, so
   an `UNRANKED` name forfeited the whole rank term rather than counting as no information. Adding or
   removing an authorship had been minting new identifiers for years — [#1326].
2. **There was no floor.** The only archived candidate of a canonical group won the id even when its rank,
   status and authorship all differed.
3. **Seniority was only a tie-break inside one score band.** A one-release-old id with a marginally better
   score beat a decade-old one outright — [#1289].
4. **The archive was a frozen first version.** `name_usage_archive` inserted an id once and afterwards only
   appended release keys, so every archived usage carried the name, authorship, rank, status and
   classification of the release that *first minted its id*. After any editorial correction the legitimate
   id lost the very attributes that identify it, and a duplicate carrying today's data outscored it.
   `XR_ONLY_PENALTY = 7` existed to paper over exactly this.

## Decisions

Four policy calls, taken with the maintainer before implementing:

- **Conservative on resurrections.** Reusing an id that was in the last release stays permissive;
  resurrecting one that has been gone needs a minimum evidence bar. A contradiction blocks reuse in both
  cases — that is what a contradiction means, and [#1326] says so in as many words: *"Changing authors
  should still receive a new identifier."*
- **Authorship compared three-valued** with `AuthorComparator`. `EQUAL` confirms, `UNKNOWN` (added or
  removed, or combination against basionym) never blocks, `DIFFERENT` never reuses.
- **The archive tracks the latest released version** of every id rather than the first.
- **A dead id records which id superseded it**, so a removed erroneous duplicate leaves a resolvable
  redirect rather than a hole.

And one asked for afterwards: **`name` records get stable ids too**, reusing the id of one of their own
usages.

## What it does now

`NameIdentity` (core/matching) answers, per attribute and three-valued, whether two versions of a usage
are the same name, on the comparators this codebase already trusts elsewhere — `AuthorComparator` and
`RankComparator`, both used by `UsageMatcher`. The scientific name is not compared at all: the caller has
already grouped both sides into one canonical names-index bucket, and that *is* "the same name string"
here.

A **contradiction** — a genuinely changed authorship, an incompatible rank, a disparate `TaxGroup`, a
misapplied name against a non-misapplied one, two different nomenclatural codes, a differing phrase on a
misapplied name — rules a pairing out. Missing information never does. What is left is graded
`CONFIRMED` (authorship *and* rank equal), `PLAUSIBLE` (one of them) or `WEAK` (neither), plus a count of
how many attributes positively agree.

`IdCandidate` replaces `ScoreMatrix` and orders the pairings. Evidence first and never outweighed; below
it seniority, and seniority is **longevity-based, not currency-based**: base-release-seen before xr-only,
then more releases, then earlier first release, then still-in-the-last-release, then lowest id, then the
name itself. Ids are handed out greedily, best pairing first — deliberately not a global optimum, because
for stability the strongest pairing must be locked in and never moved off its best partner to improve
some total.

Longevity outranking currency is what makes both [#1289] and the erroneous-duplicate case come out right.
An id that served twenty releases and was dropped in the last one is cited far more widely than the one
minted to replace it. And when two usages of one name coexist and one is later removed, the decade-old id
survives while last month's does not.

`XR_ONLY_PENALTY` is gone: an xr-only id is simply junior, which loses ties without ever ruling the id out.

Three further changes fall out of this:

- `NameUsageArchiver.archiveRelease` now refreshes the archived copy of every id the release still has, and
  re-points the archive match of a name whose scientific name changed — the nidx is the bucket candidates
  are grouped by, so an archived record has to move bucket with its name or it can never be found again.
  Only genuinely changed rows are rewritten.
- `XIdProvider.issue()` mints nothing but temporary ids. It used to score each merged usage against the
  archive on its own, so the first usage of a canonical group to be merged took the best id and the
  assignment depended on the order sectors happened to be merged in. The single `mapTempIds()` pass
  `XRelease` already ran at the end now does all of it, and it runs after `removeOrphans`, so no stable id
  is burnt on a usage dropped again in the same run.
- `idmap_name_<key>` is finally populated. Names take the stable id of one of their own usages, so for the
  overwhelming majority of names the name and its usage share an identifier and `/name/{id}` and
  `/taxon/{id}` denote the same name concept. Names are not matched a second time — their identity is
  derived from the usage mapping and inherits every stability property of it.

## Outcome

- `ReleasedId` gained `releaseCount` and `code` (~12-16 bytes per archived id; for COL scale that is worth
  measuring with the manual `ReleasedIdsTest.memory()` before the first big release). An earlier draft
  cached the parsed authorship on `ReleasedId` too, which would have retained a parsed `Name` per archived
  id for the whole run; the comparison facts are now built per canonical group and dropped with it.
- `createAllMatches` joined `name_match` on `release_keys[0]` while Postgres arrays are 1-based, so it had
  been silently matching nothing. Fixed to the last release key, which is the version the archived name is
  now kept at.
- Two `IdProviderTest` expectations changed premise rather than outcome. `baseReleaseKeepsItsIdOverXrOnlyId`
  still asserts the base id, now because xr-only is junior rather than because of a 7-point penalty.
  `xrOnlyIdReusedWithoutBaseCandidate` used to assert that *"even a weak match (rank, status & authorship
  all differ) is kept"*; that pairing is now contradicted, so the fixture was made to look like a real
  extended-to-base move and the old behaviour is pinned as its own contradiction test.
- `stableNameIds` is off by default. It changes every name id in a release at once — from a 22-character
  ShortUUID to a 7-character LATIN29 id — which is visible in the `NameID` column of every COLDP and DwC-A
  export, so it wants a heads-up to data users and goes to COL first.

## Migration

Two `dbschema.md` entries dated 2026-09-15: the `superseded_by` column plus the `usage_id_superseded`
staging table, and — no DDL but mandatory — a **one-off rebuild of every project's name usage archive**
before the first release after this deploy, because existing archives still hold first versions. Expect a
one-off burst of id churn on that first release, concentrated on names whose authorship or rank was
corrected since their id was minted, and near zero from then on. Diff `created.tsv` / `deleted.tsv` /
`resurrected.tsv` against the previous attempt before publishing it.

## Not done

- **Resolving a superseded id over HTTP.** The pairing is recorded and exported in `superseded.tsv`;
  making `/dataset/{key}/taxon/{oldId}` answer with a redirect is a separate change.
- **Spelling variation beyond what the nidx folds.** `SciNameNormalizer.normalize` stems and folds only the
  lowercase epithets, so `Aus albus` and `Aus alba` share a bucket but `Mammillaria` and `Mamillaria` do
  not, and churn there is unavoidable. The conservative fix is a second-chance pass keyed on
  `SciNameNormalizer.normalizeAll`, consulted only for usages that got a brand-new id from a bucket with no
  candidates at all and capped at `PLAUSIBLE` so it can never outrank a same-nidx match. Keeping a loose-key
  index in memory for the whole archive is not affordable, so it has to be an on-demand second streaming
  pass over `name_usage_archive` filtered by the small set of loose keys actually missed.
- **The report N+1.** `IdProvider.reportId` still issues one `NameUsageMapper.getSimple` per reported id.
  Streaming `processDatasetSimple` once per involved dataset and filtering by an `IntSet` would fix it, but
  the reports also drive the `unstable.txt` grouping and the restructure earns its own change.

[#1289]: https://github.com/CatalogueOfLife/backend/issues/1289
[#1326]: https://github.com/CatalogueOfLife/backend/issues/1326
