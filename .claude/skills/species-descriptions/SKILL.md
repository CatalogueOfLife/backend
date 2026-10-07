---
name: species-descriptions
description: Use when asked how many species (or names) were described per year in a ChecklistBank or Catalogue of Life dataset or release - description rates, discovery or description curves, new species per year or decade - for a dataset key or magic key like 3LXR, 3LR or COL26.9XR, overall or per taxGroup, for a year range, or including infraspecific names.
argument-hint: "<dataset key, e.g. 3LXR> [year range, e.g. 2000-2025] [infraspecific] [accepted only]"
---

# Original species descriptions per year and taxGroup

`species_descriptions.py` next to this file does the whole job: it fetches the dataset's extended ColDP export, counts
names in their **original combination** per year and taxGroup, and writes a TSV, an HTML report with one chart
per group, and one SVG per group. Run it; do not rebuild the counts from API searches. The counting rules
below are what you report back and defend, not something to reimplement.

## Run it

```bash
SKILL_DIR="$(git rev-parse --show-toplevel)/.claude/skills/species-descriptions"   # the directory of this file
python3 -I "$SKILL_DIR/species_descriptions.py" 3LXR --out "$OUT"
```

| request | arguments |
|---|---|
| latest published COL XR / base release | `3LXR` / `3LR` (any magic key the API resolves: `COL26.9XR`, `3R633`, a plain key) |
| a year range | `--years 2000-2025` (also `2000-`, `-1900`). It replaces the default window. |
| subspecies, varieties, forms too | `--infraspecific` |
| accepted names only | `--accepted-only` (default: accepted names and synonyms) |
| another environment | `--api https://api.dev.checklistbank.org` or `$CLB_API` |

Default years: everything found, dropping years before 1750 and after the current year as bad data.

- `$OUT` is the scratchpad or the directory the user names, a fresh one per run. Downloads are cached in
  `--work`, one zip per export job, and reused on the next run. Its default `~/.cache/clb-exports` is outside
  `$OUT`; pass `--work "$OUT/downloads"` when you may only write there.
- COL is large: the extended ColDP export is ~1.2 GB, and building a new one takes the server a while. Run the
  script in the background and wait for it, don't poll.
- `python3 -I` with nothing but the standard library. Do not install matplotlib or pandas for this.

**One export, with `clb:taxGroup`.** The script uses the extended ColDP export of the dataset's *current*
import attempt; its `NameUsage.tsv` carries the `clb:taxGroup` the server assigns to every usage (synonyms get
their accepted taxon's group). Archives built before backend commit `0b42ef87a` (2026-10-07) lack that column.
The script reads the header of an existing archive through HTTP range requests, so a stale one costs a few
requests instead of a download, and then forces a new export. It does the same when an export is recorded but
its archive is gone from the download host (dev and test databases are copies without the files).

**Credentials.** `$CLB_TOKEN`, or `$CLB_USER` + `$CLB_PASSWORD`. Tokens are per environment: a prod token is
not valid on dev or test. Forcing a new export needs an **admin** login; the server silently drops `force` for
anyone else, which the script detects. Any login lets it request an export where none exists yet, and private
releases need one anyway. Without credentials only an existing export
that already has the column can be used. When the script stops for a missing login, ask the user for one - do
not fall back to another release or an older attempt. Never print or write the token.

If it stops with "was built just now and predates the clb:taxGroup column", the server runs an older backend
(`GET /version`): tell the user it needs a deploy of `0b42ef87a` or later. Do not retry or force again.

## What is counted

| rule | detail |
|---|---|
| ranks | `species`; with `--infraspecific` every infraspecific rank of `/vocab/rank` except cultivar ranks and strain |
| status | accepted, provisionally accepted, synonym, ambiguous synonym. Never misapplied or bare names |
| original combination only | no basionym authorship, no authorship starting with `(`, no basionym relation (`basionymID`, `NameRelation.tsv`) to another name |
| new species only | replacement names (nomina nova) are left out, and so are names without any authorship: they cannot be told from a recombination |
| valid descriptions only | nomenclatural status `not established`, `manuscript`, `chresonym` are left out |
| each name once | same scientific name + authorship + rank counts once (pro parte synonyms, duplicates) |
| botanical year | the **publication**: name `publishedInYear`, then the reference's CSL `issued`, then the last year in the citation, authorship year last |
| zoological and other years | the authorship year first, then the publication years as above |
| code | the name's `code`; without one, botanical only when its taxGroup is purely botanical |
| taxGroup | the `clb:taxGroup` ChecklistBank assigns per usage from its classification |

The TSV is wide: `year`, `total`, one column per taxGroup in hierarchy order, and `unknown` for names without a
group. **Every group column includes its subgroups** (insects include coleoptera), so columns do not add up to
`total`. Algae and pseudofungi have two parent groups; they count under their primary one only (plants, fungi),
so a pseudofungus never shows up as a plant. The HTML and `svg/` have one chart per group, skipping a group whose
names all fall into one subgroup.

## Report back

Lead with the numbers from the script's summary, not your own reading of the files:

1. Total original descriptions, the year range, ranks and statuses used, and the split by rank the script prints
   with `--infraspecific`.
2. The largest groups and the peak years worth mentioning.
3. **What was left out and why** - always the number of recombinations, and `no year` when it is large. These
   counts cover every name of the requested ranks in any year, not just the requested range: say so. A
   recombined name whose original combination is missing from the dataset is not counted at all, so the curves
   are a lower bound. Many `no year` botanical names mean the source has no publication data.
4. The recent years drop because new names reach the databases late - not a real decline. Check the version and
   import date the script prints first: a snapshot imported years ago ends where it was taken.
5. Paths to the TSV, the HTML report and the `svg/` folder. Offer in one line to publish the HTML as an artifact.

## Common mistakes

| mistake | why it is wrong |
|---|---|
| counting from `/nameusage/search` facets | the index has no `publishedInYear`, cannot exclude recombinations, and `authorshipYear` is the combination year only |
| rebuilding classifications to run `TaxGroupAnalyzer` locally | the server does it: the extended ColDP export carries `clb:taxGroup` per usage |
| downloading a stale archive to check it for `clb:taxGroup` | the script peeks at the header with range requests; a cached zip is checked locally |
| reaching for DuckDB, pandas or matplotlib | not installed; the script needs the standard library only |
| dating botanical names by authorship | botanical authorships carry no year; `(L.) H.Karst.` is the 1881 combination, not the 1753 description |
| keeping bracketed names because the year in brackets is the original one | the user asked for original descriptions only; the original name is counted on its own if the dataset has it |
| reading the reference `year` from the API per name | millions of requests; the export already has `issued` and the citation |
| reading a downloaded TSV whole to "check" it | hundreds of MB; trust the script's counts, spot check with `grep` |
