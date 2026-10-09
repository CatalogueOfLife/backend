package life.catalogue.importer.corpus;

import java.io.*;
import java.util.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

/**
 * Runs the corpus tools on the small committed fixtures, which hold a few records of every input flavour:
 * a name with a separate authorship, a single string, atoms only, atoms next to a scientific name, author atoms,
 * dataset settings, informal names, identifiers, viruses. The assertions pin what the interpretation does today,
 * odd outcomes included, so a change of them shows up here first and in a corpus diff after.
 */
public class InterpreterCorpusTest {
  static final File EXPORT_FIXTURE = resource("fixture-export.tsv");
  static final File PARSER_FIXTURE = resource("fixture-parser.tsv");

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  static File resource(String name) {
    try {
      return new File(InterpreterCorpusTest.class.getResource("/interpreter-corpus/" + name).toURI());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static List<String> lines(File runDir) throws IOException {
    try (BufferedReader r = CorpusIO.reader(new File(runDir, InterpreterCorpusRunner.OUTPUT))) {
      return r.lines().toList();
    }
  }

  /**
   * @return the output rows of a run by their row number
   */
  static Map<Long, String[]> rows(File runDir) throws IOException {
    Map<Long, String[]> rows = new LinkedHashMap<>();
    for (String l : lines(runDir).subList(1, lines(runDir).size())) {
      String[] row = CorpusIO.split(l, Field.values().length);
      rows.put(Long.parseLong(row[Field.ROW.ordinal()]), row);
    }
    return rows;
  }

  static String get(Map<Long, String[]> rows, long row, Field f) {
    return rows.get(row)[f.ordinal()];
  }

  @Test
  public void runExport() throws Exception {
    File one = tmp.newFolder("one");
    File four = tmp.newFolder("four");
    String meta = new InterpreterCorpusRunner().run(EXPORT_FIXTURE, one, 1, 42, 1, 0);
    new InterpreterCorpusRunner().run(EXPORT_FIXTURE, four, 1, 42, 4, 0);
    assertTrue(meta, meta.contains("records: 30 interpreted (33 weighted)"));
    // the order and content of the output do not depend on the number of threads
    assertEquals(lines(one), lines(four));

    var rows = rows(one);
    assertEquals(30, rows.size());
    // the settings rows of the export are no records: the first record is in line 5
    assertEquals(5L, (long) rows.keySet().iterator().next());

    // name and separate authorship, with the nomenclatural code of the dataset settings
    assertEquals("3", get(rows, 5, Field.N));
    assertEquals("1001", get(rows, 5, Field.DATASET));
    assertEquals("Abies alba Mill.", get(rows, 5, Field.LABEL));
    assertEquals("Mill.", get(rows, 5, Field.AUTHORSHIP));
    assertEquals("BOTANICAL", get(rows, 5, Field.CODE));
    assertEquals("Mill.", get(rows, 5, Field.COMBINATION_AUTHORSHIP));
    // a single string
    assertEquals("Mill.", get(rows, 6, Field.AUTHORSHIP));
    // DwC atoms without a scientific name
    assertEquals("Abies alba", get(rows, 7, Field.SCIENTIFIC_NAME));
    assertEquals("Mill.", get(rows, 7, Field.AUTHORSHIP));
    // the separate authorship keeps its verbatim spelling, the atoms are normalised
    assertEquals("(Huter et al.) P. D. Sell & Whitehead", get(rows, 8, Field.AUTHORSHIP));
    assertEquals("P.D.Sell|Whitehead", get(rows, 8, Field.COMBINATION_AUTHORSHIP));
    // a different authorship in the name string is not flagged, the separate one wins...
    assertNull(get(rows, 9, Field.ISSUES));
    assertEquals("Mill.", get(rows, 9, Field.COMBINATION_AUTHORSHIP));
    assertEquals("L.", get(rows, 9, Field.BASIONYM_AUTHORSHIP));
    // ...and a partly different one is merged into the authorship of the name string by the parser
    assertNull(get(rows, 10, Field.ISSUES));
    assertEquals("Mill.", get(rows, 10, Field.AUTHORSHIP));
    assertEquals("Mill.", get(rows, 10, Field.COMBINATION_AUTHORSHIP));
    assertEquals("L.", get(rows, 10, Field.BASIONYM_AUTHORSHIP));
    // the same ColDP record in a dataset without a code and in one with a code
    assertNull(get(rows, 20, Field.CODE));
    assertEquals("BOTANICAL", get(rows, 19, Field.CODE));
    // author atoms win over the authorship string
    assertEquals("Mill., 1768", get(rows, 27, Field.AUTHORSHIP));
    assertEquals("(L. & Smith) Mill.", get(rows, 28, Field.AUTHORSHIP));
    // informal names keep the authorship in their phrase
    assertEquals("INFORMAL", get(rows, 13, Field.NAME_TYPE));
    assertEquals("Cantuaria sp. Forster, 1968", get(rows, 13, Field.LABEL));
    assertEquals("IDENTIFIER", get(rows, 14, Field.NAME_TYPE));
    assertEquals("VIRUS", get(rows, 15, Field.CODE));
    // the name status column of a NameUsage
    assertEquals("MANUSCRIPT", get(rows, 30, Field.NOM_STATUS));
    // author atoms with a year but no author
    assertEquals(InterpreterCorpusRunner.OK, get(rows, 34, Field.STATUS));
    assertEquals("(Hendel, 1902) 1902", get(rows, 34, Field.AUTHORSHIP));
    assertEquals("Hendel, 1902", get(rows, 34, Field.BASIONYM_AUTHORSHIP));
    // a hybrid uninomial atom, its code inferred from the authorship
    assertEquals("GENERIC", get(rows, 23, Field.NOTHO));
    assertEquals("BOTANICAL", get(rows, 23, Field.CODE));
    // a dagger as the only epithet leaves an indetermined genus
    assertEquals(InterpreterCorpusRunner.OK, get(rows, 24, Field.STATUS));
    assertEquals("true", get(rows, 24, Field.EXTINCT));
    assertEquals("INFORMAL", get(rows, 24, Field.NAME_TYPE));
    // hyphens only for epithets of several words
    assertEquals("MULTI_WORD_EPITHET", get(rows, 25, Field.ISSUES));
    assertNull(get(rows, 26, Field.ISSUES));
  }

  @Test
  public void runParserCorpus() throws Exception {
    File all = tmp.newFolder("all");
    String meta = new InterpreterCorpusRunner().run(PARSER_FIXTURE, all, 1, 42, 2, 0);
    assertTrue(meta, meta.contains("(PARSER_CORPUS)"));
    var rows = rows(all);
    assertEquals(8, rows.size());
    assertTrue(rows.values().stream().allMatch(r -> r[Field.STATUS.ordinal()].equals(InterpreterCorpusRunner.OK)));
    // missing trailing columns
    assertEquals("Méquignon, 1909", get(rows, 2, Field.AUTHORSHIP));
    assertEquals("GENUS", get(rows, 2, Field.RANK));
    // the COPY text escapes are undone: the source holds a literal backslash n
    assertTrue(get(rows, 4, Field.INPUT).contains("scientificName=Tachyusa (Tachyusa) smetanai Pas\\nik, 2006 |"));

    // a sample keeps the same rows in every run
    File s1 = tmp.newFolder("s1");
    File s2 = tmp.newFolder("s2");
    new InterpreterCorpusRunner().run(PARSER_FIXTURE, s1, 0.5, 7, 1, 0);
    new InterpreterCorpusRunner().run(PARSER_FIXTURE, s2, 0.5, 7, 3, 0);
    assertEquals(lines(s1), lines(s2));
    int sampled = rows(s1).size();
    assertTrue(sampled > 0 && sampled < 8);
    // the limit
    File l = tmp.newFolder("limit");
    new InterpreterCorpusRunner().run(PARSER_FIXTURE, l, 1, 42, 1, 3);
    assertEquals(3, rows(l).size());
  }

  @Test
  public void diff() throws Exception {
    File before = tmp.newFolder("before");
    new InterpreterCorpusRunner().run(EXPORT_FIXTURE, before, 1, 42, 2, 0);

    // the same run compared to itself
    String report = new InterpreterCorpusDiff(3).diff(before, before, tmp.newFolder("same"));
    assertTrue(report, report.contains("rows compared: 30 (33 records)"));
    assertTrue(report, report.contains("rows changed: 0 (0 records)"));

    // a copy with a few deliberate changes
    File after = tmp.newFolder("after");
    var rows = rows(before);
    rows.get(5L)[Field.AUTHORSHIP.ordinal()] = "Mill";         // punctuation
    rows.get(6L)[Field.AUTHORSHIP.ordinal()] = "Miller";       // text, the words differ
    rows.get(8L)[Field.AUTHORSHIP.ordinal()] = "(Huter & al.) P.D.Sell et Whitehead"; // separator
    rows.get(7L)[Field.RANK.ordinal()] = "GENUS";              // structure
    rows.get(11L)[Field.ISSUES.ordinal()] = "DOUBTFUL_NAME";   // issue added
    rows.get(24L)[Field.STATUS.ordinal()] = InterpreterCorpusRunner.ERROR;
    try (BufferedWriter w = CorpusIO.writer(new File(after, InterpreterCorpusRunner.OUTPUT))) {
      w.write(Field.header());
      w.write('\n');
      for (String[] r : rows.values()) {
        CorpusIO.writeRow(w, (Object[]) r);
      }
    }
    File out = tmp.newFolder("out");
    report = new InterpreterCorpusDiff(3).diff(before, after, out);
    assertTrue(report, report.contains("rows changed: 6 (8 records)"));
    // the rank, the authorship text, the separator and the status are significant, punctuation and issues are not
    assertTrue(report, report.contains("rows changed significantly: 4 (4 records)"));

    Map<String, String> changes = new HashMap<>();
    try (BufferedReader r = CorpusIO.reader(new File(out, InterpreterCorpusDiff.CHANGES))) {
      r.readLine();
      r.lines().map(l -> CorpusIO.split(l, 10)).forEach(c -> changes.put(c[0] + " " + c[4], c[5] + (c[6] == null ? "" : " " + c[6])));
    }
    assertEquals("PUNCTUATION", changes.get("5 AUTHORSHIP"));
    assertEquals("TEXT abbreviation", changes.get("6 AUTHORSHIP"));
    assertEquals("TEXT separator", changes.get("8 AUTHORSHIP"));
    assertEquals("CHANGED", changes.get("7 RANK"));
    assertEquals("ADDED DOUBTFUL_NAME", changes.get("11 ISSUES"));
    assertEquals("OK->ERROR", changes.get("24 STATUS"));
    assertEquals(6, changes.size());
  }
}
