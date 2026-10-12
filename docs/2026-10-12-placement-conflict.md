# Placement conflicts and genus homonyms of disparate groups

Date: 2026-10-12
Status: implemented on `feat/placement-conflict-1608`, not yet deployed. Needs the `dbschema.md` migration of the same
date before the deploy.
Issue: https://github.com/CatalogueOfLife/backend/issues/1608

## Problem

The extended release merge attached species to homonym genera of another kingdom or order, and the pick changed from
one release to the next, costing stable ids every XR: *Platygaster mayi* (ZooBank) under a fly *Platygaster*, the
bacterium *Paracoccus rhizosphaerae* under the scale insect *Paracoccus*, *Curvularia* species under Poaceae,
*Leishmania* under Psychodidae.

The genus homonym rule of #1601 only runs for an authored incoming genus against authored candidates. Every failing
case lacked authorship on one side:

1. Literature sources, mostly Plazi articles, file an unauthored genus under the host or vector of the paper's subject.
   The source data is wrong, the merge took it over, and the unauthored genus then attracted every later species of
   that name.
2. Species from nomenclators and single name datasets arrive with nothing but their genus name. Their implicit genus
   has no classification, so its group is unknown or only eukaryotes, which contradicts nothing.
3. A synonym genus homonym takes the species to its accepted genus in another group.
4. The last tie break picked the first candidate of a hash set, so the choice flipped between releases.

## Decision

Two layers, agreed in the session of 2026-10-12.

**Shield at the source.** Wrong placements are a property of the source, so they are caught once, at import time,
where they can be reviewed - not decided again in every merge. `PlacementChecker` flags an accepted genus with the new
`Issue.PLACEMENT_CONFLICT` if its group in the source is disparate to the group of every genus of that name in a
reference checklist, the latest public COL release by default. Every import runs it, `PlacementCheckJob` runs it on
datasets already imported, and `GET /dataset/placementConflict` reports the datasets with at least a share (default
10%) of conflicting accepted genera. Only accepted genera are ever flagged, so that share needs no new metric: it is
the import's `PLACEMENT_CONFLICT` issue count over its accepted `GENUS` count, both already in `dataset_import`.

**Harden the merge.**

- A flagged genus is matched without its source classification and attaches to the project genus of its name if
  there is a single one, without updating it; otherwise it is skipped with its subtree. It is never created under
  the host.
- The matcher does not guess between candidates of disparate groups that nothing told apart; it reports an
  unresolved homonym and the merge skips the name - or the species whose implicit genus it was - as
  `AMBIGUOUS_HOMONYM`.
- The lowest group of the source dataset's `taxonomicGroupScope` is the fallback group of names that tell none.
- Candidates are ordered by id before matching, so the remaining tie breaks are stable.

## Rejected

- **Analysing the dataset title** for a group, proposed on the issue. The title of a host paper names the host:
  "Diseases of *Cymbopogon citratus* (Poaceae) in China: *Curvularia nanningensis* sp. nov." yields Angiosperms.
- **Excluding flagged genera with a sector profile's `issueExclusion`.** The merge pushes a usage's match on the
  parent stack before it decides to ignore it, so the species would end up under the host family instead.
- **Gating whole datasets in the XR** above a conflict share. Reported only, for curators and data publishers.
- **Rejecting every `AMBIGUOUS` implicit genus match**, as first planned. An `AMBIGUOUS` match that survived the new
  guard is either backed by a shared classification or picks among candidates of one lineage; rejecting it would
  create a duplicate genus instead.
- **Guarding the implicit genus of a species with `isAmbiguousGenus`**, as first planned. Refusing implicit names
  for species with vague parents creates the species without any genus.

## Outcome

- The reference is configurable as `importer.placementReference` with the `ADD_IDENTIFIERS_FROM` syntax, default
  `3LR`. A reference that cannot be resolved only disables the check, it never fails an import.
- Not solved: a species whose only same-named genus in the project is a synonym in another group (*Oribates* →
  *Gyros*, *Celaeno* → *Noctilio*) when no accepted genus of that name exists yet, as long as neither the record nor
  its dataset tells a group. The matcher prefers an accepted candidate, which covers the case once the right genus
  exists.
- A source whose codes are inferred from its authorship style can still mislead: `Jung & Lee, 2021` reads as
  zoological, which makes a bacterium a eukaryote.
- Follow-up: investigate IRMNG as a second reference. It is more complete at genus level and holds many more
  unavailable names and homonyms than the COL base release.
