# Person Author Comparison, Phases 0 and 1: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-parse the author corpus with the fixed name parser and take a new baseline (phase 0), then split author
identity out of `AuthorComparator` into a pluggable `AuthorMatcher` without changing a single verdict (phase 1).

**Architecture:** Phase 0 adds a `CorpusReparser` to the test scope corpus tools in `dao`. It rewrites the parsed author
columns of the prod export with today's parser before the pairs are mined again. Phase 1 keeps `AuthorComparator` as the
one class callers use, with the year and name structure rules, and moves the string author cascade verbatim into
`StringAuthorMatcher` behind a new `AuthorMatcher` interface.

**Tech Stack:** Java 25, Maven, JUnit 4, name-parser-rust 0.2.2-SNAPSHOT (Java FFM binding), the corpus tools in
`dao/src/test/java/life/catalogue/matching/authorship/corpus/`.

**Spec:** `docs/2026-09-23-person-author-comparison.md` (sections "1. The seam" and "5. Evaluation", phases 0 and 1).

## Global Constraints

- Work in the worktree `/Users/markus/code/col/backend/.claude/worktrees/author-corpus` on branch `fix/author-nomcode`.
  Never `cd` to the main checkout.
- Every shell needs `export JAVA_HOME=$HOME/.sdkman/candidates/java/25.0.3-librca`.
- Run a single test class with `-Dsurefire.failIfNoSpecifiedTests=false` and without `-q`, which hides the test summary.
- Corpus tools run with `exec:exec` in a forked JVM (never `exec:java`), paths relative to the `dao` module. Before a
  report, `mvn -q -pl dao -am install -DskipTests`, or it measures the `api` jar in `~/.m2`.
- The prod export is `/Users/markus/code/col/backend/author-corpus.tsv.gz` (210 MB). It is never copied into git.
- Phase 1 acceptance: every existing test passes, and `AuthorVerdictDiff` shows **0 verdicts changed** on the re-parsed
  corpus.
- `new AuthorComparator(AuthorshipNormalizer)` and `compareAuthorsFirst(ScientificName, ScientificName)` keep their
  signatures, since GBIF's `matching-ws` calls both.
- `AuthorMatcher`, `StringAuthorMatcher`, `AuthorTeam` and `AuthorContext` live in `dao`, package
  `life.catalogue.matching.authorship`. A matcher holds no mutable state: one comparator is shared across threads via
  `NameIndexImpl.getAuthComp()`.
- Parser defects are never worked around in `AuthorshipNormalizer` or `AuthorComparator`. They get a
  gbif/name-parser-rust issue.
- Style: 2 space indent, 140 columns, `javax.annotation.Nullable`, comments explain why rather than what.
- Every commit message ends with `Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg`.

## Review Focus

- A name the parser can no longer parse, or no longer gives an author, must not abort a 12.4M row re-parse. It is
  dropped and counted, exactly as the export script would not have exported it (pinned in Task 2).
- An export rank string the current `Rank` enum does not know must not abort the re-parse. It is parsed as `UNRANKED`
  (pinned in Task 2).
- A pg COPY escaped authorship (a backslash, a tab) must survive the export → reparse → read round trip unchanged
  (pinned in Task 2).
- A `null` or empty authorship, or a team of `et al.` only, must never reach a matcher. The comparator answers
  `UNKNOWN` itself (pinned in Task 4).
- The year reaches a matcher exactly as given (`1878 [1879]`, `184?`), not parsed. The person matcher of phase 3 decides
  how to read it (pinned in Task 4).

---

### Task 1: Merge master and pin the parser fixes

Master carries name-parser-rust 0.2.2-SNAPSHOT with gbif/name-parser-rust#20, #21 and #22 fixed. The snapshot in
`~/.m2` may still be the one from 2026-09-04, which has none of the fixes. The test proves the fixed one is used.

**Files:**
- Modify: `parser/src/test/java/life/catalogue/parser/NameParserTest.java` (add one test and one helper after `authorships()`)

**Interfaces:**
- Consumes: `NameParser.PARSER.parse(Name, IssueContainer)` fills the given `Name`
- Produces: nothing new. Tasks 2 and 3 rely on the fixed parser being what `NameParser.PARSER` runs.

- [ ] **Step 1: Merge master**

```bash
cd /Users/markus/code/col/backend/.claude/worktrees/author-corpus
git fetch origin
git merge --no-edit origin/master
git show HEAD:pom.xml | grep 'name-parser-rust.version'
```
Expected: the merge completes without conflicts, and the pom shows `<name-parser-rust.version>0.2.2-SNAPSHOT</name-parser-rust.version>`.
If there is a conflict, stop and report it. Do not resolve it by guessing.

- [ ] **Step 2: Write the test**

Add to `NameParserTest`, after `authorships()`:

```java
  /**
   * A separately supplied authorship, as almost every ChecklistBank import has one, used to keep an "in" citation
   * inside the author, glue an all-capitals author onto the one before and read a generational "I" as an initial:
   * gbif/name-parser-rust#20, #21 and #22, fixed in 0.2.2.
   */
  @Test
  public void separateAuthorshipDefectsFixedUpstream() throws Exception {
    assertCombAuthors("Actinocyclus australis", "Grunow in Van Heurck, 1883", "Grunow");
    assertCombAuthors("Mastogloia emarginata", "Hustedt in Schmidt et al., 1925", "Hustedt");
    assertCombAuthors("Peperomia arctebaccata", "Trel. in J.F.Macbr.", "Trel.");
    assertCombAuthors("Aegerita punctiformis", "Lam. & DC.", "Lam.", "DC.");
    assertCombAuthors("Amarula aqua", "G. B. Sowerby I, 1825", "G.B.Sowerby I");
  }

  private static void assertCombAuthors(String name, String authorship, String... authors) {
    Name n = new Name();
    n.setScientificName(name);
    n.setAuthorship(authorship);
    n.setRank(Rank.SPECIES);
    NameParser.PARSER.parse(n, new IssueContainer.Simple());
    assertEquals(authorship, List.of(authors), n.getCombinationAuthorship().getAuthors());
  }
```

- [ ] **Step 3: Run it**

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/25.0.3-librca
mvn -U -q -pl parser -am install -DskipTests
mvn -pl parser test -Dtest=NameParserTest#separateAuthorshipDefectsFixedUpstream -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: PASS. `-U` fetches the current snapshot from GBIF Nexus.
If it fails with `[Grunow in Van Heurck]`, `[D.C.Lam.]` or `[I.G.B.Sowerby]`, the snapshot is stale. Build the parser
locally and run Step 3 again:
```bash
cd ~/code/gbif/name-parser-rust && git pull --ff-only
grep -m1 '<version>' bindings/java/pom.xml   # must print 0.2.2-SNAPSHOT
cargo build --release -p nameparser-ffi
mkdir -p bindings/java/native-staging/osx-aarch_64/native/osx-aarch_64
cp target/release/libnameparser_ffi.dylib bindings/java/native-staging/osx-aarch_64/native/osx-aarch_64/
mvn -q -f bindings/java/pom.xml install -DskipTests
cd /Users/markus/code/col/backend/.claude/worktrees/author-corpus
```

- [ ] **Step 4: Run the whole parser module and the author tests on the merged branch**

```bash
mvn -pl parser test
mvn -q -pl dao -am install -DskipTests
mvn -pl dao test -Dtest='AuthorComparatorTest,AuthorCorpusTest,CorpusEvaluatorTest,AuthorCorpusReportTest' -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: all pass. `AuthorCorpusTest` still reads the old sample, which carries its own parsed columns, so the parser
update cannot move it.

- [ ] **Step 5: Commit**

```bash
git add parser/src/test/java/life/catalogue/parser/NameParserTest.java
git commit -m "test(parser): pin the separate authorship fixes of name-parser-rust 0.2.2

gbif/name-parser-rust#20, #21 and #22: an in citation, an all-capitals author
after an ampersand and a generational I were misparsed whenever the authorship
came separately from the name, which is how almost every import parses it.

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 2: CorpusReparser

**Files:**
- Modify: `dao/src/test/java/life/catalogue/matching/authorship/corpus/ExportRow.java` (add `toRow()`)
- Modify: `dao/src/test/java/life/catalogue/matching/authorship/corpus/CorpusIO.java` (add `exportWriter`)
- Create: `dao/src/test/java/life/catalogue/matching/authorship/corpus/NameParserVersion.java`
- Create: `dao/src/test/java/life/catalogue/matching/authorship/corpus/CorpusReparser.java`
- Create: `dao/src/test/java/life/catalogue/matching/authorship/corpus/CorpusReparserTest.java`

**Interfaces:**
- Consumes: `CorpusIO.readGroups(File, Consumer<List<ExportRow>>)`, `ExportRow.of(String[])`, `ExportRow.team(List<String>)`,
  `NameParser.PARSER.parse(Name, IssueContainer)`
- Produces:
  - `String[] ExportRow.toRow()`, the inverse of `ExportRow.of`
  - `static TabWriter CorpusIO.exportWriter(File export)`
  - `static String NameParserVersion.get()`, e.g. `0.2.2-SNAPSHOT (name-parser-rust-0.2.2-SNAPSHOT.jar of 2026-09-23T12:00:00Z)`
  - `new CorpusReparser(NameParser)`, `@Nullable ExportRow reparse(ExportRow)`, `String reparse(File export, File out)`
    returning the statistics, and `main(<export> <reparsed export>)`

- [ ] **Step 1: Write the failing test**

`CorpusReparserTest.java`:

```java
package life.catalogue.matching.authorship.corpus;

import life.catalogue.common.io.TabWriter;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.NomCode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class CorpusReparserTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private final CorpusReparser reparser = new CorpusReparser(NameParser.PARSER);

  private static ExportRow row(String rank, String name, String authorship, List<String> comb, String combYear) {
    return new ExportRow(7, 2011, "n1", rank, NomCode.BOTANICAL, null, name, authorship,
      comb, List.of(), combYear, List.of(), List.of(), null, null);
  }

  /** the export holds the parse of the day a dataset was imported, name-parser-rust#20 among it */
  @Test
  public void rewritesAStaleParse() {
    ExportRow stale = row("SPECIES", "Actinocyclus australis", "Grunow in Van Heurck, 1883", List.of("Grunow in Van Heurck"), "1883");
    ExportRow r = reparser.reparse(stale);
    assertEquals(List.of("Grunow"), r.combAuthors());
    assertEquals("1883", r.combYear());
    // everything that is not a parse stays as exported, the code above all: the miner pairs by it
    assertEquals(7, r.nidx());
    assertEquals(2011, r.datasetKey());
    assertEquals("n1", r.nameId());
    assertEquals("SPECIES", r.rank());
    assertEquals(NomCode.BOTANICAL, r.code());
    assertEquals("Actinocyclus australis", r.scientificName());
    assertEquals("Grunow in Van Heurck, 1883", r.authorship());
  }

  @Test
  public void keepsAParseThatIsStillRight() {
    ExportRow linne = row("SPECIES", "Aus bus", "L., 1753", List.of("L."), "1753");
    assertEquals(linne, reparser.reparse(linne));
  }

  /** the export script only exports names with an author, so a name that lost its authors is dropped */
  @Test
  public void dropsANameWithoutAuthors() {
    assertNull(reparser.reparse(row("SPECIES", "Aus bus", "1883", List.of("1883"), null)));
    // not a scientific name at all
    assertNull(reparser.reparse(row("SPECIES", "Tobacco mosaic virus", "Smith", List.of("Smith"), null)));
  }

  @Test
  public void unknownRankIsUnranked() {
    ExportRow r = reparser.reparse(row("NO_SUCH_RANK", "Aus bus", "L.", List.of("L."), null));
    assertEquals(List.of("L."), r.combAuthors());
    assertEquals("NO_SUCH_RANK", r.rank());
  }

  @Test
  public void rowRoundTrip() {
    ExportRow r = new ExportRow(1, 2, "x", "SPECIES", null, "nom. illeg.", "Aus bus", "(Mill.) L. ex DC., 1753",
      List.of("L."), List.of("DC."), "1753", List.of("Mill."), List.of(), null, "Fr.");
    assertEquals(r, ExportRow.of(r.toRow()));
  }

  @Test
  public void file() throws Exception {
    File export = tmp.newFile("export.tsv.gz");
    try (TabWriter w = CorpusIO.exportWriter(export)) {
      w.write(row("SPECIES", "Actinocyclus australis", "Grunow in Van Heurck, 1883", List.of("Grunow in Van Heurck"), "1883").toRow());
      w.write(row("SPECIES", "Aus bus", "L., 1753", List.of("L."), "1753").toRow());
      w.write(row("SPECIES", "Aus bus", "1883", List.of("1883"), null).toRow());
    }
    File out = new File(tmp.getRoot(), "reparsed/export.tsv.gz");
    String stats = reparser.reparse(export, out);

    List<ExportRow> rows = new ArrayList<>();
    CorpusIO.readGroups(out, rows::addAll);
    assertEquals(2, rows.size());
    assertEquals(List.of("Grunow"), rows.get(0).combAuthors());
    assertEquals(List.of("L."), rows.get(1).combAuthors());

    assertTrue(stats, stats.contains("name parser: " + NameParserVersion.get()));
    // 3 rows read, the Grunow one changed, the year only one dropped
    assertTrue(stats, stats.matches("(?s).*\\ball\\s+3\\s+1\\s+1\\b.*"));
  }

  /** pg COPY escapes a backslash and a tab, and a re-parsed export must read back exactly as it was written */
  @Test
  public void escapedRoundTrip() throws Exception {
    ExportRow r = row("SPECIES", "Aus bus", "Smith\\Jones\tx", List.of("Smith"), null);
    File export = tmp.newFile("escaped.tsv.gz");
    try (TabWriter w = CorpusIO.exportWriter(export)) {
      w.write(r.toRow());
    }
    List<ExportRow> rows = new ArrayList<>();
    CorpusIO.readGroups(export, rows::addAll);
    assertEquals(List.of(r), rows);
  }

  @Test
  public void parserVersion() {
    String v = NameParserVersion.get();
    assertTrue(v, v.matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)? \\(.+ of .+\\)"));
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
mvn -pl dao test -Dtest=CorpusReparserTest -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: COMPILATION ERROR, since `CorpusReparser`, `NameParserVersion`, `ExportRow.toRow` and `CorpusIO.exportWriter`
do not exist yet.

- [ ] **Step 3: Add `toRow` to `ExportRow`**

Insert after `of(String[] row)`:

```java
  /**
   * The inverse of {@link #of(String[])}.
   */
  public String[] toRow() {
    return new String[]{
      String.valueOf(nidx), String.valueOf(datasetKey), nameId, rank, code == null ? null : code.name(), nomStatus,
      scientificName, authorship,
      team(combAuthors), team(combExAuthors), combYear,
      team(basAuthors), team(basExAuthors), basYear, sanctioningAuthor
    };
  }
```

- [ ] **Step 4: Add `exportWriter` to `CorpusIO`**

Insert before `pairWriter`:

```java
  public static TabWriter exportWriter(File export) throws IOException {
    return writer(export, ExportRow.COLUMNS);
  }
```

- [ ] **Step 5: Create `NameParserVersion`**

```java
package life.catalogue.matching.authorship.corpus;

import org.gbif.nameparser.rust.NameParserRust;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The name parser the corpus tools run with, to be written into what they produce: a corpus is only comparable to
 * another parsed by the same parser. A snapshot keeps its version across builds, so the date of its jar comes with it.
 */
public class NameParserVersion {
  private static final String POM = "/META-INF/maven/org.gbif.nameparser/name-parser-rust/pom.properties";

  private NameParserVersion() {
  }

  public static String get() {
    try (InputStream in = NameParserRust.class.getResourceAsStream(POM)) {
      Properties p = new Properties();
      if (in != null) {
        p.load(in);
      }
      Path jar = Path.of(NameParserRust.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      return p.getProperty("version", "unknown") + " (" + jar.getFileName() + " of " + Files.getLastModifiedTime(jar) + ")";
    } catch (Exception e) {
      return "unknown (" + e.getMessage() + ")";
    }
  }
}
```

- [ ] **Step 6: Create `CorpusReparser`**

```java
package life.catalogue.matching.authorship.corpus;

import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.ParsedNameUsage;
import life.catalogue.common.io.TabWriter;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.annotation.Nullable;

/**
 * Parses the names of an export again with the name parser on the classpath and rewrites their parsed author columns.
 * The prod export holds the parse of the day each dataset was imported, defects fixed since included
 * (gbif/name-parser-rust#20 to #22), so without this a corpus measures old parsers as much as the author comparison.
 * <p>
 * It parses the way an import does, name and authorship separately. A name the export script would not have exported
 * with today's parse - not scientific, no author, an author holding the team separator - is dropped.
 * <pre>
 * mvn -q -pl dao test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
 *   -Dexec.args="-Xmx4g --enable-native-access=ALL-UNNAMED -cp %classpath life.catalogue.matching.authorship.corpus.CorpusReparser \
 *     /path/to/author-corpus.tsv.gz target/author-corpus/reparsed/author-corpus.tsv.gz"
 * </pre>
 */
public class CorpusReparser {
  private static final int ROWS = 0;
  private static final int CHANGED = 1;
  private static final int DROPPED = 2;

  private final NameParser parser;
  private final Map<Integer, long[]> byDataset = new TreeMap<>();

  public CorpusReparser(NameParser parser) {
    this.parser = parser;
  }

  /**
   * @return the row with the parse of today, or null if the export script would not export it
   */
  @Nullable
  public ExportRow reparse(ExportRow row) {
    Name n = new Name();
    n.setScientificName(row.scientificName());
    n.setAuthorship(row.authorship());
    n.setRank(rank(row.rank()));
    n.setCode(row.code());
    Name p = parser.parse(n, IssueContainer.VOID).map(ParsedNameUsage::getName).orElse(null);
    if (p == null || p.getType() != NameType.SCIENTIFIC) {
      return null;
    }
    Authorship comb = p.getCombinationAuthorship();
    Authorship bas = p.getBasionymAuthorship();
    if (comb.getAuthors().isEmpty() && bas.getAuthors().isEmpty()) {
      return null;
    }
    if (Stream.of(comb.getAuthors(), comb.getExAuthors(), bas.getAuthors(), bas.getExAuthors())
        .flatMap(List::stream)
        .anyMatch(a -> a.indexOf(ExportRow.TEAM_SEPARATOR) >= 0)) {
      return null;
    }
    return new ExportRow(row.nidx(), row.datasetKey(), row.nameId(), row.rank(), row.code(), row.nomStatus(),
      row.scientificName(), row.authorship(),
      List.copyOf(comb.getAuthors()), List.copyOf(comb.getExAuthors()), comb.getYear(),
      List.copyOf(bas.getAuthors()), List.copyOf(bas.getExAuthors()), bas.getYear(),
      p.getSanctioningAuthor()
    );
  }

  private static Rank rank(String rank) {
    try {
      return Rank.valueOf(rank);
    } catch (IllegalArgumentException | NullPointerException e) {
      return Rank.UNRANKED;
    }
  }

  /**
   * @return the statistics per dataset: rows read, rows whose parse changed and rows dropped
   */
  public String reparse(File export, File out) throws IOException {
    try (TabWriter writer = CorpusIO.exportWriter(out)) {
      CorpusIO.readGroups(export, group -> {
        for (ExportRow row : group) {
          long[] counts = byDataset.computeIfAbsent(row.datasetKey(), k -> new long[3]);
          counts[ROWS]++;
          ExportRow r = reparse(row);
          if (r == null) {
            counts[DROPPED]++;
            continue;
          }
          if (!r.equals(row)) {
            counts[CHANGED]++;
          }
          try {
            writer.write(r.toRow());
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
      });
    }
    return render();
  }

  private String render() {
    StringBuilder sb = new StringBuilder();
    sb.append("name parser: ").append(NameParserVersion.get()).append('\n');
    sb.append(String.format("%-8s %12s %12s %12s%n", "dataset", "rows", "changed", "dropped"));
    long[] all = new long[3];
    byDataset.forEach((key, c) -> {
      sb.append(String.format("%-8d %,12d %,12d %,12d%n", key, c[ROWS], c[CHANGED], c[DROPPED]));
      for (int i = 0; i < 3; i++) {
        all[i] += c[i];
      }
    });
    sb.append(String.format("%-8s %,12d %,12d %,12d%n", "all", all[ROWS], all[CHANGED], all[DROPPED]));
    return sb.toString();
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 2 || !new File(args[0]).isFile()) {
      System.err.println("Usage: CorpusReparser <export.tsv[.gz]> <reparsed export.tsv[.gz]>");
      System.exit(1);
    }
    File out = new File(args[1]);
    String stats = new CorpusReparser(NameParser.PARSER).reparse(new File(args[0]), out);
    System.out.println(stats);
    Files.writeString(new File(out.getAbsoluteFile().getParentFile(), "reparse-stats.txt").toPath(), stats, StandardCharsets.UTF_8);
  }
}
```

A name that throws inside the parser has to be dropped rather than abort the run. `NameParser.parse` does not throw
since name-parser 5.0, so there is no catch. If Task 3's run on the full corpus dies on a row anyway, add a
`try/catch (RuntimeException)` around the `parser.parse` call that counts the row as dropped, plus a test with that row.

- [ ] **Step 7: Run the test**

```bash
mvn -pl dao test -Dtest=CorpusReparserTest -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: all 8 tests pass. Three failures have a known cause and a known fix:
- `file` fails on the statistics regex: print `stats`. The `all` line must hold `3` rows, `1` changed and `1` dropped,
  with the Grunow row changed, the Linnaeus row unchanged and the year only row dropped.
- `dropsANameWithoutAuthors` fails on the virus: today's parser treats `Tobacco mosaic virus` as a scientific name.
  Replace it with the BOLD BIN `BOLD:AAA0001`, which is never scientific.
- `escapedRoundTrip` fails: `TabWriter` and `TabReader` do not agree on pg COPY escaping. Every re-parse would then
  corrupt such authorships further. Stop and report it. Do not change `CorpusIO` without asking, since the miner reads
  the real pg COPY export through the same reader.

- [ ] **Step 8: Commit**

```bash
git add dao/src/test/java/life/catalogue/matching/authorship/corpus/
git commit -m "test(authorship): re-parse the author corpus with the parser of today

The prod export holds the parse each dataset got when it was imported, so the
corpus kept name-parser-rust defects that have since been fixed: 59,295 names
had an in citation inside an author. CorpusReparser runs every name through
the import's parse again and rewrites its author columns, dropping what the
export script would not have exported.

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 3: The re-parsed baseline

This task produces the numbers phase 1 is diffed against, and the new frozen sample.

**Files:**
- Modify: `dao/src/test/java/life/catalogue/matching/authorship/corpus/AuthorCorpusReport.java:162` (one header line)
- Modify: `dao/src/test/java/life/catalogue/matching/authorship/corpus/AuthorCorpusReportTest.java` (one test)
- Modify: `dao/src/test/resources/author-corpus/author-pairs-sample.tsv.gz` (regenerated)
- Modify: `dao/src/test/java/life/catalogue/matching/authorship/corpus/AuthorCorpusTest.java` (row count and pins)
- Modify: `docs/AUTHOR-CORPUS.md` (the re-parse step)
- Modify: `docs/2026-09-23-person-author-comparison.md` (an `## Outcome` section)

**Interfaces:**
- Consumes: `NameParserVersion.get()`, `CorpusReparser.main` (Task 2)
- Produces: `dao/target/author-corpus/reparsed/pairs.tsv.gz` and
  `dao/target/author-corpus/reparsed/verdicts-before-seam.tsv.gz`, which Task 4 diffs against

- [ ] **Step 1: Write the failing test**

Add to `AuthorCorpusReportTest`:

```java
  /** a corpus is only comparable to one parsed by the same parser */
  @Test
  public void headerNamesTheParser() {
    assertTrue(report.lines().limit(8).anyMatch(l -> l.equals("name parser: " + NameParserVersion.get())));
  }
```

- [ ] **Step 2: Run it to verify it fails**

```bash
mvn -pl dao test -Dtest=AuthorCorpusReportTest -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: FAIL in `headerNamesTheParser`.

- [ ] **Step 3: Add the header line**

In `AuthorCorpusReport.report`, directly after the line that appends `author map: ...`:

```java
    sb.append("name parser: ").append(NameParserVersion.get()).append('\n');
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn -pl dao test -Dtest=AuthorCorpusReportTest -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: PASS.

- [ ] **Step 5: Re-parse, mine and report the full corpus**

```bash
mvn -q -pl dao -am install -DskipTests
cd dao
mvn -q test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
  -Dexec.args="-Xmx4g --enable-native-access=ALL-UNNAMED -cp %classpath life.catalogue.matching.authorship.corpus.CorpusReparser /Users/markus/code/col/backend/author-corpus.tsv.gz target/author-corpus/reparsed/author-corpus.tsv.gz"
mvn -q test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
  -Dexec.args="-Xmx4g --enable-native-access=ALL-UNNAMED -cp %classpath life.catalogue.matching.authorship.corpus.AuthorPairMiner target/author-corpus/reparsed/author-corpus.tsv.gz target/author-corpus/reparsed/pairs.tsv.gz"
mvn -q test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
  -Dexec.args="-Xmx4g --enable-native-access=ALL-UNNAMED -cp %classpath life.catalogue.matching.authorship.corpus.AuthorCorpusReport target/author-corpus/reparsed/pairs.tsv.gz target/author-corpus/reparsed"
cp target/author-corpus/reparsed/verdicts.tsv.gz target/author-corpus/reparsed/verdicts-before-seam.tsv.gz
cd ..
```
Expected:
- `reparse-stats.txt` names parser 0.2.2-SNAPSHOT, and the `all` row reads about 12.4M rows. IRMNG (2007), WoRMS (2011),
  ITIS (2144), IPNI (2006) and ZooBank (2037) show tens of thousands changed between them.
- The report header shows the same parser.
- In the report's "Same act judged DIFFERENT by its authors", `Grunow` / `Grunow in Van Heurck` (416 names) is gone.

Write down the matrices (ALL, BOTANICAL, ZOOLOGICAL, per pair and weighted) and the miner's label counts from
`miner-stats.txt` for Step 8.

- [ ] **Step 6: Refresh the sample**

```bash
cd dao
mvn -q test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
  -Dexec.args="-Xmx4g -cp %classpath life.catalogue.matching.authorship.corpus.AuthorPairSampler target/author-corpus/reparsed/pairs.tsv.gz src/test/resources/author-corpus/author-pairs-sample.tsv.gz"
cd ..
mvn -pl dao test -Dtest=AuthorCorpusTest -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: `sample()` fails with the new row count. Set it in `AuthorCorpusTest.sample()`, then run again. Each
`assertPinned` prints `<scope> is now: a, b, c, d`, also into
`dao/target/surefire-reports/life.catalogue.matching.authorship.corpus.AuthorCorpusTest-output.txt`. Put exactly those
numbers into `all()`, `botanical()` and `zoological()`, and run once more.
Expected: all 4 tests pass.

- [ ] **Step 7: Document the re-parse step in `docs/AUTHOR-CORPUS.md`**

Change the diagram to

```
author-corpus-export.sql ──▶ author-corpus.tsv.gz ──CorpusReparser──▶ reparsed export ──AuthorPairMiner──▶ pairs.tsv.gz ──AuthorCorpusReport──▶ report.txt
      (run by hand)            (~200 MB, not in git)                                                                                        └─▶ verdicts.tsv.gz ──AuthorVerdictDiff
                                                                                               └──AuthorPairSampler──▶ author-pairs-sample.tsv.gz ──AuthorCorpusTest
```

and insert this as a new step between **1. Export** and **2. Mine**, renumbering the steps after it:

```markdown
**2. Re-parse.** The export holds the parse each dataset got when it was imported, including parser defects fixed
since. `CorpusReparser` runs every name through the import's parse again, name and authorship separately, and rewrites
the parsed author columns. A name the export script would not export with today's parse is dropped. It writes
`reparse-stats.txt` with the parser version and, per dataset, the rows read, changed and dropped. Mine the re-parsed
export, never the raw one. The report header names the parser too, and two reports are only comparable when it matches.

    mvn -q -pl dao test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
      -Dexec.args="-Xmx4g --enable-native-access=ALL-UNNAMED -cp %classpath life.catalogue.matching.authorship.corpus.CorpusReparser /path/to/author-corpus.tsv.gz target/author-corpus/reparsed/author-corpus.tsv.gz"
```

In "Refreshing the sample", replace "Only with a new export." with "Only with a new export or a new parser, after a re-parse."

- [ ] **Step 8: Record the baseline in the design record**

Append to `docs/2026-09-23-person-author-comparison.md`:

```markdown
## Outcome

**Phase 0, the re-parsed baseline** (<date>, name parser <version from the report header>). <N> of 12,373,803 names
changed their parse and <M> were dropped. Mining gave <SAME> `SAME`, <DIFF> `DIFF` and <DUBIOUS> `DUBIOUS` pairs.
The string matcher on it:

<the ALL matrix with and without years, copied from report.txt>

<one sentence per notable shift against the old baseline of 94.1% SAME judged EQUAL: which worklist entries went away>
```

Fill every `<…>` from Step 5's output. This is a record of numbers, so nothing may stay a placeholder.

- [ ] **Step 9: Commit**

```bash
git add dao/src/test/java/life/catalogue/matching/authorship/corpus/AuthorCorpusReport.java \
  dao/src/test/java/life/catalogue/matching/authorship/corpus/AuthorCorpusReportTest.java \
  dao/src/test/java/life/catalogue/matching/authorship/corpus/AuthorCorpusTest.java \
  dao/src/test/resources/author-corpus/author-pairs-sample.tsv.gz \
  docs/AUTHOR-CORPUS.md docs/2026-09-23-person-author-comparison.md
git commit -m "test(authorship): re-parsed corpus baseline and sample

The report header names the parser. The sample and its pins are taken from the
corpus re-parsed with name-parser-rust 0.2.2, which the person matcher will be
measured against.

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```

---

### Task 4: The seam

This is one atomic refactor. `AuthorComparator` keeps years and name structure, and the author cascade moves verbatim
into `StringAuthorMatcher`.

**Files:**
- Create: `dao/src/main/java/life/catalogue/matching/authorship/AuthorMatcher.java`
- Create: `dao/src/main/java/life/catalogue/matching/authorship/AuthorTeam.java`
- Create: `dao/src/main/java/life/catalogue/matching/authorship/AuthorContext.java`
- Create: `dao/src/main/java/life/catalogue/matching/authorship/StringAuthorMatcher.java`
- Modify: `dao/src/main/java/life/catalogue/matching/authorship/AuthorComparator.java`
- Modify: `dao/src/test/java/life/catalogue/matching/authorship/AuthorComparatorTest.java` (the helper at line 784 and the comment at line 661, nothing else)
- Create: `dao/src/test/java/life/catalogue/matching/authorship/AuthorComparatorSeamTest.java`
- Modify: `docs/2026-09-23-person-author-comparison.md` (Outcome, phase 1)

**Interfaces:**
- Consumes: `AuthorshipNormalizer.normalize(Authorship, NomCode)`, `AuthorshipNormalizer.lookup(List<String>, int, NomCode)`,
  `AuthorshipNormalizer.lookup(List<String>, NomCode)`, `TaxGroup` (module `vocab`, already a `dao` dependency)
- Produces, all used by phase 3:
  - `interface AuthorMatcher { Equality compareTeams(AuthorTeam t1, AuthorTeam t2, AuthorContext ctx, AuthorMatcher.Mode mode); }`
  - `enum AuthorMatcher.Mode { LAX, YEAR_CONFLICT, STRICT }`, with public final int fields `minCommonStart`,
    `lookupShorterThan` and `jaroDistance`
  - `record AuthorTeam(List<String> authors, @Nullable String year)`
  - `record AuthorContext(@Nullable NomCode code, @Nullable TaxGroup group)`
  - `class StringAuthorMatcher implements AuthorMatcher`, constructed as `new StringAuthorMatcher(AuthorshipNormalizer)`
  - `new AuthorComparator(AuthorMatcher)`,
    `Equality AuthorComparator.compare(Authorship, Authorship, @Nullable NomCode, @Nullable TaxGroup)` and
    `Equality AuthorComparator.compare(ScientificName, ScientificName, @Nullable TaxGroup)`. There is deliberately no
    public overload taking an `AuthorContext`: next to `compare(Authorship, Authorship, NomCode)` it would make every
    existing call with a literal `null` code ambiguous.

- [ ] **Step 1: Write the failing test**

`AuthorComparatorSeamTest.java`:

```java
package life.catalogue.matching.authorship;

import life.catalogue.api.model.Name;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.AuthorMatcher.Mode;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * What {@link AuthorComparator} hands to its {@link AuthorMatcher}: the comparator owns years, name structure and team
 * selection, a matcher only ever sees two non empty teams.
 */
public class AuthorComparatorSeamTest {

  record Call(AuthorTeam t1, AuthorTeam t2, AuthorContext ctx, Mode mode) {}

  static class RecordingMatcher implements AuthorMatcher {
    final List<Call> calls = new ArrayList<>();
    Equality answer = Equality.EQUAL;

    @Override
    public Equality compareTeams(AuthorTeam t1, AuthorTeam t2, AuthorContext ctx, Mode mode) {
      calls.add(new Call(t1, t2, ctx, mode));
      return answer;
    }
  }

  private final RecordingMatcher m = new RecordingMatcher();
  private final AuthorComparator comp = new AuthorComparator(m);

  private static Authorship auth(String year, String... authors) {
    return Authorship.yearAuthors(year, authors);
  }

  @Test
  public void laxHandsOverNormalizedTeamsYearsAndCode() {
    assertEquals(Equality.EQUAL, comp.compare(auth("1753", "L."), auth("1753", "Linné"), NomCode.BOTANICAL));
    assertEquals(List.of(new Call(new AuthorTeam(List.of("l"), "1753"), new AuthorTeam(List.of("linne"), "1753"),
      new AuthorContext(NomCode.BOTANICAL, null), Mode.LAX)), m.calls);
  }

  /** years within the tolerance of 11 but not equal: only close author strings still count */
  @Test
  public void yearConflict() {
    comp.compare(auth("1753", "L."), auth("1758", "Linné"), null);
    assertEquals(Mode.YEAR_CONFLICT, m.calls.get(0).mode());
  }

  /** further apart than the tolerance: the years decide and no author is compared */
  @Test
  public void differentYearsNeedNoMatcher() {
    assertEquals(Equality.DIFFERENT, comp.compare(auth("1753", "L."), auth("1790", "L."), null));
    assertTrue(m.calls.isEmpty());
  }

  /** existing behaviour the person matcher's UNKNOWN meets: in a year conflict, unknown authors are a mismatch */
  @Test
  public void unknownAuthorsInAYearConflictAreDifferent() {
    m.answer = Equality.UNKNOWN;
    assertEquals(Equality.DIFFERENT, comp.compare(auth("1753", "L."), auth("1758", "L."), null));
  }

  /** the lax comparison keeps the ex authors, sources leave them out all the time */
  @Test
  public void laxKeepsExAuthors() {
    Authorship ex = auth(null, "Benth.");
    ex.setExAuthors(new ArrayList<>(List.of("Pohl")));
    comp.compare(ex, auth(null, "Pohl"), NomCode.ZOOLOGICAL);
    assertEquals(List.of("benth", "pohl"), m.calls.get(0).t1().authors());
  }

  /** the strict comparison selects the team by code: the ex author in zoology, the author in botany */
  @Test
  public void strictSelectsTheTeamByCode() {
    Authorship ex = auth(null, "Benth.");
    ex.setExAuthors(new ArrayList<>(List.of("Pohl")));
    comp.compareStrict(ex, auth(null, "Pohl"), NomCode.ZOOLOGICAL, 0);
    comp.compareStrict(ex, auth(null, "Pohl"), NomCode.BOTANICAL, 0);
    assertEquals(new Call(new AuthorTeam(List.of("pohl"), null), new AuthorTeam(List.of("pohl"), null),
      new AuthorContext(NomCode.ZOOLOGICAL, null), Mode.STRICT), m.calls.get(0));
    assertEquals(List.of("benth"), m.calls.get(1).t1().authors());
    assertEquals(Mode.STRICT, m.calls.get(1).mode());
  }

  @Test
  public void neverAnEmptyTeam() {
    assertEquals(Equality.UNKNOWN, comp.compare(auth(null, "L."), new Authorship(), null));
    assertEquals(Equality.UNKNOWN, comp.compare(auth(null, "L."), null, null));
    assertEquals(Equality.UNKNOWN, comp.compare(auth(null, "L."), auth(null, "et al."), null));
    assertTrue(m.calls.isEmpty());
  }

  /** a matcher gets the year as given, parsing it is its own business */
  @Test
  public void rawYear() {
    comp.compare(auth("1878 [1879]", "Smith"), auth("1878", "Smith"), null);
    assertEquals("1878 [1879]", m.calls.get(0).t1().year());
  }

  @Test
  public void namesPassCodeAndGroup() {
    Name n1 = new Name();
    n1.setCombinationAuthorship(auth(null, "Sw."));
    Name n2 = new Name();
    n2.setCode(NomCode.ZOOLOGICAL);
    n2.setCombinationAuthorship(auth(null, "Swainson"));
    comp.compare(n1, n2, TaxGroup.Molluscs);
    assertEquals(new AuthorContext(NomCode.ZOOLOGICAL, TaxGroup.Molluscs), m.calls.get(0).ctx());
    // without a group as before
    comp.compare(n1, n2);
    assertEquals(new AuthorContext(NomCode.ZOOLOGICAL, null), m.calls.get(1).ctx());
    // authorships take a group too
    comp.compare(auth(null, "Sw."), auth(null, "Swainson"), NomCode.ZOOLOGICAL, TaxGroup.Molluscs);
    assertEquals(new AuthorContext(NomCode.ZOOLOGICAL, TaxGroup.Molluscs), m.calls.get(2).ctx());
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
mvn -pl dao test -Dtest=AuthorComparatorSeamTest -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: COMPILATION ERROR, since `AuthorMatcher`, `AuthorTeam` and `AuthorContext` do not exist yet.

- [ ] **Step 3: Create `AuthorMatcher`, `AuthorTeam` and `AuthorContext`**

`AuthorMatcher.java`:

```java
package life.catalogue.matching.authorship;

import life.catalogue.matching.Equality;

/**
 * Decides whether two author teams name the same authors. {@link AuthorComparator} does everything around it: it
 * compares the years, lines up combination against basionym authorship and selects which authors of a team count
 * under which code. It never hands over an empty team.
 */
public interface AuthorMatcher {

  /**
   * How strict a comparison is, as the comparator needs it.
   */
  enum Mode {
    /** the years agree, or at least one is missing */
    LAX(4, 4, 90),
    /** both years are given and differ, but within the tolerance: only close author strings still count */
    YEAR_CONFLICT(12, 4, 99),
    /** {@link AuthorComparator#compareStrict}: every author is looked up in the author map, no fuzzy surnames */
    STRICT(4, Integer.MAX_VALUE, 100);

    /** the length of a common start that makes two surnames match */
    public final int minCommonStart;
    /** authors shorter than this are looked up in the author map before they are compared */
    public final int lookupShorterThan;
    /** the Jaro-Winkler similarity in percent above which two surnames match */
    public final int jaroDistance;

    Mode(int minCommonStart, int lookupShorterThan, int jaroDistance) {
      this.minCommonStart = minCommonStart;
      this.lookupShorterThan = lookupShorterThan;
      this.jaroDistance = jaroDistance;
    }
  }

  /**
   * @param t1 a team of at least one author
   * @param t2 a team of at least one author
   */
  Equality compareTeams(AuthorTeam t1, AuthorTeam t2, AuthorContext ctx, Mode mode);
}
```

`AuthorTeam.java`:

```java
package life.catalogue.matching.authorship;

import life.catalogue.common.tax.AuthorshipNormalizer;

import java.util.List;

import javax.annotation.Nullable;

/**
 * The authors of one team as {@link AuthorshipNormalizer#normalize(org.gbif.nameparser.api.Authorship,
 * org.gbif.nameparser.api.NomCode)} gives them, already selected by code, with the year of the authorship.
 *
 * @param year the year as given, not parsed: "1753", "184?", "1878 [1879]"
 */
public record AuthorTeam(List<String> authors, @Nullable String year) {
}
```

`AuthorContext.java`:

```java
package life.catalogue.matching.authorship;

import life.catalogue.api.vocab.TaxGroup;

import org.gbif.nameparser.api.NomCode;

import javax.annotation.Nullable;

/**
 * What is known about the names whose authors are compared.
 *
 * @param code  selects the author map abbreviations are looked up in
 * @param group the taxonomic group of the names, for a matcher that knows which groups a person worked on
 */
public record AuthorContext(@Nullable NomCode code, @Nullable TaxGroup group) {
}
```

- [ ] **Step 4: Create `StringAuthorMatcher`, moving code out of `AuthorComparator`**

Create `StringAuthorMatcher.java` with the new `compareTeams` below. Then **move** these members of `AuthorComparator`,
character for character except the one change of visibility noted:
- `compareNormalizedAuthorteam(List, List, int, int)`
- `jaro(String, String)`
- `compare(Author, Author, int, int)` (stays `@VisibleForTesting static`, package private)
- `compoundSurnamesMatch(Author, Author, int, int)`
- `surnamesMatch(String, String, int, int)`

```java
package life.catalogue.matching.authorship;

import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.AuthorshipNormalizer.Author;
import life.catalogue.matching.Equality;
import life.catalogue.matching.similarity.JaroWinkler;

import org.gbif.nameparser.api.NomCode;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.google.common.annotations.VisibleForTesting;

/**
 * Compares authors as strings. Each is folded to lower case ASCII, read as initials and a surname and compared by a
 * rule cascade: identical, a Jaro-Winkler similarity, a common start, a compound surname cited by its first part.
 * Abbreviations are expanded with the author map of the code first. Two teams are equal as soon as any one author
 * of one equals any one author of the other.
 */
public class StringAuthorMatcher implements AuthorMatcher {
  private final AuthorshipNormalizer normalizer;

  public StringAuthorMatcher(AuthorshipNormalizer normalizer) {
    this.normalizer = normalizer;
  }

  /**
   * Tries three comparisons: string equality, the surname rules, and the surname rules again once every author is
   * looked up in the author map.
   */
  @Override
  public Equality compareTeams(AuthorTeam t1, AuthorTeam t2, AuthorContext ctx, Mode mode) {
    final NomCode mapCode = ctx.code();
    List<String> authorTeam1 = normalizer.lookup(t1.authors(), mode.lookupShorterThan, mapCode);
    List<String> authorTeam2 = normalizer.lookup(t2.authors(), mode.lookupShorterThan, mapCode);
    Equality equality = compareNormalizedAuthorteam(authorTeam1, authorTeam2, mode.minCommonStart, mode.jaroDistance);
    if (equality != Equality.EQUAL) {
      // try again by looking up entire author strings
      List<String> authorTeam1l = normalizer.lookup(authorTeam1, mapCode);
      List<String> authorTeam2l = normalizer.lookup(authorTeam2, mapCode);
      // only compare again if the queue is actually different then before
      if (!authorTeam1.equals(authorTeam1l) || !authorTeam2.equals(authorTeam2l)) {
        equality = compareNormalizedAuthorteam(authorTeam1l, authorTeam2l, mode.minCommonStart, mode.jaroDistance);
      }
    }
    return equality;
  }

  // compareNormalizedAuthorteam, jaro, compare(Author, Author, int, int), compoundSurnamesMatch, surnamesMatch:
  // moved here verbatim from AuthorComparator
}
```

The trailing comment marks where the moved members go. Delete it once they are pasted in.

- [ ] **Step 5: Rewrite `AuthorComparator` around the matcher**

Replace the fields, constructor, the author team plumbing and every call of the old 7 argument `compareAuthorteam`. The
old `minCommonSubstring` field and the constants `MIN_AUTHOR_LENGTH_WITHOUT_LOOKUP` and `MIN_JARO_SURNAME_DISTANCE`
go; `Mode` carries their values. Keep the class javadoc and add to it: "Who an author is, is decided by an
{@link AuthorMatcher}: this class keeps what is about the structure of a name - the year tolerances, combination against
basionym authorship and which team counts under which code. The default matcher compares strings, see
{@link StringAuthorMatcher}."

```java
  private final AuthorMatcher matcher;

  public AuthorComparator(AuthorshipNormalizer normalizer) {
    this(new StringAuthorMatcher(normalizer));
  }

  public AuthorComparator(AuthorMatcher matcher) {
    this.matcher = matcher;
  }

  public Equality compare(@Nullable Authorship a1, @Nullable Authorship a2) {
    return compare(a1, a2, (NomCode) null);
  }

  /**
   * @param code the nomenclatural code of the names, which selects the author map to look up abbreviations in.
   *             Unlike in {@link #compareStrict(Authorship, Authorship, NomCode, int)} it does not select the relevant
   *             author team: ex authors keep being compared, as sources leave them out all the time.
   */
  public Equality compare(@Nullable Authorship a1, @Nullable Authorship a2, @Nullable NomCode code) {
    return compare(a1, a2, code, null);
  }

  /**
   * @param group the taxonomic group of both names if known, for a matcher that knows which groups a person worked on
   */
  public Equality compare(@Nullable Authorship a1, @Nullable Authorship a2, @Nullable NomCode code, @Nullable TaxGroup group) {
    return compareAuthorships(a1, a2, new AuthorContext(code, group));
  }

  /**
   * Compares the authorteams and year of two names.
   * If given both the year and authorteam needs to match to yield an EQUAL,
   * with a small difference of 11 years being accepted.
   * Not an overload of compare: next to compare(a1, a2, NomCode) every call passing a literal null would be ambiguous.
   */
  private Equality compareAuthorships(@Nullable Authorship a1, @Nullable Authorship a2, AuthorContext ctx) {
    // compare year first - simpler to calculate
    var yc = new YearComparator(11, a1, a2);
    Equality result = yc.compare();
    // compare authors if it's not already different
    if (result != Equality.DIFFERENT) {
      Equality aresult;
      if (result == Equality.EQUAL || !yc.hasYears()) {
        aresult = compareAuthorteam(a1, a2, null, ctx, Mode.LAX);
      } else {
        aresult = compareAuthorteam(a1, a2, null, ctx, Mode.YEAR_CONFLICT);
        // if unknown years and author is also unknown, make this a mismatch
        if (aresult == Equality.UNKNOWN) {
          return Equality.DIFFERENT;
        }
      }
      return result.and(aresult);
    }
    return result;
  }
```

In `compareAuthorsFirst(Authorship, Authorship, NomCode)`, replace the first statement's call with
`compareAuthorteam(a1, a2, null, new AuthorContext(code, null), Mode.LAX)`. Keep the rest.

Replace `compare(ScientificName, ScientificName)` with:

```java
  /**
   * Does a comparison of recombination and basionym authorship using the author compare method once for the recombination authorship and once for the basionym.
   */
  public Equality compare(ScientificName n1, ScientificName n2) {
    return compare(n1, n2, (TaxGroup) null);
  }

  /**
   * @param group the taxonomic group of both names if known, for a matcher that knows which groups a person worked on
   */
  public Equality compare(ScientificName n1, ScientificName n2, @Nullable TaxGroup group) {
    final AuthorContext ctx = new AuthorContext(ObjectUtils.coalesce(n1.getCode(), n2.getCode()), group);
    return compare(n1, n2, (a1, a2) -> compareAuthorships(a1, a2, ctx));
  }
```

In `compareStrict(Authorship, Authorship, NomCode, int)`, the first statement becomes
`Equality result = compareAuthorteam(a1, a2, code, new AuthorContext(code, null), Mode.STRICT);`.

Replace both `compareAuthorteam` methods with:

```java
  /**
   * @param code the code determines which ex author to use and which author map. If null both authorteams are used for matching
   */
  @VisibleForTesting
  Equality compareAuthorteam(Authorship a1, Authorship a2, NomCode code) {
    return compareAuthorteam(a1, a2, code, new AuthorContext(code, null), Mode.LAX);
  }

  /**
   * Selects and normalizes both teams and hands them to the matcher, never an empty one.
   *
   * @param teamCode determines which of authors and ex authors are compared. If null both are
   */
  private Equality compareAuthorteam(@Nullable Authorship a1, @Nullable Authorship a2, @Nullable NomCode teamCode,
                                     AuthorContext ctx, Mode mode) {
    // convert to all lower case, ascii only, no punctuation but commas seperating authors and normed whitespace
    List<String> team1 = AuthorshipNormalizer.normalize(a1, teamCode);
    List<String> team2 = AuthorshipNormalizer.normalize(a2, teamCode);
    if (team1.isEmpty() || team2.isEmpty()) {
      return Equality.UNKNOWN;
    }
    return matcher.compareTeams(new AuthorTeam(team1, a1.getYear()), new AuthorTeam(team2, a2.getYear()), ctx, mode);
  }
```

Add the imports `life.catalogue.api.vocab.TaxGroup` and `life.catalogue.matching.authorship.AuthorMatcher.Mode`. Remove
every import and the `LOG` field if they are now unused (`JaroWinkler`, `StringUtils`, the static `Author` import,
`Logger`, `LoggerFactory`).

- [ ] **Step 6: Point the two test references at the moved code**

In `AuthorComparatorTest`, the helper at line 784 becomes:

```java
  private void assertAuth(AuthorshipNormalizer.Author a1, Equality eq, AuthorshipNormalizer.Author a2) {
    assertEquals(eq, StringAuthorMatcher.compare(a1, a2, AuthorMatcher.Mode.LAX.minCommonStart, AuthorMatcher.Mode.LAX.jaroDistance));
  }
```

The old helper passed `MIN_AUTHOR_LENGTH_WITHOUT_LOOKUP` (4) as the common start and `MIN_JARO_SURNAME_DISTANCE` (90),
the same values as `LAX`. In the comment at line 661, replace `AuthorComparator.compare(Author, Author, ...)` with
`StringAuthorMatcher.compare(Author, Author, ...)`. Change nothing else in this file.

- [ ] **Step 7: Run the new and the old tests**

```bash
mvn -q -pl dao -am install -DskipTests
mvn -pl dao test -Dtest='AuthorComparatorSeamTest,AuthorComparatorTest,AuthorBucketerTest,YearComparatorTest,AuthorCorpusTest,CorpusEvaluatorTest,AuthorCorpusReportTest,ChangedMatcherTest' -Dsurefire.failIfNoSpecifiedTests=false
mvn -pl core test -Dtest='UsageMatcherTest,NameIdentityTest,BasionymSorterTest' -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: all pass. A failure in anything but `AuthorComparatorSeamTest` means the move was not verbatim. Fix the move,
never the test.

- [ ] **Step 8: Prove 0 changed verdicts on the corpus**

```bash
cd dao
mvn -q test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
  -Dexec.args="-Xmx4g -cp %classpath life.catalogue.matching.authorship.corpus.AuthorCorpusReport target/author-corpus/reparsed/pairs.tsv.gz target/author-corpus/reparsed"
mvn -q test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
  -Dexec.args="-cp %classpath life.catalogue.matching.authorship.corpus.AuthorVerdictDiff target/author-corpus/reparsed/verdicts-before-seam.tsv.gz target/author-corpus/reparsed/verdicts.tsv.gz"
cd ..
```
Expected: `Compared <n> pairs: 0 verdicts changed, 0 fixed, 0 regressed.` Anything else fails the task. Find the moved
line that differs.

- [ ] **Step 9: Run the full unit suites of dao and core**

```bash
mvn -pl dao,core test
```
Expected: BUILD SUCCESS.

- [ ] **Step 10: Record phase 1 in the design record**

Append to the `## Outcome` section of `docs/2026-09-23-person-author-comparison.md`:

```markdown
**Phase 1, the seam** (<date>). `AuthorMatcher`, `StringAuthorMatcher`, `AuthorTeam` and `AuthorContext` as designed;
`Mode` carries the parameters the comparator used to pass (`LAX` 4/4/90, `YEAR_CONFLICT` 12/4/99, `STRICT` 4/all/100).
Deviation: the comparator's new overloads take a `TaxGroup` next to the code rather than an `AuthorContext`, since a
public `compare(Authorship, Authorship, AuthorContext)` would make every existing call with a `null` code ambiguous.
On the re-parsed corpus `AuthorVerdictDiff` found 0 of <n> verdicts changed. A matcher's `UNKNOWN` in a year conflict
still becomes `DIFFERENT`, the rule the comparator already had, and phase 3's relatives policy meets it there.
```

Fill `<date>` with today and `<n>` with the pair count from Step 8's `AuthorVerdictDiff` line.

- [ ] **Step 11: Commit**

```bash
git add dao/src/main/java/life/catalogue/matching/authorship/ \
  dao/src/test/java/life/catalogue/matching/authorship/AuthorComparatorTest.java \
  dao/src/test/java/life/catalogue/matching/authorship/AuthorComparatorSeamTest.java \
  docs/2026-09-23-person-author-comparison.md
git commit -m "refactor(authorship): author identity behind an AuthorMatcher

AuthorComparator keeps the years, combination against basionym authorship and
the team selection by code; who an author is, is decided by an AuthorMatcher.
The string cascade moves verbatim into StringAuthorMatcher, the default of the
existing constructor, so every caller and GBIF's matching-ws stay unchanged.
AuthorContext carries the code and an optional TaxGroup for the person matcher
to come. 0 verdicts of the re-parsed corpus changed.

Claude-Session: https://claude.ai/code/session_01Wh268z3E46ReEyUwu4uaFg"
```
