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

COL publishes two kinds of release: the **base release**, and the **extended release** built on top of it
with names merged in from further sources. Both draw on one pool of identifiers. Wherever this document
speaks of *the previous release*, it means the previous release of the same kind: the previous base release
for a base release, the previous extended release for an extended one.

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
| scientific name | a different name once epithet spellings are folded | gender endings and common epithet spellings: `Felis rufus` ↔ `Felis rufa`, `baileyi` ↔ `baileii` | |
| authorship | a different author: `Mill.` → `DC.`; the same author with publication years more than 11 apart | the same author cited differently: `Mill.` = `Miller`, `L.` = `Linné`, publication years up to 11 apart; the same combination author, whatever the basionym author in brackets: `(Lamb.) G.Don` = `(Roxb. ex D.Don) G.Don`; one side's basionym author matching the other side's combination author: `(Bryk, 1949)` = `Bryk, 1948` | authorship added or removed; an authorship that cannot be parsed; one side's basionym author not matching the other side's combination author |
| rank | two different concrete ranks: species against genus; the catch-all rank "other" against any other rank | the same rank | unranked against any rank but "other"; a vague rank against a rank it covers: "suprageneric name" against family |
| taxonomic status | a misapplied name against anything that is not one | the same kind of status: accepted ↔ provisionally accepted, synonym ↔ ambiguous synonym | a taxon sunk into synonymy, or a synonym raised to accepted |
| nomenclatural code | two different codes: zoological against botanical — the *Oenanthe* case | the same code | one side not stated |
| taxonomic group | groups on separate branches: an animal against a plant | the same group | one side unplaced; one group within the other: insects against arthropods |
| name phrase (`sensu …`) | a different phrase on two misapplied names | the same phrase | one side missing; a different phrase on names that are not misapplied |
| accepted name | never on its own | the same accepted name for two synonyms — this is what keeps pro parte synonyms of one and the same name apart | |

Two rules follow from the table.

**Full agreement needs both authorship and rank.** A candidate where both positively agree is treated as
confirmed; one where only a single one of them does is merely plausible. Everything else that agrees —
status, code, taxonomic group, name phrase, accepted name — strengthens a candidate but cannot by itself
make it a confirmed match. How an authorship is written beyond that does not count: an exact copy of an
earlier citation is no stronger evidence than a variant COL reads as the same author.

**Bringing an identifier back takes more than keeping one.** An identifier the previous release still had
can be kept on fairly thin evidence. One that was already dropped needs positive agreement on authorship or
rank before it is resurrected, so that an identifier removed as an error does not quietly reappear on a
name nobody has much information about.

## When several old identifiers would fit

Sometimes more than one previous identifier is a candidate for the same name — typically because the name
was present more than once in the past.

An identifier the previous release still carried comes first, and nothing below can take it away. Only
candidates that nothing in the table above contradicts get this far, so between them the one already in
circulation is the one your links and citations point at. Below that the evidence decides, and only when the
data says *exactly* as much about two candidates does age decide, and then COL prefers
**longevity over recency**:

1. an identifier the previous release still had, over one that has to be brought back
2. the identifier the data says more about — see the table above
3. an identifier a base release has used, over one only ever issued in an extended release
4. the identifier that appeared in more releases, base and extended releases counted alike
5. the identifier whose first release was the earliest
6. the lowest identifier

Among identifiers that all have to be brought back, one that served twenty releases and was dropped last
month is cited in far more publications than the one minted to replace it, so restoring the older one costs
the community less than keeping the newer. This is deliberate, and it is the answer to the long-standing
complaint that an identifier used for years could be replaced by one issued weeks ago
([#1289](https://github.com/CatalogueOfLife/backend/issues/1289)).

## Duplicates that get removed

This is the case the ranking above is really built for.

A name reaches a release twice — say two sources contribute the same taxon. Both copies get an identifier:
the old, long-lived one and a newly minted one. Some releases later an editor spots the duplication and
removes one of the two usages.

**The older, more widely cited identifier survives**, whichever of the two copies was removed, unless the
data speaks against it. The copies being cited differently does not, as long as COL reads both citations as
the same author. The one minted a release or two ago is the one that dies.

Where it can tell, COL also records which identifier took over: when the dead identifier was in the previous
release, and a remaining usage of the same name that it could have belonged to kept an existing identifier.
That pairing is listed in `superseded.tsv` (see below). The project's identifier archive keeps it once the release
is published, but only if it is the newest published base release or the newest published extended release built on
it, and no other of those releases - the base release and the extended releases built on it - still carries the dead
identifier. An extended release does not reconsider the identifiers it inherits from its base release, so removing
the extended copy of a name that is also in the base release records no pairing.

> Requesting a dead identifier does **not** redirect you to its survivor. The replacement is recorded, but
> the API does not resolve it, so an old identifier still returns nothing.

## What can still change your identifier

Being honest about the remaining cases matters more than a reassuring summary.

- **A genuinely changed author gives a new identifier — by design.** If `Mill.` becomes `DC.`, this is
  not the same name, and COL advertises that by issuing a new identifier rather than silently carrying
  the old one over. A publication year corrected by more than 11 years counts the same way. This has been
  policy since 2021 and is deliberate ([#1326](https://github.com/CatalogueOfLife/backend/issues/1326)).
- **Spelling variants in the genus or in a single-word name are not folded.** Epithets are: `rufus` and
  `rufa` are matched. Genus names and other uninomials are compared as written, apart from case, accents
  and punctuation, so `Mammillaria` and `Mamillaria` are two different names to COL and receive two
  different identifiers. There is no recovery from this.
- **Names left out of the names index never get a stable identifier.** They keep a temporary one for the
  life of the release. Placeholder names are the common case; names that merely cannot be parsed are
  indexed and do get stable identifiers.

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
| `resurrected.tsv` | identifiers this release brought back that the previous release did not have | an identifier reappeared |
| `superseded.tsv` | deleted identifiers that another identifier took over, with the surviving name | a duplicate you cited was cleaned up |
| `unstable.txt` | names whose identifiers churned, grouped by scientific name | you are auditing instability for a group |
| `temporary.tsv` | usages that could not be given a stable identifier | a name has an unexpectedly long identifier |
| `nomatch.txt` | usages with no names index match at all | you are diagnosing why a name never stabilises |

`temporary.tsv` and `nomatch.txt` are written directly into the release directory, not into `id-reports.gz`.

## Identifiers for names, as opposed to usages

COLDP separates a **name** from the **usage** of that name in a taxonomy, and exports both: `ID` on a
usage, `nameID` pointing at the name it uses. A name record can also be given a stable identifier — the
identifier of one of its own usages, so that for the great majority of names the name and its usage share
one.

> This is a per project setting, off by default and off for COL, so the `nameID` column of COL exports is
> not stable across releases. Treat it as a within-release link only.

## Further reading

- [`API.md`](API.md) — datasets, projects, releases, and the compound keys that address a record
- [`XRELEASE.md`](XRELEASE.md) — how base releases and extended releases share one pool of identifiers
- [`2026-09-15-stable-id-evidence-model.md`](2026-09-15-stable-id-evidence-model.md) — the design record
  behind the current rules, for developers
