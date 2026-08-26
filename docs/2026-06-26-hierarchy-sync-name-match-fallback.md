# HierarchySync — name-match fallback placement

**Date:** 2026-06-26
**Issue:** [CatalogueOfLife/checklistbank#1699](https://github.com/CatalogueOfLife/checklistbank/issues/1699)
**Component:** `core/src/main/java/life/catalogue/assembly/HierarchySync.java`

## Problem

`HierarchySync` delegates a project's higher classification to a source taxonomy. Today it
only acts on project usages that already carry a **source-dataset identifier** (resolved via
`IdentifierScopeResolver`). Project usages with no such identifier are skipped entirely, so they
stay floating at the project root instead of being nested under their classification.

Issue #1699 asks that unmatched names at least be placed under the closest higher taxon the
source can offer:

- A floating binomial such as *Alchemilla acutiloba* should be nested under its genus
  *Alchemilla* even when the full species cannot be matched (issue 1.1 / 1.3).
- Names that *do* exist in the source but were not id-matched on import (e.g. *Agrostemma
  githago*, *Alisma lanceolatum*, *Capsicum annuum*) should be nested under their genus (issue 2).

This corresponds to **option 2** of the maintainer's issue comment: do a higher-rank lookup
*during* the hierarchy sync rather than persisting a higher-match id on import. The
infrastructure (`nameIndex`, the matcher supplier) is already injected and reserved for this via
the existing `TODO(hierarchy-sync)` markers.

## Goal & non-goals

**Goal:** place project usages that lack a source identifier by re-matching them against the
hierarchy source dataset and using full *or* `HIGHERRANK` matches purely for **placement**
(parent rewiring + ancestor import).

**Non-goals (explicit constraints):** for name-matched usages we do **not**
- change taxonomic status,
- add or copy synonyms,
- update authorship, or
- add identifiers to the project usage (no convergence to id-matching; full matches stay
  name-matched on every run — a possible, less-conservative follow-up).

**Also in scope (two related improvements requested during design):**
- **Import down to genus for the identifier path too** — unify both paths so an id-matched
  species nests under its genus, not its family.
- **Project-side dedup of imported ancestors (all ranks)** — when the project already provides
  an equivalent genus/family/order, reuse it instead of inserting a duplicate copy. This is the
  documented `TODO(hierarchy-sync)` "project-side dedup".

## Design

### 1. New phase-1 sub-pass: `discoverNameMatches`

Runs inside `syncHigherClassification()` immediately **after** `discoverIdentifierMatches`. It
streams project usages (or reuses the same stream) and selects candidates that are **all** of:

- not already in `projectMatches` (no identifier match), and
- not tagged with this sector (defensive; already wiped by `deleteOld`), and
- an **accepted taxon** — synonyms are left untouched, because retargeting a synonym's
  `parent_id` to a different accepted taxon is a status change, which is out of scope.

Selected candidates feed a new placement structure, kept **separate** from `projectMatches` so
they never reach phases 2–4:

```java
/** project usage id -> source "anchor" id to nest the usage directly under (name-match fallback). */
private final Map<String, String> namePlacements = new LinkedHashMap<>();
```

### 2. Two matchers: source-targeted and project-targeted

`UsageMatcher` matches against a single dataset fixed at construction. We need **both**:

- a **source matcher** (target = `sourceDatasetKey`) to find anchors for floating project usages;
- a **project matcher** (target = the project) to dedup ancestors before importing them.

The injected `matcherSupplier` already targets the project — keep it and use it for dedup. For
the source we need a matcher whose target is `sourceDatasetKey`, only known after
`init()`/`resolveSourceDatasetKey()`. Change `SyncFactory.hierarchy(...)` to **also** inject a
source-capable provider:

```java
// SyncFactory — keep the existing project-bound supplyPgMatcher(projectKey) as matcherSupplier,
// and add a dataset-keyed provider for the source:
BiFunction<Integer, SqlSession, UsageMatcher> sourceMatcherFn = (dk, sess) -> matcherFactory.postgres(dk, sess);
```

`HierarchySync` builds `sourceMatcher = sourceMatcherFn.apply(sourceDatasetKey, session)` in the
name-match pass and `projectMatcher = matcherSupplier.apply(session)` for dedup. The Postgres
matcher queries the DB directly and needs **no** separate load/build step; both rely on the
injected global `nameIndex` for canonical nidx ids. The `@SuppressWarnings("unused")` markers on
`matcherSupplier` / `nameIndex` are dropped (both are now used).

### 3. Matching & interpretation

For each candidate, build a `SimpleName` from the project usage's `Name` (canonical name, rank,
authorship) and call:

```java
UsageMatch m = matcher.parseAndMatch(sn, /*higherRank*/ true);
```

We lack reliable in-project classification context for these floating names, so we rely on the
name's implied genus for higher matching (v1 keeps it simple; classification context can be added
later if useful).

Interpret `m.type`:

| MatchType | Action | Placement anchor (source id) |
|---|---|---|
| `EXACT`, `VARIANT`, `CANONICAL` | place (full match → same taxon) | matched usage's **immediate parent** (`m.usage.getParent()`) |
| `HIGHERRANK` | place | the **matched usage itself** (`m.usage.getId()`) |
| `AMBIGUOUS`, `NONE`, `UNSUPPORTED` | leave untouched | — |

Record `namePlacements.put(projectUsageId, anchorSourceId)` for the two placed cases. If a full
match's immediate parent is `null` (matched a root), skip — there is nothing to nest under.

### 4. Ancestor import & rewiring (import down to genus, with dedup)

The placement anchor must exist in the project before we can rewire under it. Reuse the existing
collect → insert → rewire machinery (`collectAncestors`, `insertAncestorsTopDown`,
`rewireProjectParents`), with three changes that apply to **both** the identifier and name-match
paths:

- **Import threshold relaxed to include GENUS (both paths).** Today `collectAncestors` collects
  only ranks *strictly higher than* `Rank.GENUS`. Change the predicate to
  `rank.higherOrEqualsTo(GENUS)` so the genus is collected for import too. Id-matched species
  then rewire to their genus (the closest project ancestor) instead of their family, and name-match
  anchors that are genera get a node to nest under. Ranks below genus (subgenus, section) remain
  out of the imported set; a species rewires to genus.
- **Project-side dedup before insert (all ranks).** In `insertAncestorsTopDown`, before copying a
  ready ancestor, resolve it against the project in two steps:
  1. **By identifier** — if the ancestor's source id is already a matched project usage
     (`matchReverse`), reuse its accepted project equivalent (`resolveToAccepted`). This covers the
     existing id-path genera (e.g. a project genus that carries the source genus's identifier).
  2. **By name** — otherwise run the ancestor's `SimpleName` through the **project matcher**
     (`parseAndMatch`, `higherRank=false`). Reuse only on a **single, non-ambiguous, accepted**
     match of the **same rank**.

  On reuse: `sourceToProject.put(sourceAncestorId, existingProjectId)`, skip the
  `CopyUtil.copyUsage`, skip `addIdentifier`, and **do not** tag the existing record with this
  sector (so `deleteBySector` leaves user data intact on re-runs). Homonym safety falls out of the
  matcher: when the project holds two genera sharing a canonical id the matcher returns `AMBIGUOUS`,
  so we do **not** dedup and instead import a fresh copy in the correct lineage. Lineage-aware
  disambiguation (feeding the already-resolved project parent as classification context) is a noted
  follow-up, not required for v1. A synonym, ambiguous, or different-rank result also falls through
  to a fresh import.
- **Imported (non-deduped) nodes** are tagged with this sector's key + `Sector.Mode.HIERARCHY` and
  get a fresh project-side `VerbatimSource` linking back to the source, exactly as today. The
  existing `addIdentifier` on freshly imported ancestors stays — those are newly created
  sector-owned records, so identifying them back to the source is correct and is distinct from the
  "no identifier on the placed leaf usage" constraint.
- **Rewire.** After import/dedup, set the project usage's `parent_id` to the project equivalent of
  its anchor (`sourceToProject.get(anchorSourceId)`, falling back through
  `resolveProjectIdForSource` for an anchor that is itself an existing matched project usage). Skip
  the update when it equals the current parent, and guard against self-loops/cycles with the
  existing `wouldCreateCycle` check. For **name-match** placements the project leaf usage itself is
  **not** tagged with the sector and gets **no** identifier.

### 5. Flagging

Every project usage placed by this fallback gets `Issue.MATCHING_HIGHERRANK` (an existing
INFO-level constant already used by match-on-import in `PgImport`) added to its **verbatim
source** via `VerbatimSourceMapper.addIssue(key, issue)`.

The placed usage is pre-existing project data, so — mirroring phase 4's `nameVerbatimSourceKey`
helper — we attach the issue to its existing verbatim source, or mint a **sector-less**
`VerbatimSource` if it has none, so `deleteBySector` never wipes user data on re-runs.

### 6. Order of operations in `syncHigherClassification`

1. `discoverIdentifierMatches` (unchanged).
2. `discoverNameMatches` (new) → fills `namePlacements`.
3. `collectAncestors` extended: include GENUS (`higherThanOrEqual`) for the identifier path, and
   also walk anchors in `namePlacements` (down to genus), merging into `ancestorsToInsert`; record
   each name placement's anchor as its single-element rewire target.
4. `insertAncestorsTopDown` now dedups each ancestor against the project matcher before inserting
   (all ranks); deduped ancestors map straight into `sourceToProject` without a copy.
5. `rewireProjectParents` for identifier-matched accepted usages (now resolving to genus) **plus**
   a rewire of each `namePlacements` usage to the project equivalent of its anchor.
6. Flag each placed name-match usage with `Issue.MATCHING_HIGHERRANK`.

Phases 2–4 iterate `projectMatches` only and are therefore untouched by name placements.

## Affected files

- `core/.../assembly/HierarchySync.java` — new sub-pass, placement map, source + project matcher
  wiring, genus-level import threshold, project-side ancestor dedup, flagging; drop the
  fallback + project-dedup `TODO(hierarchy-sync)` markers and the `@SuppressWarnings`.
- `core/.../assembly/SyncFactory.java` — inject the source-capable matcher provider into
  `hierarchy(...)` (keep the existing project-bound `matcherSupplier`).
- Class javadoc updated to document the name-match sub-pass and remove the "not yet implemented"
  note. `HIERARCHY-SYNC.md` is updated **only if present** in this checkout (currently absent on
  master).

## Testing

Integration test (`*IT`, TestContainers) with a small source dataset (genus *Alchemilla* under a
family/order, an accepted species *Agrostemma githago* under genus *Agrostemma*, and a genus with
a homonym to force ambiguity) and a project holding floating, un-identified usages:

1. **HIGHERRANK → genus import + nest:** floating *Alchemilla acutiloba* (no source id, species
   absent from source) → genus *Alchemilla* imported, species rewired under it, verbatim source
   carries `MATCHING_HIGHERRANK`.
2. **Full match → nest under genus:** *Agrostemma githago* exists in source but un-id-matched →
   placed under project's *Agrostemma* (imported), no identifier added to the species, no status
   change.
3. **Ambiguous → untouched:** a name whose canonical hits multiple source genera → left at root,
   no flag, no import.
4. **Synonym candidate → untouched:** a floating synonym is not re-parented.
5. **Idempotency:** a second run produces the same tree and does not duplicate imported genera.
6. **Identifier-path genus nesting:** an id-matched species whose genus is not itself id-matched
   nests under the imported genus (not the family).
7. **Dedup, existing project genus:** when the project already provides the genus (accepted),
   no duplicate genus is imported and the species nests under the existing project genus, which is
   not tagged with the sector (survives `deleteBySector`).
8. **Dedup, homonym lineage:** a project genus that is a homonym under an unrelated family is
   **not** reused; a fresh genus is imported in the correct lineage.

## Open follow-ups (not in this change)

- Convergence: optionally add the source identifier for full `EXACT` matches so they integrate via
  the identifier path on the next run.
- Classification-context-aware matching of the floating leaf usages themselves (the source-side
  match currently relies on the name's implied genus, not a reconstructed project classification).

## Outcome

Salvaged from the implementation plan's self-review before it was retired.

- **Assumption that needed watching during execution:** whether `SimpleNameCached.getStatus()` /
  `getRank()` and `UsageMatch.usage.getClassification()` are populated by `UsageMatcherPgStore`
  (it builds `SimpleNameClassified` with a parent-chain classification). If `getStatus()` comes back
  null on store-returned candidates, the dedup `isTaxon()` guard has to relax to
  `status == null || status.isTaxon()` — a store candidate is an accepted-tree node by construction.
