# Regenerating authormap.txt

The runtime loads only `api/src/main/resources/authorship/authormap.txt`
(`canonical <TAB> code <TAB> aliases…`, code ∈ BOT/ZOO/ANY). It is regenerated
offline by `AuthorMapGenerator` (test scope, never run by the build).

## Sources (precedence: manual > wikidata > existing > ipni/huh)
- `authormap-manual.txt` — committed, hand-curated. Highest precedence. Home for
  corrections and for authors preserved from the diff report.
- Wikidata — live SPARQL: P428 (botanist abbrev → BOT), P835 (zoologist citation
  → ZOO), both → ANY. No download needed.
- `authormap.txt` itself — the IPNI-derived base, kept for continuity + diff.
- Optional IPNI / HUH TSV dumps — download manually, pass as `ipni=/path/dump.tsv`
  / `huh=/path/dump.tsv`; adjust column indices in the generator if the dump layout differs.

## Run
    mvn -q -pl api exec:java -Dexec.classpathScope=test \
      -Dexec.mainClass=life.catalogue.common.tax.authormap.AuthorMapGenerator \
      -Dexec.args="api/src/main/resources/authorship"

## After running
1. Review `authormap-diff-report.txt` — every removed canonical/alias.
2. Move anything worth keeping into `authormap-manual.txt` and re-run.
3. `git diff` the regenerated `authormap.txt`, run `mvn -pl api test`, commit.

## Compound surnames in a canonical

A canonical is read as `initials… surname`, the surname being its last word. Parts of a compound
surname must therefore stay spelled out: `J B G M Bory de Saint-Vincent`, not
`J B B de Saint-Vincent`. The short form invents an initial "B" out of "Bory", which then conflicts
with the real initials a source cites ("J.B.M. Bory de St. Vincent") and makes the two compare as
different authors. The IPNI-derived base carries that mangling in a few hundred
`initials + particle + surname` canonicals; correct them in `authormap-manual.txt` as they surface.
See [backend#1595](https://github.com/CatalogueOfLife/backend/issues/1595).
