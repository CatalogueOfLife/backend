# A softer taxonomic group veto, never in merges

Date: 2026-10-15
Status: implemented on `fix/group-veto`, not yet merged or deployed.
Issue: https://github.com/CatalogueOfLife/backend/issues/1650

## Problem

`UsageMatcher` removes every candidate whose taxonomic group (`TaxGroup`, analyzed from its classification) is
disparate to the group of the query's classification. That keeps real homonyms apart: the bird and the plant
*Oenanthe*, the nematode and the fungus *Capillaria*. In the protist, algae and fungi area the veto misfires on
misfiled data:
- monitoring data files plankton under `kingdom=Animalia`
- lichens and rusts come under `Plantae`
- green algae come under `Fungi`

*Tripos furca* with `kingdom=Animalia` lost both of its Chromista usages, and the higher rank fallback lost the genus
*Tripos* too, so it ended at the kingdom Animalia. GBIF matches it exactly.

In the GBIF impact comparison against release 315557 (COL26.6 XR), the kingdom conflicts were:
- 955 inputs / 308k occurrences sent as `Animalia` and matched by GBIF to a Chromista taxon
- 323 inputs / 18k sent as `Plantae` and matched to fungi

Bacteria went the other way: GBIF matched an animal or plant homonym where CLB kept the bacterium. There the veto is
right.

## Merges first

Matching drives merges, the most important thing done when building COL. A false merge is not acceptable. It is much
worse than a duplicate species under another classification. So the veto stays strict wherever something is written,
and only matching that writes nothing gets more lenient.

## Decision

Two rules:

1. **Everywhere, merges included.** A bi- or trinomial whose authorship compares EQUAL (`AuthorComparator`) to the
   candidate's is the same name, whatever group its placement suggests.
   - Real homonyms of bi- and trinomials across groups have different authors: the wasp *Agathis montana* Shestakov
     and the plant *Agathis montana* de Laub., the gastropod *Ficus variegata* Röding and the plant *Ficus variegata*
     Blume.
   - It generalises #1430, which needed the identical authorship string, so "(Ehrenb.) F.Gómez" now equals
     "(Ehrenberg) F.Gómez".
   - `NameIdentity` follows the same principle for stable ids.
2. **Only in the RELAXED mode of read-only matching.** A conflicting group no longer removes a candidate. The candidate
   only loses against candidates of a compatible group. It applies:
   - to a bi- or trinomial whose query group is a top level one (Animals, Plants, Fungi, Protists, Bacteria,
     Eukaryotes, ...), i.e. the query gave little more than a kingdom
   - to any name between overlapping groups (below)
   - never to a candidate with a DIFFERENT authorship

   A specific query group keeps the veto. *Sabia parviflora* given in the plant family Sabiaceae must not match the
   only, bad animal record of that name; GBIF's own fixture expects the family.

**Overlapping groups** are declared on `TaxGroup` (`overlaps`, `getOverlaps`). They are disparate groups where the same
nomenclatural code governs both sides and kingdom placement moved:
- Protists ~ Animals: protozoa as animals
- Protists ~ Fungi: slime moulds
- Fungi ~ Plants: fungi and lichens as plants

Two groups overlap if one of them *is* a declared group and the other its declared partner or a group below that
partner. So Animals overlap with Algae and Insects with Protists, but Insects never with Algae, and Angiosperms never
with Ascomycetes: two specific groups contradict. `isDisparateTo` is unchanged.

**The switch is the existing `MatchingMode`.** DEFAULT was renamed to RELAXED, before it was ever deployed.

| Mode | Group veto | Higher rank fallback |
|---|---|---|
| STRICT | strict | no |
| RELAXED | relaxed | yes |
| FUZZY | relaxed, plus fuzzy matching (not yet available) | yes |

- GBIF's `strict=true` maps to STRICT and keeps the kingdom veto, as GBIF's own fixtures expect: *Abies alba* with
  `kingdom=Animalia` gives no match.
- `UsageMatcher.match(snc, mode, verbose)` and `matchClassification(classification, mode, verbose)` are the read-only
  entry points. They never insert names. They are used by `/match/gbif`, `/match/nameusage`, matching jobs and
  OpenRefine reconciliation.
- Merges and syncs never pass a mode. `TreeMergeHandler` uses `match(snc, allowInserts, verbose)`, and `HierarchySync`
  uses `parseAndMatch(snc, higherRank)`. Both always keep the strict veto, so no writing code path can relax groups.
- The duplicate search treats RELAXED like STRICT, as it did DEFAULT.

## What stays strict

- **`PlacementChecker` (#1608).** Softening it would let a protist species attach to an animal genus homonym again.
- **`NameIdentity`.** Softening it would hand a stable id to a different taxon, the id equivalent of a false merge.
  Agreeing authorship already overrules a placement conflict there.
- **`TaxGroupAnalyzer`** picks a group, so it needs the plain disparity.
- **The genus homonym decision** of qualified genus names and `spansDisparateGroups` (ambiguous homonyms of disparate
  groups): a protozoan genus is not the sponge genus of the same name.

## Rejected

- **Overlaps or the bi- and trinomial preference in merges.** Overlaps would merge a sponge genus from a source
  classified only by kingdom onto the only protist genus of that name in a project. The bi- and trinomial preference
  would merge an authorless wasp onto the only plant of that name.
- **Lenient groups**, i.e. flagging Protists as weak against everything. That would also soften protists vs bacteria.
- **Overlap inheritance on both sides.** Insects vs Algae is no overlap.
- **Overlaps derived from shared codes.** They would silently add Protists ~ Plants and change with the codes
  metadata.
- **The group independent single-taxon rule** suggested in #1650 (ignore the group if all candidates are one taxon).
  It would merge a new animal genus onto the only plant homonym in a project.
- **A separate boolean parameter next to `MatchingMode`.** The mode already separates the read-only entry points from
  the writing ones.

## Outcome

- **Known GBIF difference.** The two `Known.KINGDOM` cases of the ported matching-ws tests stay marked, but the reason
  turned around. *Puma concolor* with `kingdom=Plantae` now matches the cat in RELAXED mode, while GBIF's fixture
  expects the kingdom Plantae.
- **Existing behaviour, not changed here.** In both modes a single surviving candidate with a DIFFERENT authorship is
  still matched when the query has no rank: *Agathis montana Shestakov* with `kingdom=Plantae` gives the plant.
