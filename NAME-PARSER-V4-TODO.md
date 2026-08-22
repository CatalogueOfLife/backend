# name-parser v4 — open parser-side issues

Tracking document for the GBIF `name-parser` (v4.x) fixes still needed to make the CoL backend
`feature/name-parser-v4` branch green. Every item below was classified **parser-side** by the
parser author (Markus) during the backend test sweep; the corresponding CoL fixtures are
intentionally left unchanged and will pass once the parser is fixed.

Backend pin while iterating: `name-parser.version = 4.1.2-SNAPSHOT` (must be pinned to a release
before the branch merges). Repro source: `backend/importer/src/test/resources/dwca/<key>/`.

---

## 1. Placeholder `species N` collapses to bare number + unranked  —  `dwca/17`, `testTree[25]`  ⚠️ HALF-FIXED (parser)

**Phrase fixed, rank still wrong.** Against `4.1.2-SNAPSHOT` (2026-06-13 13:20) the phrase is now kept
verbatim (`Allium species 1` ✅, was `Allium 1`) — but the **rank is still `UNRANKED`**, not `SPECIES`:

| | value |
|---|---|
| verbatim scientificName | `Allium species 1` |
| expected | `Allium species 1` &nbsp;`[species]` |
| v4 actual | `Allium species 1` &nbsp;`[unranked]` |

Remaining parser work: the `species N` / `sp. N` placeholder should infer rank `SPECIES`. Once fixed,
the CoL fixture `dwca/17/expected.tree` also needs `Allium sp. 1` → `Allium species 1` (verbatim phrase).

The informal placeholder loses both the `sp.` rendering and the species rank.

| | value |
|---|---|
| verbatim scientificName | `Allium species 1` (genus=`Allium`, specificEpithet=`species 1`) |
| expected | `Allium species 1` &nbsp;`[species]` |
| v4 actual | `Allium 1` &nbsp;`[unranked]` |

**Expected parse:** genus=`Allium`, phrase=`species 1` (rendered), rank=`SPECIES`, type=`INFORMAL`.
v4 drops the `sp.`/`species` token, keeps only the trailing `1`, and demotes the rank to `UNRANKED`.

---

## 2. Autonym with species author before `subsp.` — basionym author misplaced & duplicated  —  `dwca/19`, `testTree[27]`  ✅ FIXED

**Resolved.** `(Klatt)` is no longer misread as a subgenus (NameTokens only treats `(Word)` as a
subgenus before any species epithet). The mid-name species author span (`(Klatt) Baker`, `d'Urv.`,
`L.`) is captured and applied as the name's authorship for autonyms (ICN Art. 22.1/26.1 — the
autonym's final epithet bears no author of its own). The formatter now places autonym authorship by
code: botany after the species epithet, zoology (and unknown code) at the very end of the trinomen.
`Trimezia spathata (Klatt) Baker subsp. spathata` round-trips. Tests: `NameParserImplTest.autonymAuthorship`,
`NameFormatterTest.testBuildName`.

Autonyms in botany and zoology are treated differently:
https://www.iapt-taxon.org/icbn/frameset/0030Ch3Sec5a026.htm
https://www.iapt-taxon.org/icbn/frameset/0026Ch3Sec3a022.htm
https://code.iczn.org/types-in-the-species-group/article-72-general-provisions/?frame=1

The name should be parsed with authorships, but the name formatter should place the authorship differently if its zoo (at the very end) or botany (after the species).

Problem: A subspecies autonym where the species authorship sits **before** the `subsp.` epithet is
re-rendered with the basionym author wrongly inserted after the genus and the authorship repeated.

| | value |
|---|---|
| verbatim scientificName | `Trimezia spathata (Klatt) Baker subsp. spathata` |
| | (genus=`Trimezia`, specificEpithet=`spathata`, infraspecificEpithet=`spathata`, authorship=`(Klatt) Baker`, rank=subspecies, code=botany) |
| expected | formatter reconstructs to the botanical autonym just as the input is.
| v4 actual | `Trimezia (Klatt) spathata subsp. spathata (Klatt) Baker` &nbsp;`[subspecies]` |


---

## 3. Informal/unparsable names are over-normalized  —  `dwca/27`, `testTree[35]`  ✅ FIXED

**Resolved.** `Incertae_sedis` (underscore variant) is now detected as a `PLACEHOLDER` and thrown
unparsable, so the backend keeps the verbatim form (underscore preserved) rather than parsing it as a
binomial. Fix: the placeholder regex treats `_` as a separator (`incertae[\s_]*sedis`). Test:
`NameParserImplTest.unparsablePlaceholder`.

Underscore-joined IPNI placeholder tokens should be kept close to their verbatim form. Only
**properly parsable** names should be normalized.

| | value |
|---|---|
| verbatim (IPNI) | `Incertae_sedis` (genus / scientificName, `fam.`) |
| expected | `Incertae_sedis` &nbsp;`[family]` (underscore preserved) |
| v4 actual | `Incertae sedis` &nbsp;`[family]` (underscore normalized to space) |

**Rule:** for informal names, emit the verbatim form (`Incertae_sedis`); 
whitespace normalization or decoding char entities is good, but otherwise leave the name as it was if it is unparsable or for the phrase part of the informal names.

---

## 4. Formatter drops the publication year from rendered labels  —  `dwca/31`, `testTree[39]`  ✅ RESOLVED

**Passing against `4.1.2-SNAPSHOT` (2026-06-13 13:20 build).** The year renders for dwca/31 again
(`Pedicularis Tsoong, 1903`, `P.C.Tsoong, 1901`, …), so the parsed year is shown even though the dataset
is `code: botanical` — matching the decision that a parsed year should always be displayed (fungi/zoology
usage). `testTree[39]` is green and stable across re-runs. (The one transient failure seen earlier was
stale core/dao bytecode after the first `-U` snapshot pull, before those modules were rebuilt against the
new `name-parser-api`.)

`org.gbif.nameparser.util.NameFormatter.appendAuthorship` (the label formatter the backend's
tree printer uses) no longer appends `Authorship.year`, so every rendered label loses its year.

| | value |
|---|---|
| expected | `Pedicularis Tsoong, 1903` `[genus]`<br>`Pedicularis conspicua P.C.Tsoong, 1901` `[species]`<br>`Pedicularis inconspicua P.C.Tsoong, 1903` `[species]` |
| v4 actual | `Pedicularis Tsoong`<br>`Pedicularis conspicua P.C.Tsoong`<br>`Pedicularis inconspicua P.C.Tsoong` |

(The author-initial spacing, e.g. `P.C. Tsoong` → `P.C.Tsoong`, is already correct v4 behavior —
only the dropped year is wrong.)

**Same root cause blocks, once fixed:**
- the parser/core `SynonymTest.label` expectation (year missing from the formatted label);
- the bulk of `dwca/34` / `testTree[42]` (see CoL follow-ups below).

---

## Deferred / enhancement (parser), not blocking a specific test yet

- **per-authorship `imprintYear`** — currently only the combination/overall imprint year is exposed;
  CoL parked the per-authorship variant awaiting a parser release.
- **`Authorship.inAuthors`** — present on the model but inert (no accessors). CoL has nothing to
  persist/render until the parser exposes it.

---

# CoL-side follow-ups (NOT parser work — listed so nothing is lost)

These are backend fixture/code updates that are **blocked on item 4** or need a per-line review
once the parser stabilizes; they are not bugs in the parser:

- **`dwca/34` / `testTree[42]`** — the `expected.tree` is stale and needs a wholesale rewrite once
  the formatter (item 4) lands. Most diffs are *intended* v4 behavior already:
  - `nom.illeg.` → `nom. illeg.` and `(auct. )` → `(auct.)` (good normalization → update fixture);
  - sensu/auct `taxonomicNote` routing now appends `sensu Markus` to several names (working as intended);
  - one to confirm with Markus: `Canna ehemannii` now retains `nom. subnud. auct.` in the label.
- **`pu!chra` (`acefNameIssues`)** — ✅ already resolved CoL-side (commit `3fed66d9e`): v4 fully parses
  `Lamprostiba pu!chra`, so `PARTIALLY_PARSABLE_NAME` and `PARSED_NAME_DIFFERS` are no longer expected.

---

## Status snapshot (importer module)

`NormalizerTreeIT.testTree` against `name-parser 4.1.2-SNAPSHOT` (2026-06-13 13:20 build):
**2 failures — `[25]` and `[42]`** (stable across re-runs).

- ✅ `[27]` Trimezia autonym, `[35]` Incertae_sedis, `[39]` Pedicularis year — all pass (parser items 2/3/4).
- ✅ `[16]` dwca/8, `[17]` dwca/9 — resolved CoL-side via **option B**: the text tree renders the
  autonym author at the end (`Calendula incana subsp. incana Willd.`), matching the convention used by
  the six other botanical-autonym fixtures (`… subsp. parnassi Boiss.`, `… var. lanulosa Nuttall`, etc.).
  Fixtures `dwca/8` & `dwca/9` `expected.tree` updated to add the author; READMEs annotated. No code
  change — an earlier attempt to strip the author in `toSimpleName()` broke those six fixtures, since
  author-at-end is the established convention.
- ⚠️ `[25]` Allium (item 1) — phrase fixed, **rank still UNRANKED** → parser.
- ⏳ `[42]` dwca/34 — CoL `expected.tree` rewrite still pending (see CoL follow-ups).

`NormalizerACEFIT.acefNameIssues` passes.
