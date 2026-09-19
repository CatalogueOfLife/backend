package life.catalogue.matching.authorship.corpus;

import life.catalogue.common.io.Resources;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.authorship.AuthorComparator;

import java.io.File;
import java.nio.file.Files;
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
    AuthorCorpusReport.report(Resources.toFile(CorpusEvaluatorTest.KNOWN_MISJUDGEMENTS), dir,
      new AuthorComparator(AuthorshipNormalizer.INSTANCE));
    report = Files.readString(new File(dir, AuthorCorpusReport.REPORT).toPath());
    verdicts = AuthorVerdictDiff.read(new File(dir, AuthorCorpusReport.VERDICTS));
  }

  private String section(String title) {
    int start = report.indexOf("## " + title);
    assertTrue("section missing: " + title, start >= 0);
    int end = report.indexOf("\n## ", start + 1);
    return report.substring(start, end < 0 ? report.length() : end);
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

  /** with the same year on both sides it takes the authors to keep Sw. and Swainson apart */
  @Test
  public void tellsAuthorFromYearCausedMisses() {
    String s = section("Same act judged DIFFERENT by its authors");
    assertTrue(s, s.contains("Swainson"));
    assertTrue(s, s.contains("Wang Wen Tsai"));
    assertFalse(section("Same act judged DIFFERENT by its years only").contains("Swainson"));
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
}
