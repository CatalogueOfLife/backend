package life.catalogue.matching.person;

import life.catalogue.common.io.Resources;
import life.catalogue.matching.authorship.corpus.AuthorCorpusReport;

import java.io.File;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PersonCorpusReportTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  /** both policies on the relatives fixture: the person sections, and the flips against the string verdicts */
  @Test
  public void reportsBothPolicies() throws Exception {
    File pairs = Resources.toFile(RelativesFixtureTest.FIXTURE);
    File strings = tmp.newFolder("strings");
    AuthorCorpusReport.report(pairs, strings, true);
    File out = tmp.newFolder("persons");
    PersonCorpusReport.report(pairs, new File(strings, AuthorCorpusReport.VERDICTS), out, PersonResolver.Margins.DEFAULT);
    for (String policy : new String[]{"unknown", "different"}) {
      String r = Files.readString(new File(out, policy + "/" + AuthorCorpusReport.REPORT).toPath());
      assertTrue(r, r.contains("comparator: persons, relatives " + policy.toUpperCase()));
      assertTrue(r, r.contains("## What decided the verdicts"));
      assertTrue(r, r.contains("## Citations resolved"));
      assertTrue(r, r.contains("## Citations no person resolves"));
      assertTrue(r, r.contains("## Pairs the relatives policy decided"));
      assertTrue(r, r.contains("Hook. | Hook.f."));
      assertTrue(r, r.contains("## Run"));
      assertTrue(new File(out, policy + "/diff.txt").isFile());
    }
  }

  /** unset margins keep their default, which Task 10 may move */
  @Test
  public void margins() {
    var d = PersonResolver.Margins.DEFAULT;
    assertEquals(new PersonResolver.Margins(d.minAge(), 50, d.activeSlack()),
      PersonCorpusReport.margins(new String[]{"p", "v", "o", "--posthumous", "50"}, 3));
    assertEquals(new PersonResolver.Margins(12, d.posthumous(), 5),
      PersonCorpusReport.margins(new String[]{"p", "v", "o", "--min-age", "12", "--active-slack", "5"}, 3));
  }
}
