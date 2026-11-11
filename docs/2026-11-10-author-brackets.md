# Authors with and without brackets, and why a name lost its identifier

Date: 2026-11-10
Status: merged (#1704) and deployed on prod 2026-11-10, misapplication notes (see Outcome) too; sensu authors on
branch `feat/sensu-authors`, not merged

## Problem

The dry run of the redirect backfill (`POST /admin/archive/redirects?projectKey=3&since=2328&dryRun=true`, job
`75e69804`) would have redirected 830,350 dead identifiers. About 23,700 of them paired a name cited with an author
without brackets with one cited with a different author in brackets:

- *Monohammus confusor* Saunders, 1881 → *Monohammus confusor* (Kirby, 1837)
- *Abraxas grossulariata* Matsumura, 1925 → *Abraxas grossulariata* (Linnaeus, 1758), although the dead usage was a
  synonym of another *Abraxas* species

These are mostly zoological catalogues citing a later user of a name as its author, sometimes for a misidentification.
`AuthorComparator` lined the unbracketed author up against the bracketed one, but could only answer EQUAL or UNKNOWN
there, never DIFFERENT. With rank agreeing, `NameIdentity` made such pairings PLAUSIBLE: enough for a redirect, a
resurrection, or for keeping an identifier.

## The cases

Counted on the redirect pairs of the dry run and on the identifiers COL26.9 and COL26.10 have in common:

| | case | example | before | now |
|---|---|---|---|---|
| 1 | one side without author | `Verbenaceae` / `Verbenaceae J.St.-Hil.` | unknown | unknown |
| 2 | both without brackets | `Marti, 1940` / `Marty, 1940` | compared | compared |
| 3 | both only bracketed | `(Thiele, 1909)` / `(Thiele, 1910)` | compared | compared |
| 4 | both with brackets and combination author | `(Kunth) Desv.` / `(Humb. & Bonpl.) Desv.` | combination author decides | unchanged |
| 5 | bracketed only against full | `(Olsson, 1869)` / `(Olsson, 1869) Stunkard, 1962` | basionym authors compared | unchanged |
| 6a | without brackets = other's combination author | `Suksd.` / `(G.S.Torr.) Suksd.` | equal | equal, `BASIONYM_OMITTED` |
| 6b | without brackets = other's bracketed author | `L.` / `(L.) Mill.` | different | **probably equal**, `BASIONYM_AUTHOR_CITED` |
| 6c | matching neither | `DC.` / `(L.) Mill.` | different | different |
| 7a | without brackets against only bracketed, same author | `Pascoe, 1864` / `(Pascoe, 1859)` | equal | equal, `BRACKETS` |
| 7b | … same author, years more than 11 apart | `Kennel, 1879` / `(Kennel, 1899)` | **unknown** | **probably different** |
| 7c | … different author | `Harrington, 1899` / `(Kirby, 1837)` | **unknown** | **probably different** |
| 8 | notes inside the authorship | `(Hagen, 1856) non Brullé, 1832` / `Germar, 1830`, `auct. non (DC.) Bunge` / `Jaub. & Spach` | the parser moves the note out, the rest is one of the cases above | same, so the first is now 7c |

`AuthorComparatorTest.bracketsAndRoles` tests every example of this table, numbered by case.

## Decisions

- **One rule for every code.** An author without brackets is the original author. Against the other side's bracketed
  author that is the same name with brackets added or lost (7a), probably the same name next to a combination author
  (6b), or a different original author (7b, 7c). It can
  only be read as the combination author with the basionym author left out where the other side shows that
  combination author (6a). The code is often wrong in the data and a code specific rule is harder to predict. The only
  case suffering from this is botanical data with a bracketed author and no combination author, like
  `(Moc. & Sessé)`, which the dry run shows to be rare.
- **Two probable shades of `Equality`, not a comparison for identifiers only.** The authorship comparison returns
  `PROBABLY_DIFFERENT` for 7b and 7c and `PROBABLY_EQUAL` for 6b, nothing else does. A test for `== EQUAL` or
  `== DIFFERENT` only acts on a certain answer, `isEqual()` and `isDifferent()` also on a probable one, so every
  caller states how much doubt it can afford. Nothing that was `EQUAL` before is downgraded.
- **6b is probably equal.** Making it equal for every caller broke `UsageMatcherBasionymAuthorTest.strictAndMerges`,
  which pins #1660: merges keep *Aus bus* Mill. and *Aus bus* (Mill.) Smith apart. The data proves #1660 right:
  COL26.10 holds 1,583 such pairs side by side (5,298 in its XR), mostly botanical, an original name and a later
  transfer of a basionym of the same author from another genus, like *Aechmea blanchetii* Baker and (Baker) Mez.
  Read as equal by `NameIdentity` they swapped identifiers: every usage paired with both ids as CONFIRMED and
  seniority, not the exact authorship, decided. Read as `PROBABLY_EQUAL` the pairing is no contradiction, so an id
  survives a source adding the combination author, but PLAUSIBLE at most, so the exact pairing always wins.
- **The consumers**, decided one by one:

  | consumer | test | effect |
  |---|---|---|
  | `NameIdentity` (ids, redirects) | contradiction on `isDifferent()`; CONFIRMED and the placement override only on `EQUAL`; PLAUSIBLE on `isEqual()` | 7b/7c cost the id, 6b keeps it without confirming |
  | `UsageMatcher.notEqualAuthorship`, qualified names and merges | `!= EQUAL` | 6b and 7c stay apart, as before |
  | `TreeMergeHandler`, bare names merged into a candidate | `== EQUAL` | unchanged |
  | `UsageMatcher`, names without a rank | removes `isDifferent()` | 6b now matches directly (#1660), 7c is removed |
  | `UsageMatcher.mayBeHomonyms` | anything but `EQUAL` or `UNKNOWN` may be a homonym | 6b as before, 7c now ambiguous instead of guessed |
  | `HierarchySync.sameName` | same only on `EQUAL` or `UNKNOWN` | 6b as before, 7c no longer joined |
  | `MatchingDao` decision rematch | rules out `isDifferent()`; of several matches a single `EQUAL` one wins | a decision survives `L.` becoming `(L.) Mill.`, 7c rules it out, and with both names in the source each decision keeps to its own |
  | genus checks, `PlacementChecker`, diff printer | `== DIFFERENT`, single authorships | unchanged |

  The relaxed fallback of #1660 stays for qualified names and for a shared basionym author with another combination
  author, `(L.) Pers.` against `(L.) Vill.`.
- **A reason, never a decision.** `AuthorComparator.compareWithReason` returns an `AuthorshipComparison` with one of
  `MISSING, SAME_AUTHOR, SAME_YEAR, PUBLISHED_IN, ANONYMOUS, BASIONYM_OMITTED, BRACKETS, BASIONYM_AUTHOR_CITED,
  DIFFERENT_AUTHOR, DIFFERENT_YEAR`. When the years differ and the authors too, the author is the reason. `NameIdentity.Verdict` carries
  it and, for a contradiction, the `Conflict` (misapplied, phrase, rank, authorship, code, group). Verdicts still
  compare and equal on evidence and corroboration only.
- **The reasons are shown** in the verdict column of the redirect report, where a contradicted dead id now names the
  live id that came closest, and in `unstable.txt` of every release as `why:` for a dropped identifier:
  `contradicted <explanation>` or `outranked`.
- **`PROBABLY_EQUAL` for 6b only.** Citation variants of one author (`Mill.` = `Miller`, `(Bryk, 1949)` = `Bryk, 1948`)
  stay `EQUAL`: downgrading them would change every merge testing `== EQUAL`, and for identifiers bring back the exact
  citation tie-break reverted on 2026-09-15 because it let a removed duplicate keep its junior id.

## Side effects measured

Identifiers kept across an authorship change that is now 7b or 7c, i.e. identifiers the new rule would have dropped:

- COL26.9 → COL26.10: 5.42M identifiers in both, 10,773 authorship changes, 43 across brackets with different authors.
  About 12 are spelling variants the comparator still reads as the same author (Mosely/Moseley, Förster/(Foerster)),
  about 30 genuine author changes, e.g. *Lindenius albilabris albilabris* Tsuneki, 1967 → (Fabricius, 1793).
- COL26.9 XR → COL26.10 XR: 8.46M identifiers in both, 17,033 authorship changes, 85 across brackets with different
  authors, 2 with years too far apart.

A few dozen identifiers per release, the same as an unbracketed author changing already costs.

## Outcome

The review of the change found that the decision rematch matched both names of an isonym pair once `L.` and
`(L.) Mill.` were no longer different: a sector target or decision on either one became ambiguous and was lost, an XR
override decision skipped. `MatchingDao.matchDataset` now keeps the single match with an equal authorship, if there is
one. A year alone against an author with a close year, `1837` against `(Kirby, 1840)`, is explained as
`DIFFERENT_YEAR`, not `DIFFERENT_AUTHOR`.

Two effects are intended but were not measured before the merge: redirects between the two names of an isonym pair
when only one of them is live (6b is no contradiction any more; the verdict column says `basionym author cited`), and
more names skipped as `AMBIGUOUS_HOMONYM` in merges, as 7c candidates now count as possible homonyms. The next dry run
and the next XR show their size.

To do after the deploy: a new dry run of the redirect backfill, then the real run. Old identifiers of BOLD BINs and
UNITE species hypotheses (1.52M "no target" rows in the dry run) still get no redirect: they are not matched to the
names index the pairing groups by.

The dry run after the deploy (job `60a89831`, the same dead ids as `75e69804`) gave 809,715 redirects instead of
830,350. 24,620 former redirects are contradicted now: 23,181 across brackets, 633 the same author with years too
far apart, and among them about 750 carrying a note like `Tindale, 1932 non Donovan, 1805`. About a dozen are spellings
of one author the comparator does not recognise (`M'Coy`/`McCoy`, `Cox`/`(J. C. Cox)`), which without brackets
were different names already. 2,564 ids gained a redirect as 6b, 1,627 more as the bracket mismatch was the only
competitor, 206 became ambiguous, in 14 of them an exact author tied with a 6b author. The existing redirects of
the first, real backfill (`since` COL26.6 XR) and of the releases since COL26.9 XR were all decided by the old rule
and are never overwritten, so they are cleared before the backfill runs again.

The case 8 examples showed 3,205 redirects leaving a name whose note marks a misapplication, like *Cicer songaricum*
auct. non (DC.) Bunge, an ambiguous synonym of COL21, redirected to *Cicer songaricum* Jaub. & Spach. Imports derive
the misapplied status from such notes (`MisappliedNameMatcher`), but archived usages of older releases kept the
synonym status. `NameIdentity` now applies the same rule to a synonym's name phrase and to a note left in its
authorship.

The real backfill after the deploy (job `3323ba1a`, 2026-11-10) wrote 1,054,345 redirects and left 148 dead ids
contradicted on the phrase, the very same misapplication in most of them: `sensu Hutton, 1873` against
`sensu F. W. Hutton, 1873`, or the note in the phrase on one side and in the authorship on the other.
`MisapplicationNote` (2026-11-11) reads a note as its sensu author, `auct.` and the excluded names, and `NameIdentity`
compares these with the `AuthorComparator` for two misapplied names. Replayed on prod data, 109 of the 148 pair now. Of
the 39 left, 12 fail on `s.` and `s.auct.`, which the parser reads as author names, and the rest name different senses.
The matcher was left alone: `UsageMatcher` does not look at phrases or notes at all, and what that costs merges is to be
measured on an XR before it changes.
