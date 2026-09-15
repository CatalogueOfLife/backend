package life.catalogue.dao;

import life.catalogue.api.model.ArchivedNameUsage;
import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;
import life.catalogue.db.mapper.ArchivedNameUsageMatchMapper;
import life.catalogue.db.mapper.DatasetMapper.ArchivableRelease;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The archive fixture ranks 14, 12, 13 - base release 12 above its extended release 13 - and 11 is deleted:
 * <ul>
 *   <li>A Abies alba: in 11, 12, 13, 14, authorship corrected to Miller in 14</li>
 *   <li>B Cedrus libani: only in the deleted 11</li>
 *   <li>C Picea abies: in 12 and 13, provisionally accepted in 13 only, dropped from 14</li>
 *   <li>D Larix decidua: only in the extended release 13, missing from the archive</li>
 *   <li>E Pinus nigra: in 12 and 13, renamed to Pinus mugo in 14</li>
 * </ul>
 */
public class NameUsageArchiverIT {
  static final int PROJECT = Datasets.COL;

  @ClassRule
  public static SqlSessionFactoryRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.archive();

  SqlSessionFactory factory;
  NameUsageArchiver archiver;

  @Before
  public void init() {
    factory = SqlSessionFactoryRule.getSqlSessionFactory();
    archiver = new NameUsageArchiver(factory);
    DatasetInfoCache.CACHE.clear();
    // the matches of the old archive, following the names its rows hold
    try (SqlSession session = factory.openSession(true)) {
      var amm = session.getMapper(ArchivedNameUsageMatchMapper.class);
      amm.persist(DSID.of(PROJECT, "A"), null, 1);
      amm.persist(DSID.of(PROJECT, "B"), null, 6);
      amm.persist(DSID.of(PROJECT, "C"), null, 2);
      amm.persist(DSID.of(PROJECT, "E"), null, 4);
    }
  }

  ArchivedNameUsage get(String id) {
    try (SqlSession session = factory.openSession(true)) {
      return session.getMapper(ArchivedNameUsageMapper.class).get(DSID.of(PROJECT, id));
    }
  }

  Integer nidx(String id) {
    try (SqlSession session = factory.openSession(true)) {
      var m = session.getMapper(ArchivedNameUsageMatchMapper.class).get(DSID.of(PROJECT, id));
      return m == null ? null : m.getNidx();
    }
  }

  String sql(String query) throws Exception {
    try (SqlSession session = factory.openSession(true);
         Statement st = session.getConnection().createStatement();
         ResultSet rs = st.executeQuery(query)
    ) {
      return rs.next() ? rs.getString(1) : null;
    }
  }

  void assertNewestGenerations() {
    var a = get("A");
    assertEquals("Miller", a.getName().getAuthorship());
    assertArrayEquals(new int[]{11, 12, 13, 14}, a.getReleaseKeys());
    assertEquals(Integer.valueOf(1), nidx("A"));

    var b = get("B");
    assertEquals("A.Rich.", b.getName().getAuthorship());
    assertArrayEquals(new int[]{11}, b.getReleaseKeys());
    assertEquals(Integer.valueOf(6), nidx("B"));

    // base release 12 outranks its extended release 13
    var c = get("C");
    assertEquals(TaxonomicStatus.ACCEPTED, c.getStatus());
    assertArrayEquals(new int[]{12, 13}, c.getReleaseKeys());

    var d = get("D");
    assertEquals("Larix decidua", d.getName().getScientificName());
    assertArrayEquals(new int[]{13}, d.getReleaseKeys());
    assertEquals(Integer.valueOf(3), nidx("D"));

    var e = get("E");
    assertEquals("Pinus mugo", e.getName().getScientificName());
    assertArrayEquals(new int[]{12, 13, 14}, e.getReleaseKeys());
    assertEquals(Integer.valueOf(5), nidx("E"));
  }

  @Test
  public void ranking() {
    var ranking = archiver.ranking(PROJECT);
    assertEquals(List.of(14, 12, 13), ranking.archivable().stream().map(ArchivableRelease::getKey).toList());
    assertEquals(Integer.valueOf(12), ranking.baseRelease(13));
    assertFalse(ranking.isFallbackBase(13));
    assertFalse(ranking.isArchivable(11));
  }

  @Test
  public void archiveProject() {
    assertEquals(List.of(13, 14), archiver.unarchivedReleases(PROJECT));

    var stats = archiver.archiveProject(archiver.ranking(PROJECT), true, false);
    assertEquals(3, stats.releases);
    assertEquals(1, stats.inserted);
    assertNewestGenerations();
    assertTrue(archiver.unarchivedReleases(PROJECT).isEmpty());

    var again = archiver.archiveProject(archiver.ranking(PROJECT), true, false);
    assertTrue("a rerun wrote " + again, again.isUnchanged());
  }

  @Test
  public void publishedInOrder() {
    for (int key : new int[]{12, 13, 14}) {
      archiver.archiveRelease(key);
    }
    assertNewestGenerations();
  }

  @Test
  public void extendedReleasePublishedAfterTheNextBaseRelease() {
    for (int key : new int[]{12, 14, 13}) {
      archiver.archiveRelease(key);
    }
    assertNewestGenerations();
  }

  @Test
  public void supersededOnlyFromTheHighestRankedRelease() throws Exception {
    try (SqlSession session = factory.openSession(true)) {
      var anum = session.getMapper(ArchivedNameUsageMapper.class);
      anum.addSuperseded(14, "C", "A"); // the newest release dropped C in favour of A
      anum.addSuperseded(12, "B", "A"); // an older release's staging is stale
    }
    archiver.archiveProject(archiver.ranking(PROJECT), true, false);
    assertEquals("A", sql("SELECT superseded_by FROM name_usage_archive WHERE dataset_key=3 AND id='C'"));
    assertNull(sql("SELECT superseded_by FROM name_usage_archive WHERE dataset_key=3 AND id='B'"));
    assertEquals("0", sql("SELECT count(*) FROM usage_id_superseded"));
  }

  @Test
  public void dryRunWritesNothing() {
    var stats = archiver.archiveProject(archiver.ranking(PROJECT), true, true);
    assertEquals(1, stats.inserted);
    assertEquals(1, stats.renamed);
    assertTrue(stats.rewritten > 0);
    assertNull(get("D"));
    assertEquals("Mill.", get("A").getName().getAuthorship());
    assertEquals(List.of(13, 14), archiver.unarchivedReleases(PROJECT));
  }
}
