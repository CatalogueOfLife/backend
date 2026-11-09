package life.catalogue.jobs;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.JobResult;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.NameUsageArchiver;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;
import life.catalogue.jobs.RedirectPairing.Outcome;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import javax.annotation.Nullable;

import org.apache.commons.io.IOUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import static org.junit.Assert.*;

/**
 * Uses the archive test data: project 3 with base releases 11 (deleted), 12 and 14 and extended release 13 built on 12.
 * Base release 14 has no extended release yet, so the live releases are 14 and 13.
 * The test adds its own archive records with LATIN29 ids, which the fixture ids A to E mostly are not.
 */
public class ArchiveRedirectJobIT {

  @ClassRule
  public static SqlSessionFactoryRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.archive();

  @Rule
  public final TemporaryFolder tmp = new TemporaryFolder();

  @Before
  public void init() throws Exception {
    JobResult.setDownloadConfigs(URI.create("https://download.checklistbank.org/job/"), tmp.newFolder("jobs"));
    // Abies alba, names index 1: BB is live, CC and FF are the same name, DD is not, GG already has a redirect
    archive("BB", 1, "Abies alba", "Mill.", null, 12, 13, 14);
    archive("CC", 1, "Abies alba", "Miller", null, 11, 12);
    archive("DD", 1, "Abies alba", "DC.", null, 12);
    archive("FF", 1, "Abies alba", null, null, 11);
    archive("GG", 1, "Abies alba", "Mill.", "ZZ", 12);
    // Pinus nigra, names index 4: HH is only in the last extended release, JJ in the newest base release
    archive("HH", 4, "Pinus nigra", "J.F.Arnold", null, 13);
    archive("JJ", 4, "Pinus nigra", "J.F.Arnold", null, 14);
    archive("KK", 4, "Pinus nigra", "J.F.Arnold", null, 12);
    // Picea abies, names index 2: two equal live ids
    archive("LL", 2, "Picea abies", "(L.) H.Karst.", null, 13);
    archive("MM", 2, "Picea abies", "(L.) H.Karst.", null, 13);
    archive("NN", 2, "Picea abies", "(L.) H.Karst.", null, 12);
    // Larix decidua, names index 3: no live id at all
    archive("PP", 3, "Larix decidua", "Mill.", null, 12);
  }

  static void archive(String id, int nidx, String name, @Nullable String authorship, @Nullable String supersededBy,
                      int... releaseKeys) throws SQLException {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true);
         var ps = session.getConnection().prepareStatement(
           "INSERT INTO name_usage_archive (id, n_id, dataset_key, n_rank, n_origin, n_type, n_scientific_name, n_authorship, "
             + "status, origin, release_keys, superseded_by) "
             + "VALUES (?, ?, 3, 'SPECIES', 'SOURCE', 'SCIENTIFIC', ?, ?, 'ACCEPTED', 'SOURCE', ?, ?)");
         var ps2 = session.getConnection().prepareStatement(
           "INSERT INTO name_usage_archive_match (dataset_key, usage_id, index_id) VALUES (3, ?, ?)")
    ) {
      ps.setString(1, id);
      ps.setString(2, "n" + id);
      ps.setString(3, name);
      ps.setString(4, authorship);
      Integer[] keys = new Integer[releaseKeys.length];
      for (int i = 0; i < releaseKeys.length; i++) {
        keys[i] = releaseKeys[i];
      }
      ps.setArray(5, session.getConnection().createArrayOf("int", keys));
      ps.setString(6, supersededBy);
      ps.execute();
      ps2.setString(1, id);
      ps2.setInt(2, nidx);
      ps2.execute();
    }
  }

  ArchiveRedirectJob job(Integer since, boolean dryRun) {
    return new ArchiveRedirectJob(Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), Datasets.COL, since, dryRun);
  }

  static ArchiveRedirectJob run(ArchiveRedirectJob job) {
    job.run();
    assertEquals("job failed: " + job.getError(), JobStatus.FINISHED, job.getStatus());
    return job;
  }

  static Map<String, String> redirects() {
    Map<String, String> redirects = new HashMap<>();
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var anum = session.getMapper(ArchivedNameUsageMapper.class);
      for (String id : List.of("BB", "CC", "DD", "FF", "GG", "HH", "JJ", "KK", "LL", "MM", "NN", "PP")) {
        redirects.put(id, anum.get(DSID.of(Datasets.COL, id)).getSupersededBy());
      }
    }
    return redirects;
  }

  /**
   * @return the report rows without the header
   */
  static List<String[]> report(ArchiveRedirectJob job) throws IOException {
    List<String[]> rows = new ArrayList<>();
    try (var zip = new ZipFile(job.getResult().getFile())) {
      var entry = zip.getEntry(ArchiveRedirectJob.REPORT_NAME);
      assertNotNull(entry);
      var lines = IOUtils.readLines(zip.getInputStream(entry), StandardCharsets.UTF_8);
      assertTrue(lines.get(0).startsWith("deadID\t"));
      for (var line : lines.subList(1, lines.size())) {
        rows.add(line.split("\t", -1));
      }
    }
    assertTrue(job.getResult().getSize() > 0);
    assertNotNull(job.getResult().getMd5());
    return rows;
  }

  static Map<String, String> outcomes(List<String[]> report) {
    Map<String, String> outcomes = new HashMap<>();
    for (var row : report) {
      outcomes.put(row[0], row[3] + " " + row[6]);
    }
    return outcomes;
  }

  @Test
  public void dryRunOnlyReports() throws Exception {
    var job = run(job(null, true));
    assertTrue(job.getStep(), job.getStep().startsWith("dry run, nothing written"));
    assertEquals(0, job.getUpdated());

    var outcomes = outcomes(report(job));
    assertEquals(Map.of(
      "CC", "BB REDIRECT",
      "DD", " CONTRADICTED",
      "FF", "BB REDIRECT",
      "KK", "JJ REDIRECT",
      "NN", "LL AMBIGUOUS"
    ), outcomes);

    var redirects = redirects();
    assertEquals("ZZ", redirects.remove("GG"));
    redirects.forEach((id, by) -> assertNull(id, by));
  }

  @Test
  public void backfill() throws Exception {
    var job = run(job(null, false));
    assertEquals(3, job.getUpdated());
    assertEquals(3, (int) job.getCounts().get(Outcome.REDIRECT));
    assertEquals(1, (int) job.getCounts().get(Outcome.AMBIGUOUS));
    assertEquals(1, (int) job.getCounts().get(Outcome.CONTRADICTED));
    assertTrue(job.getStep(), job.getStep().startsWith("3 redirects written"));

    var redirects = redirects();
    assertEquals("BB", redirects.get("CC"));
    assertEquals("BB", redirects.get("FF"));
    // the base release id wins the tie over the extended release only id, which is live nevertheless
    assertEquals("JJ", redirects.get("KK"));
    // never overwritten
    assertEquals("ZZ", redirects.get("GG"));
    for (String id : List.of("BB", "DD", "HH", "JJ", "LL", "MM", "NN", "PP")) {
      assertNull(id, redirects.get(id));
    }

    // a second run finds nothing left to do
    var again = run(job(null, false));
    assertEquals(0, again.getUpdated());
    assertEquals(Map.of("DD", " CONTRADICTED", "NN", "LL AMBIGUOUS"), outcomes(report(again)));

    // publishing keeps the redirects of ids no live release carries
    new NameUsageArchiver(SqlSessionFactoryRule.getSqlSessionFactory()).archiveProject(
      new NameUsageArchiver(SqlSessionFactoryRule.getSqlSessionFactory()).ranking(Datasets.COL), false, false);
    assertEquals(redirects, redirects());
  }

  @Test
  public void since() throws Exception {
    // FF was only in release 11, which is older than 12
    var job = run(job(12, false));
    assertEquals(2, job.getUpdated());
    var redirects = redirects();
    assertEquals("BB", redirects.get("CC"));
    assertEquals("JJ", redirects.get("KK"));
    assertNull(redirects.get("FF"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void sinceMustBeAReleaseOfTheProject() {
    job(999, false);
  }

  @Test
  public void ignoredReleasesGetNoRedirects() throws Exception {
    // with release 12 ignored only CC, which 11 carried as well, and FF are left
    var job = run(new ArchiveRedirectJob(Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(),
      p -> new IntOpenHashSet(new int[]{12}), Datasets.COL, null, false));
    assertEquals(2, job.getUpdated());
    var redirects = redirects();
    assertEquals("BB", redirects.get("CC"));
    assertEquals("BB", redirects.get("FF"));
    assertNull(redirects.get("KK"));
    assertNull(redirects.get("DD"));
  }

  @Test
  public void dryRunIsNoDuplicateOfARealRun() {
    assertTrue(job(null, true).isDuplicate(job(null, true)));
    assertFalse(job(null, true).isDuplicate(job(null, false)));
    assertFalse(job(12, false).isDuplicate(job(null, false)));
  }
}
