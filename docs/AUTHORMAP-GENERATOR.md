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
