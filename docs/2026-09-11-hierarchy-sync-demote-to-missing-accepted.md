# Hierarchy sync: demote to an accepted taxon the project lacks

Date: 2026-09-11
Status: implemented on branch `feat/hierarchy-demote-missing-accepted`, not yet released
Issue: [backend#1582](https://github.com/CatalogueOfLife/backend/issues/1582)

## Problem

The Archis project (316318) delegates its higher classification to the latest COL XRelease through a
hierarchy sector. Its names come from two text tree datasets that carry COL identifiers added on import
(`add identifiers from: 3LXR`). Two names from the issue, checked against COL26.8 XR (316165):

| project usage | identifier | that id in COL | accepted in COL | in the project? |
|---|---|---|---|---|
| *Gyraulus crista* | `col:3HYBL` | *Gyraulus (Armiger) crista*, synonym | *Armiger crista* | no |
| *Hieracium pilosella* | `col:3LS95` | *Hieracium pilosella* L., synonym | *Pilosella officinarum* | no |

Both stayed accepted, and *Gyraulus crista* was moved under the genus *Armiger*:

1. Phase 1 collected the synonym's source chain and rewired the still accepted project usage under the
   closest ancestor the project held - the imported genus of the accepted species.
2. Phase 2 wanted to demote it, but the source's accepted taxon had no project equivalent. It logged
   "cannot demote" at INFO and left the usage accepted.

A demote only ever worked when the project happened to hold the accepted taxon already. Full name matches
could not demote at all: the 2026-06-26 name match fallback deliberately used them for placement only.

## Goal

- Import the source's accepted taxon (below genus) when the project lacks it, tagged with the hierarchy
  sector like every imported ancestor, and demote the project usage to its synonym.
- Treat full name matches (EXACT, VARIANT) like identifier matches: status, synonymy and authorship follow
  the source, and the usage gains the source identifier.

## Non-goals

- Dangling references after a re-sync. `deleteOld` removes the imported accepted every run and re-imports it
  under a new id. A demoted synonym is re-pointed on the next run because it is matched again - by the
  identifier it carries - but anything no longer matched keeps a dangling parent. Stable ids and a repair step
  are a separate change.
- Placement-only matches (CANONICAL, HIGHERRANK, snapped and ambiguous) keep their current behaviour.
- Synonyms are still not name matched.

## Design

- **Missing accepted.** `collectAncestors` sees a matched source synonym whose accepted taxon is ranked below
  genus and not matched by any project usage. It adds the accepted to the ancestors to import and records its
  source ancestor chain. `insertAncestorsTopDown` waits for any pending ancestor on that chain, because the
  direct source parent (a species or subgenus) is usually never collected. It places the import under the
  closest ancestor the project holds, and dedups it against an existing accepted project usage of that name and
  rank. That also happens on a re-run, where the demoted usage is a project synonym of a source synonym whose
  accepted was just deleted.
- **No premature rewire.** An accepted project usage matched to a source synonym is not rewired in phase 1 any
  more. Phase 2 demotes it; if it cannot, the usage stays where it was instead of landing in a foreign genus.
- **Promoted name matches.** An EXACT or VARIANT match that is not snapped and whose source usage no identifier
  match or earlier name match claimed joins `projectMatches`. A new pass adds the source identifier. Without
  it a demoted usage would be lost on the next run: it is a synonym and never name matched again. These usages
  are no longer flagged `MATCHING_HIGHERRANK`, which describes a higher rank placement.
- **Stale identifiers.** A usage may now carry a stale id next to the current one, so identifier discovery uses
  the first id that still resolves.
- **Phase 2 order.** Promotions run before demotions. With inverted synonymy the accepted a demotion needs
  exists only as a project synonym awaiting promotion, and the outcome used to depend on map order.
  Unresolvable demotions and promotions are logged at WARN and summarised as a sector sync warning.

## Rejected alternatives

- **Only flag an unresolvable demote.** The project would never converge on the source; every re-sync would
  report the same names.
- **Keep full name matches placement only.** The Archis names happen to carry identifiers, but names without
  one would stay accepted wherever the source has them as synonyms.
- **Promote name matches without adding the identifier.** Breaks on the second run, see above.
- **Also promote CANONICAL matches, or snaps.** A canonical match ignores authorship, and a snap is the matcher
  picking one of several candidates. A demotion based on either can move a homonym.
- **Relink children around `deleteOld` now.** Deferred to the stable id change.

## Outcome

- `HierarchySyncIT` covers the two identifier and name match shapes of the issue, their re-runs, reuse of an
  accepted the project already holds, an infraspecific accepted whose species is missing, and a guard that an
  authorship conflict still ends in a higher rank placement.
- Two existing tests changed their expectations on purpose:
  - `nameMatchFullMatchPlacesUnderGenus` and `nameMatchRescuesStaleIdentifier`: a full match now gains the source
    identifier and is not flagged `MATCHING_HIGHERRANK`.
  - `invertedSynonymyDoesNotCreateCycle`: with promotions first the pair now ends up exactly as in the source
    (B accepted, A its synonym). It used to only assert that no cycle formed, which order dependent code could
    reach by leaving both unchanged.
- The Archis identifiers point at COL synonyms (checked for both names of the issue on 2026-09-11), so the real
  cases take the identifier path; the name match promotion is for names without one.
- Not done here: stable ids for re-imported rows and a repair of dangling parents after a sync and before a
  release, including `ProjectRelease`, which aborts in `IdProvider` on a missing parent today.
