# Catalogue of Life identifiers

Every name usage in a COL release carries a short identifier such as `7HCQP` or `BNMGX`. These are the
identifiers you cite, link to and store in your own database, so the whole point of them is that the same
name keeps the same identifier from one release to the next.

This document explains what COL treats as "the same name", what it treats as a different one, what it
treats as simply unknown — and what that means for an identifier you have already published.

## What an identifier looks like, and why

COL identifiers are numbers written in a 29-character alphabet: `23456789BCDFGHJKLMNPQRSTVWXYZ`. Two
things are missing from it on purpose.

- `0`, `O`, `1` and `I` are gone, because they are easily confused in print and in many screen fonts.
- **Every vowel** is gone, so that a randomly generated identifier can never spell a meaningful — or
  offensive — word in any language.

An identifier is at most 7 characters long. They are issued in ascending numeric order, so a **shorter**
identifier is always an older one; among identifiers of equal length, an alphabetically earlier one is
older. Do not compare them as plain strings across different lengths — `Z` is older than `32`, not
younger.

You will meet them in the `ID` column of the COLDP and Darwin Core Archive exports, and in portal URLs
of the form `catalogueoflife.org/data/taxon/{id}`.

### Only releases have stable identifiers

A COL *release* is an immutable snapshot and its identifiers are stable. The underlying *project* changes
continuously, may temporarily hold duplicate or incomplete data, and does **not** use stable identifiers
— never cite a project identifier. See [`API.md`](API.md) for how datasets, projects and releases relate.

### Two kinds of name keep their own identifier

Where a name already has a globally recognised identifier, COL adopts it rather than minting one of its
own:

| source | form in COL |
|---|---|
| UNITE species hypotheses | used verbatim, e.g. `SH19186714.17FU` |
| BOLD BINs | the colon becomes a dot, e.g. `BOLD:AAA3374` → `BOLD.AAA3374` |

## Why this is hard

It is tempting to assume a name is a stable string that can simply be looked up. In a taxonomic database
it is not.

- **Spelling varies.** `Felis rufus` and `Felis rufa` are the same name with a different gender ending.
  `baileyi` and `baileii` are the same epithet, differently transliterated.
- **Author citations vary enormously.** `Mill.`, `Miller`; `L.`, `Linné`, `Linnaeus`. The publication
  year is often cited a year out.
- **Authorship gets added.** A genus that was unqualified for years acquires its author when a better
  source is merged in.
- **Taxonomic status changes.** An accepted species is sunk into synonymy, or a synonym is resurrected.
  That is a change in classification, not a change of name.
- **The same name arrives twice.** Two sources contribute the same taxon, an editor notices months
  later, and one copy is removed.
- **Genuine homonyms exist.** *Oenanthe* is a genus of birds and a genus of plants. They must never
  share an identifier, however identical the string.

Every release has to decide, for every name, whether it is the same name that carried a given identifier
last time. Getting that wrong in either direction is costly: a needlessly new identifier breaks every
citation of the old one, and a wrongly reused identifier silently points your readers at a different
organism.

## The rules

COL compares each attribute of a name three ways — **these differ**, **these agree**, or **we cannot
tell from what we have** — and the last is not the same as the first.

> An attribute that *contradicts* always costs a name its identifier.
> An attribute that is merely *missing* never does.

That single distinction is what changed most in recent releases. Adding an authorship where there was
none, or a rank where a name was unranked, used to be enough to mint a new identifier. It no longer is:
the information got better, the name did not change.

| | a **different** name | the **same** name | tells us **nothing** |
|---|---|---|---|
| scientific name | a different canonical name | gender endings and common epithet spellings: `Felis rufus` ↔ `Felis rufa`, `baileyi` ↔ `baileii` | |
| authorship | a genuinely different author: `Mill.` → `DC.` | the same author cited differently: `Mill.` = `Miller`, `L.` = `Linné`; years within a small tolerance | authorship added or removed; a combination author lined up against a basionym author |
| rank | species against genus | | unranked against a concrete rank; the deliberately vague ranks such as "suprageneric name" |
| taxonomic status | a misapplied name against anything that is not one | accepted ↔ synonym; accepted ↔ provisionally accepted | |
| nomenclatural code | zoological against botanical — the *Oenanthe* case | | one side not stated |
| taxonomic group | an animal against a plant | | one side unplaced |
| name phrase (`sensu …`) | a different `sensu` on two misapplied names | | one side missing |
| accepted name | never on its own | corroborates — this is what keeps pro parte synonyms of one and the same name apart | |

Two rules follow from the table.

**Full agreement needs both authorship and rank.** A candidate where both positively agree is treated as
confirmed; one where only a single one of them does is merely plausible. Everything else that agrees —
status, code, taxonomic group, accepted name — strengthens a candidate but cannot by itself make it a
confirmed match.

**Bringing an identifier back takes more than keeping one.** An identifier the last release still had can
be kept on fairly thin evidence. One that was already dropped in an earlier release needs positive
agreement on authorship or rank before it is resurrected, so that an identifier removed as an error does
not quietly reappear on a name nobody has much information about.

## When several old identifiers would fit

Sometimes more than one previous identifier is a candidate for the same name — typically because the name
was present more than once in the past.

Evidence decides first, and nothing below can outweigh it. Only when the data says *exactly* as much
about two candidates does age decide, and then COL prefers **longevity over recency**:

1. an identifier a normal release has used, over one only ever issued in an extended release
2. the identifier that appeared in more releases
3. the identifier issued earliest

An identifier that served twenty releases and was dropped last month is cited in far more publications
than the one minted to replace it, so restoring the older one costs the community less than keeping the
newer. This is deliberate, and it is the answer to the long-standing complaint that an identifier used
for years could be replaced by one issued weeks ago
([#1289](https://github.com/CatalogueOfLife/backend/issues/1289)).

## Duplicates that get removed

This is the case the ranking above is really built for.

A name reaches a release twice — say once from the base release and once merged from another source.
Both copies get an identifier: the old, long-lived one and a newly minted one. Some releases later an
editor spots the duplication and removes one of the two usages.

**The older, more widely cited identifier survives.** The one minted a release or two ago is the one that
dies. COL records which identifier took over from it; that pairing is listed in the release reports (see
below) and stored with the release.

> Requesting a dead identifier does **not** yet redirect you to its survivor. The replacement is
> recorded, but resolving it over the API is not implemented, so an old identifier still returns nothing.

## What can still change your identifier

Being honest about the remaining cases matters more than a reassuring summary.

- **A genuinely changed author gives a new identifier — by design.** If `Mill.` becomes `DC.`, this is
  not the same name, and COL advertises that by issuing a new identifier rather than silently carrying
  the old one over. This has been policy since 2021 and is deliberate
  ([#1326](https://github.com/CatalogueOfLife/backend/issues/1326)).
- **Spelling variants in the genus or in a single-word name are not folded.** Epithets are: `rufus` and
  `rufa` are matched. Genus names and other uninomials are compared as written, so `Mammillaria` and
  `Mamillaria` are two different names to COL and receive two different identifiers. There is no
  recovery from this today.
- **Names that cannot be parsed or indexed never get a stable identifier.** They keep a temporary one for
  the life of the release. Placeholder names are the common case.
- **Expect a one-off burst of change** at the first release after the identifier rules were reworked,
  concentrated on names whose authorship or rank had been corrected years after their identifier was
  first issued. It settles from the next release on.

## Tracking what changed between releases

Every release publishes its identifier reports at

```
https://download.checklistbank.org/releases/{projectKey}/{attempt}
```

For COL the project key is `3`, and the attempt is the release's build number. Most of the reports are
bundled in `id-reports.gz`; two sit alongside it.

| file | what it lists | reach for it when |
|---|---|---|
| `created.tsv` | identifiers issued for the first time in this release | you want to know what is new |
| `deleted.tsv` | identifiers the previous release had and this one does not | a link of yours stopped working |
| `resurrected.tsv` | identifiers brought back after one or more releases without them | an identifier reappeared |
| `superseded.tsv` | deleted identifiers that another identifier took over, with the surviving name | a duplicate you cited was cleaned up |
| `unstable.txt` | names whose identifiers churned, grouped by scientific name | you are auditing instability for a group |
| `temporary.tsv` | usages that could not be given a stable identifier | a name has an unexpectedly long identifier |
| `nomatch.txt` | usages with no names index match at all | you are diagnosing why a name never stabilises |

`temporary.tsv` and `nomatch.txt` are written directly into the release directory, not into the archive.

## Identifiers for names, as opposed to usages

COLDP separates a **name** from the **usage** of that name in a taxonomy, and exports both: `ID` on a
usage, `nameID` pointing at the name it uses. A name record can also be given a stable identifier — the
identifier of one of its own usages, so that for the great majority of names the name and its usage share
one.

> This is available per project but is **off for COL today**, so the `nameID` column of COL exports is
> not yet stable across releases. Treat it as a within-release link only.

## Further reading

- [`API.md`](API.md) — datasets, projects, releases, and the compound keys that address a record
- [`XRELEASE.md`](XRELEASE.md) — how base releases and extended releases share one pool of identifiers
- [`2026-09-15-stable-id-evidence-model.md`](2026-09-15-stable-id-evidence-model.md) — the design record
  behind the current rules, for developers
