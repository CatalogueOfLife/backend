# Maintaining authormap.txt

The runtime loads only `api/src/main/resources/authorship/authormap.txt`
(`canonical <TAB> code <TAB> aliases…`, code ∈ BOT/ZOO/ANY). Every alias is a lookup key for the
canonical in its row.

The file is **curated in place**: fix a canonical, add an alias or add an author by editing it
directly. There is no separate curation file and no upstream dump to regenerate it from. Two developer
tools work on it, both test scope and never run by the build:

- `AuthorMapGenerator` grows the map with authors from Wikidata.
- `AuthorMapSurnameFix` repairs the IPNI-derived initials canonicals against the IPNI API.

The root pom pins exec's `mainClass`, so run both with `-DmainClass`, not `-Dexec.mainClass`.

What an edit does to author matching is measured against the corpus described in
[AUTHOR-CORPUS.md](AUTHOR-CORPUS.md). Its report also lists the alias candidates the map lacks, ranked by
the number of names they affect.

## Where the rows come from
- **The IPNI base**: the initials canonicals such as `C G D Nees von Esenbeck`. They were
  generated in 2015 for GBIF's checklistbank from an IPNI author export whose source was not kept,
  and ported here in 2018 as `abbreviation <TAB> full name <TAB> canonical`. Every such row still
  carries IPNI's standard form as its first alias and the full name after it. IPNI offers no bulk
  author download to regenerate them from.
- **Wikidata** (added July 2026): P428 (botanist abbreviation, only recorded as BOT), P835
  (zoologist author citation, imported as an alias → ZOO), both → ANY. The canonical is the English
  label, a full name.
- **Hand edits** made directly in the file.

## Growing the map from Wikidata
    mvn -q -pl api exec:java -Dexec.classpathScope=test \
      -DmainClass=life.catalogue.common.tax.authormap.AuthorMapGenerator \
      -Dexec.args="api/src/main/resources/authorship"

It reads the current file, pages through the live Wikidata SPARQL endpoint, merges both and rewrites
the file sorted.
Precedence is existing map > Wikidata: a canonical and its code come from the existing row, so every
hand correction survives a regeneration, and Wikidata only adds aliases and new authors. Rows are
joined when they share a multi-word alias (a full name), so a Wikidata person can also fold two
existing rows into one. An alias key held by several authors stays with the first existing one and is
dropped from the others.

Afterwards:
1. Review `authormap-diff-report.txt`, which lists every canonical and alias key that disappeared.
2. Add back anything worth keeping by editing `authormap.txt`, then re-run.
3. `git diff` the file, run `mvn -pl api test`, commit.

## Compound surnames in a canonical
A canonical is read as `initials… surname`, the surname being its last word; the word before a
nobiliary particle is also compared as the first part of a compound surname. Parts of a compound
surname must therefore stay spelled out: `J B G M Bory de Saint-Vincent`, not `J B B de Saint-Vincent`.
The short form invents an initial "B" out of "Bory", which then conflicts with the real initials a
source cites ("J.B.M. Bory de St. Vincent") and makes the two compare as different authors
([backend#1595](https://github.com/CatalogueOfLife/backend/issues/1595)).

The 2015 generator made exactly that mistake: it took the last word of the full name as the surname.
`AuthorMapSurnameFix` repaired the IPNI base in place in September 2026
([backend#1597](https://github.com/CatalogueOfLife/backend/issues/1597)). For every base row with at
least two initials it asks the public IPNI API for the author behind the row's standard form
(`https://www.ipni.org/api/1/search?q=author%20std:Nees&f=f_authors`) and uses IPNI's separate surname field:
- a word collapsed into an initial is spelled out again when IPNI's surname starts with it
  (`A A F von Waldheim` → `A A Fischer von Waldheim`, `H R López` → `H Ruiz López`). A canonical only
  ever grows leftward: IPNI's surname is itself inconsistent the other way (`Bory`, `Kerner`,
  `Rochebrune` hold one part only), so it never shortens one;
- a generational suffix is dropped (`B L T Sr.` → `B L Turner`), and the younger of a father/son pair
  that would then share a canonical gets the filius `f.` IPNI uses itself (`J Kickx f.`);
- everything else doubtful (no full name alias, an IPNI surname the row does not know, more initials
  than IPNI has forenames) only goes to a review report.

It rewrites the canonical column of the changed lines only, without re-sorting, and caches the IPNI answers.
On the repaired file it changes nothing. Re-run it after adding initials canonicals by hand:

    mvn -q -pl api exec:java -Dexec.classpathScope=test \
      -DmainClass=life.catalogue.common.tax.authormap.AuthorMapSurnameFix \
      -Dexec.args="api/src/main/resources/authorship/authormap.txt api/target/authormap-surname-report.txt api/target/ipni-authors.tsv"

Compound surnames without a particle (`Ruiz López`) are now spelled out correctly, but the author
comparison still reads only their last word as the surname: telling a first surname part from a middle
name without a particle is not safe (`Schultz Bip.` is not every `Schultz`).
