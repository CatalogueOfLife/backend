# Rank groups and vague rank coverage

Date: 2026-10-15
Status: implemented on `fix/matcher-rank-groups` (PR #1661) against name-parser-api 5.5.0-SNAPSHOT, not yet merged
or deployed. Needs the name-parser-api 5.5.0 release first.
Issues: https://github.com/CatalogueOfLife/backend/issues/1658, https://github.com/CatalogueOfLife/backend/issues/1659

## Problem

The matcher stopped vetoing a differing rank within the ranks above the family group and within the family group
(#1661), so Osteichthyes as a class matches a parvphylum and Bacteria a kingdom or domain. The groups were built by
hand in `UsageMatcher.sameSupragenericGroup` out of `isFamilyGroup()` and `isSuprageneric()`, which let SUPERGENUS
through: it is suprageneric but not family group, so it was lumped with ORDER and CLASS, and a supergenus never
vetoed an order of the same name although the genus group is meant to stay strict.

Next to it `RankComparator.compareVagueRanks` mapped each vague rank (SUPRAGENERIC_NAME, INFRAGENERIC_NAME,
INFRASPECIFIC_NAME, INFRASUBSPECIFIC_NAME) to the ranks it may stand for, again by hand. A survey of the backend
found ~170 rank checks, nearly all on name-parser-api predicates, with a few concepts re-coded repeatedly: a vague
rank's range, "same group", and "concrete rank" (about ten variants, some excluding only OTHER and UNRANKED, some
all vague ranks).

## Decision

The grouping lives in name-parser-api 5.5.0, as an additive change:

- `RankGroup` with SUPRAFAMILIAL, FAMILY, GENUS, SPECIES and INFRASPECIFIC, ordered high to low.
  The boundaries reuse the existing predicates: `isSuprafamilial()` (new, everything above GIGAFAMILY incl. FALANX),
  `isFamilyGroup()`, `isGenusGroup()` (incl. SUPERGENUS, DIVISION_BOTANY and the botanical sections and series),
  SPECIES_AGGREGATE with SPECIES, and `isInfraspecific()`. SUPRAGENERIC_NAME spans two groups and has none, nor
  have OTHER and UNRANKED.
- `Rank.getGroup()`, `sameGroup(Rank)`, `isConcrete()` (= `!isUncomparable()`) and `covers(Rank)`, which tells
  whether a vague rank includes another rank. SUPRAGENERIC_NAME covers the suprafamilial and family group ranks,
  INFRAGENERIC_NAME `isInfragenericStrictly()`, INFRASPECIFIC_NAME `isInfraspecific()` and INFRASUBSPECIFIC_NAME
  `isInfrasubspecific()`.

The backend uses it in the matcher only:

- `UsageMatcher.sameSupragenericGroup`: both ranks concrete and in the same SUPRAFAMILIAL or FAMILY group.
  Which groups are lenient stays the matcher's decision, not the library's.
- `RankComparator.compare`: a vague rank covering the other one, in either direction, is UNKNOWN.
  `compareVagueRanks` and the unused `RankRange` are gone; `TreeMergeHandler`'s upgrade of a vague merged rank to
  the concrete source rank calls `compare` instead.

## Behaviour changes

- A supergenus no longer matches an order or class of the same name with no rank veto.
- SUPRAGENERIC_NAME vs SUPERGENUS is DIFFERENT instead of UNKNOWN. That reaches `NameIdentity` (stable ids) and the
  merge's rank upgrade, but only for same-canonical pairs with a supergenus on one side, which hardly exist.
- Two vague ranks compare symmetrically: INFRASUBSPECIFIC_NAME vs INFRASPECIFIC_NAME used to be DIFFERENT in one
  order and UNKNOWN in the other, now it is UNKNOWN both ways.

## Rejected

- The four groups of the zoological code, with subspecies in the species group: the matcher keeps species and
  infraspecific names apart, and a name there often is a different taxon.
- Splitting infraspecific into a subspecies and an infrasubspecific group to give INFRASUBSPECIFIC_NAME its own
  group: `covers` handles that vague rank without it.
- A backend-only prototype to be moved upstream later: the API is small and GBIF's matching-ws is a natural second
  consumer.
- `ABOVE_FAMILY` / `SUPRA_FAMILY` as names: SUPRAFAMILIAL pairs with INFRASPECIFIC and reads less like SUPERFAMILY,
  a family group rank.

## Not done

A sweep of the rest of the backend: the "concrete rank" variants, the required name parts per rank group in
`NameInterpreter`, `NameValidator`, `FormattableName` and `NameFormatter`, hand-coded copies of library predicates
(`HierarchySync` `higherOrEqualsTo(GENUS)` is `isGenusOrSuprageneric()`), and the vague rank list hard coded in the
`ignoreAmbiguousRanks` SQL of `NameUsageMapper.xml`.
`NameFormatter.scientificNameHtml` and `LHigherName.getNameHtml` italicise by `ordinal() >= GENUS`, which includes
OTHER and UNRANKED names; whether unranked binomials should stay italic is still to be decided.
