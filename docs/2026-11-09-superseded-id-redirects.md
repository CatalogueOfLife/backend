# Serving and backfilling superseded id redirects

Date: 2026-11-09
Status: implemented on branch `feat/superseded-redirects`, not yet deployed; the prod backfill for COL is still to run

## Why

[data#1867](https://github.com/CatalogueOfLife/data/issues/1867): a GBIF user in New Zealand found 279 plant taxa
whose COL identifier differs between COL26.6 XR (315557) and COL26.10 XR (316691). The prod API and the published
`id-reports.gz` of every release in between show when each one changed:

| release | attempt | ids changed |
|---|---|---|
| COL26.7 | 606 | 145 |
| COL26.7 XR | 607 | 27 |
| COL26.8 | 620 | 102 |
| COL26.8 XR | 622 | 1 |
| COL26.9 | 629 | 3 |
| COL26.10 XR | 643 | 1 |

278 of them changed in releases built before the stable id fixes of
[2026-09-15-stable-id-evidence-model.md](2026-09-15-stable-id-evidence-model.md). Only 2 of the new ids were newly
minted; all the others were archived ids brought back. 191 of them had been used in COL26.6 XR for a duplicate that an
extended release merge sector added (GRIN, Dyntaxa, Nortaxa and others), mostly as "provisionally accepted" with
differently spaced authorship: *Helosciadium* W.D.J.Koch `637G6`, in use since COL21, became `VJTPP`. The one later
case, *Closterium acutum* var. *acutum* `MWYX5`, has an archived authorship glued by the old parser (`Bréb.in Ralfs`)
that the current authorship `Bréb.` contradicts.

The current id provider keeps the ids it now has. Reverting to the old ids was rejected: the new ones have been
published for 3 to 4 monthly releases, are used downstream (by the reporter and by GBIF's COL based taxonomy), and
reverting them would break every one of them a second time.

What users of the old ids lacked is a way to find the new one. `name_usage_archive.superseded_by` existed, but:

- the API never served it: a dead id answered 404 with its archived usage and nothing else;
- the id provider only records a redirect for an id the previous release still had, and only since the COL26.9 XR,
  so none of the 26.7 to 26.9 churn has one.

## What changed

**Serving.** `ArchivedNameUsage.supersededBy` is selected with the archived usage, so the 404 body of
`/dataset/{release}/taxon/{id}` and `/taxon/{id}/info` carries it, and the message ends with `superseded by <id>`.
`TaxonDao.enrich404` follows the chain up to 5 hops and reports the last id. No redirect is followed with an HTTP
301: the target belongs to the newest releases, not necessarily to the release asked for, and a 404 with the
archived record is what clients already handle. The portal links the replacement on its tombstone page.

**Backfilling.** `ArchiveRedirectJob`, `POST /admin/archive/redirects?projectKey=&since=&dryRun=`:

- **Live ids** are the ones `ReleaseRanking.liveKeys()` carries: the supplying releases of the newest generation,
  plus the highest ranked supplying base and extended release. Without the latter, running between a base release
  and its extended release would treat the last extended release's ids as dead. The next extended release's id
  provider still sees them as current and records its own redirects for them.
- **Dead ids** are all other archived ids carried by a not ignored release with an attempt at or after `since`,
  without a redirect yet.
- **Pairing.** `RedirectPairing` compares each dead id with the live ids of its canonical names index group using
  `NameIdentity`, on the archived versions of both, with the facts built as the id provider builds them.
  - A contradiction rules a pairing out.
  - A redirect needs PLAUSIBLE evidence or better, the gate a resurrection must pass.
  - The best verdict wins.
  - A tie goes to the id the newest base release carries; anything still tied is ambiguous and gets no redirect.
  - Many dead ids may point at one live id.
  - Ids that are no LATIN29 integers (temporary and OTU ids) are left out on both sides.
- **Writing.** `fillSuperseded` only writes where `superseded_by` is null and never on an id a live release
  carries. A second run changes nothing.
- **Concurrency.** The job holds the project lock. Publishing does not take it, so before each write batch the job
  re-reads the live releases and fails if they changed.
- **Report.** The result file is a zip holding `redirects.tsv` with every decision:

  | outcome | meaning |
  |---|---|
  | REDIRECT | the dead id now points at the live id |
  | AMBIGUOUS | two live ids are equally good candidates, no redirect |
  | WEAK | nothing contradicts the best live id, but nothing corroborates it either, no redirect |
  | CONTRADICTED | every live id is a different name, no redirect |
  | NO_TARGET | the group has no live id |

  A dry run writes only the report.

Publishing leaves the backfill alone: `clearSuperseded` only touches ids a newest generation release carries again,
and `applySuperseded` only ids that were current, which `liveKeys()` excludes from the backfill.

## Not in this change

- No identifier is reverted, and the id provider is unchanged.
- No HTTP redirect, at the API or at the portal.
- Redirect chains are not compressed at publish time; they are followed when read.
- The glued `in` authorships stored by the old parser (`Bréb.in Ralfs`) are not repaired; such ids get no redirect.

## Rollout

1. Deploy the backend, then the portal.
2. Dry run on prod: `POST /admin/archive/redirects?projectKey=3&since=315557&dryRun=true`. Review `redirects.tsv`
   and check the 279 data#1867 pairs.
3. Run it without `dryRun`, holding COL releases and publishing meanwhile.
4. Reply on data#1867.

## Outcome

The real backfill on prod (job 3323ba1a, `since=2328`) left 1,518,977 dead ids without a target, every one of them an
OTU: 834,421 BOLD BINs and 684,556 UNITE SHs (all `.10FU`) with the LATIN29 ids they had up to COL26.5 XR, before
`IdProvider.otuId` made the code the id. Their groups hold the live OTU, but the pairing left out every non LATIN29
id, and `NameIdentity` has no evidence for two unranked names without authorship anyway. Since 2026-11-11 a dead id
whose name is an OTU code redirects to the live id of exactly that code, reported as outcome `OTU`, and never by
name; a live OTU id is no target of a name based redirect.
