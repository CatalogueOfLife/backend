package life.catalogue.matching.authorship.corpus;

import life.catalogue.common.io.Resources;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.authorship.AuthorComparator;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuthorCorpusReportTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private String report;
  private List<AuthorVerdictDiff.VerdictRow> verdicts;

  @Before
  public void report() throws Exception {
    File dir = tmp.newFolder("report");
    AuthorCorpusReport.report(Resources.toFile(CorpusEvaluatorTest.KNOWN_MISJUDGEMENTS), dir, true);
    report = Files.readString(new File(dir, AuthorCorpusReport.REPORT).toPath());
    verdicts = AuthorVerdictDiff.read(new File(dir, AuthorCorpusReport.VERDICTS));
  }

  private String section(String title) {
    int start = report.indexOf("## " + title);
    assertTrue("section missing: " + title, start >= 0);
    int end = report.indexOf("\n## ", start + 1);
    return report.substring(start, end < 0 ? report.length() : end);
  }

  /**
   * A corpus is only comparable to one parsed by the same parser. The report parses nothing itself, so it names the
   * parser the pairs came with, never the one on its own classpath.
   */
  @Test
  public void headerNamesTheCorpusParser() throws Exception {
    assertTrue(report, report.lines().limit(8).anyMatch(l -> l.equals("corpus parsed by: the imports, not re-parsed")));

    File pairs = new File(tmp.getRoot(), "pairs.tsv");
    Files.copy(Resources.toFile(CorpusEvaluatorTest.KNOWN_MISJUDGEMENTS).toPath(), pairs.toPath());
    CorpusIO.writeParser(pairs, "0.2.2-SNAPSHOT (test)");
    File dir = tmp.newFolder("reparsed");
    AuthorCorpusReport.report(pairs, dir, true);
    String reparsed = Files.readString(new File(dir, AuthorCorpusReport.REPORT).toPath());
    assertTrue(reparsed, reparsed.lines().limit(8).anyMatch(l -> l.equals("corpus parsed by: 0.2.2-SNAPSHOT (test)")));
  }

  @Test
  public void oneVerdictPerPair() {
    assertEquals(9, verdicts.size());
    var martin = verdicts.stream().filter(v -> v.keyA().equals(";;;martin")).findFirst().orElseThrow();
    assertEquals("DIFF", martin.label());
    assertEquals("EQUAL", martin.verdict());
    assertEquals("Martin", martin.authorshipA());
    assertEquals("Martius", martin.authorshipB());
  }

  @Test
  public void listsWhatIsTakenForOne() {
    String s = section("Different names judged EQUAL");
    assertTrue(s, s.contains("Martin") && s.contains("Martius"));
    assertTrue(s, s.contains("J.E. Gray"));
    assertFalse(s, s.contains("Swainson"));
  }

  /** neither pair has a year, so it takes the authors to keep them apart */
  @Test
  public void tellsAuthorFromYearCausedMisses() {
    String s = section("Same act judged DIFFERENT by its authors");
    assertTrue(s, s.contains("Wang Wen Tsai"));
    assertTrue(s, s.contains("G\u00f3mez-Campo"));
    assertFalse(section("Same act judged DIFFERENT by its years only").contains("Wang Wen Tsai"));
    // judged EQUAL since the code of the names selects the author map
    assertFalse(s, s.contains("Swainson"));
  }

  /**
   * What is too dubious to measure is still worth a look: judged EQUAL it is most likely a second record of one
   * name in its dataset, judged DIFFERENT it may be the comparator missing just that.
   */
  @Test
  public void keepsDubiousPairsVisible() {
    String s = section("Dubious pairs judged EQUAL");
    assertTrue(s, s.contains("Pohl ex Benth."));
    assertFalse(section("Dubious pairs judged DIFFERENT").contains("Pohl"));
    assertTrue(section("Dubious pairs by what makes them dubious").contains("INTRA_NOYEAR"));
    assertFalse(section("Different names judged EQUAL").contains("Pohl"));
  }

  /**
   * What the author map is worth shows in a second report without it, diffed against the first. The map makes
   * the brothers Gray one author by expanding "G.R. Gray" to a full name that has no initials left to differ.
   */
  @Test
  public void withoutTheAuthorMap() throws Exception {
    assertTrue(section("Different names judged EQUAL").contains("J.E. Gray"));
    assertTrue(report.substring(0, report.indexOf("## ")).contains("author map: 60,"));

    File dir = tmp.newFolder("nomap");
    AuthorCorpusReport.report(Resources.toFile(CorpusEvaluatorTest.KNOWN_MISJUDGEMENTS), dir, false);
    report = Files.readString(new File(dir, AuthorCorpusReport.REPORT).toPath());
    assertFalse(section("Different names judged EQUAL").contains("J.E. Gray"));
    assertTrue(report.substring(0, report.indexOf("## ")).contains("author map: none"));
    // without a map there is nothing a pair could be missing in it, so the list is left out
    assertFalse(report.contains("Alias candidates"));
  }

  @Test
  public void hasMatricesForEveryScope() {
    String s = section("Confusion matrix");
    for (String scope : CorpusEvaluator.SCOPES) {
      assertTrue(scope, s.contains(scope));
    }
    assertTrue(report.contains("## Confusion matrix without years"));
  }

  @Test
  public void saysWhatItMeasured() {
    String s = report.substring(0, report.indexOf("## "));
    assertTrue(s, s.contains("pairs: 9"));
    assertTrue(s, s.contains("author map"));
  }

  /** a report on another comparator: its own header lines and sections, every pair shown to its extension, no alias list */
  @Test
  public void reportOnAnotherComparator() throws Exception {
    File dir = tmp.newFolder("other");
    List<String> seen = new ArrayList<>();
    var ext = new AuthorCorpusReport.Extension() {
      @Override
      public void before(AuthorPair p) {
        seen.add("before " + p.keyA());
      }

      @Override
      public void after(CorpusEvaluator.Verdict v) {
        seen.add("after " + v.pair().keyA());
      }

      @Override
      public void render(StringBuilder sb) {
        sb.append("\n## Extension\nrendered\n");
      }
    };
    AuthorCorpusReport.report(Resources.toFile(CorpusEvaluatorTest.KNOWN_MISJUDGEMENTS), dir,
      new AuthorComparator(AuthorshipNormalizer.INSTANCE), List.of("comparator: test"), null, ext);
    String r = Files.readString(new File(dir, AuthorCorpusReport.REPORT).toPath());
    assertTrue(r, r.lines().limit(8).anyMatch(l -> l.equals("comparator: test")));
    assertFalse(r, r.contains("Alias candidates"));
    assertTrue(r, r.contains("## Extension\nrendered"));
    assertEquals(18, seen.size());
    assertEquals("before ;;;martin", seen.get(0));
    assertEquals("after ;;;martin", seen.get(1));
  }
}
