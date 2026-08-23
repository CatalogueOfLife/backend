package life.catalogue.db.mapper;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SectorImport;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.JobStatus;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.*;
import static life.catalogue.api.vocab.Datasets.COL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SectorImportMapperTest extends MapperTestBase<SectorImportMapper> {
  static int attempts = 1;
  
  Sector s;
  Sector s2;

  @Before
  public void prepare() {
    attempts = 1;
    s = new Sector();
    s.setDatasetKey(COL);
    s.setSubjectDatasetKey(DATASET11.getKey());
    s.setMode(Sector.Mode.ATTACH);
    s.setSubject(newSimpleName());
    s.setTarget(newSimpleName());
    s.setNote(RandomUtils.randomUnicodeString(1024));
    s.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    s.setModifiedBy(TestEntityGenerator.USER_EDITOR.getKey());
    
    mapper(SectorMapper.class).create(s);
  
    s2 = new Sector();
    s2.setDatasetKey(COL);
    s2.setSubjectDatasetKey(DATASET12.getKey());
    s2.setMode(Sector.Mode.ATTACH);
    s2.setSubject(newSimpleName());
    s2.setTarget(newSimpleName());
    s2.setNote(RandomUtils.randomUnicodeString(1024));
    s2.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    s2.setModifiedBy(TestEntityGenerator.USER_EDITOR.getKey());
  
    mapper(SectorMapper.class).create(s2);
  }
  
  public static SectorImport create(JobStatus status, Sector s) {
    SectorImport d = new SectorImport();
    DatasetImportMapperTest.fill(d, status);
    d.setJob("SectorImportTest");
    d.setDatasetKey(s.getDatasetKey());
    d.setSectorKey(s.getId());
    d.setAttempt(attempts++);
    d.setDatasetAttempt(73);
    d.addWarning("warning 1");
    d.addWarning("warning 2");
    return d;
  }

  public SectorImportMapperTest() {
    super(SectorImportMapper.class);
  }

  /**
   * Persists the sector import and a matching job record so status, step, job and error can be joined.
   */
  private SectorImport createBoth(JobStatus status, Sector sec) {
    SectorImport d = create(status, sec);
    createJob(session(), d);
    mapper().create(d);
    return d;
  }
  
  
  @Test
  public void roundtrip() throws Exception {
    SectorImport d1 = createBoth(JobStatus.FINISHED, s);
    commit();
    assertEquals(1, d1.getAttempt());
  
    SectorImport d2 = mapper().get(d1.getSectorDSID(), d1.getAttempt());
    assertEquals(d1, d2);
  }
  
  @Test
  public void listCount() throws Exception {
    createBoth(JobStatus.FAILED, s);
    createBoth(JobStatus.FINISHED, s);
    createBoth(JobStatus.WAITING, s);
    createBoth(JobStatus.FINISHED, s);
    createBoth(JobStatus.CANCELED, s);
    createBoth(JobStatus.RUNNING, s2);
    createBoth(JobStatus.FINISHED, s2);
    
    assertEquals(7, mapper().count(null, null, null, null, null));
    assertEquals(7, mapper().count(null, null,null, new ArrayList<>(), null));
    assertEquals(1, mapper().count(null, null,null, List.of(JobStatus.FAILED), null));
    assertEquals(3, mapper().count(null, null,null, List.of(JobStatus.FINISHED), null));
    assertEquals(2, mapper().count(null, null,null, List.of(JobStatus.RUNNING, JobStatus.WAITING), null));

    assertEquals(2, mapper().list(null, null,null, List.of(JobStatus.RUNNING, JobStatus.WAITING), null,null, new Page()).size());
    assertEquals(0, mapper().list(null, 100,null, List.of(JobStatus.RUNNING, JobStatus.WAITING), null, false, new Page()).size());
    assertEquals(0, mapper().list(null, null,null, List.of(JobStatus.RUNNING, JobStatus.WAITING), null, true, new Page()).size());

    assertEquals(5, mapper().count(s.getId(), null, null, null, null));
    assertEquals(5, mapper().count(s.getId(), null, s.getSubjectDatasetKey(), null, null));
    assertEquals(5, mapper().count(s.getId(), COL, s.getSubjectDatasetKey(), null, null));
    assertEquals(0, mapper().count(s2.getId(), COL, s.getSubjectDatasetKey(), null, null));
    assertEquals(5, mapper().count(null, COL, s.getSubjectDatasetKey(), null, null));
    assertEquals(2, mapper().count(null, COL, s2.getSubjectDatasetKey(), null, null));
    assertEquals(2, mapper().count(s2.getId(), COL, null, null, null));
    
    assertEquals(0, mapper().count(99999, null, null, null, null));
    assertEquals(0, mapper().count(99999, null, 789, null, null));
    assertEquals(0, mapper().count(null, 456789876, null, null, null));
    
  }

  @Test
  public void deleteByDataset() throws Exception {
    mapper().deleteByDataset(Datasets.COL);
  }

  @Test
  public void counts() throws Exception {
    assertEquals((Integer) 0, mapper().countBareName(DATASET11.getKey(), 1));
    assertEquals((Integer) 0, mapper().countDistribution(DATASET11.getKey(), 1));
    assertEquals((Integer) 0, mapper().countMedia(DATASET11.getKey(), 1));
    assertEquals((Integer) 0, mapper().countName(DATASET11.getKey(), 1));
    assertEquals((Integer) 0, mapper().countNameMatches(DATASET11.getKey(), 1));
    assertEquals((Integer) 0, mapper().countReference(DATASET11.getKey(), 1));
    assertEquals((Integer) 0, mapper().countSynonym(DATASET11.getKey(), 1));
    assertEquals((Integer) 0, mapper().countTaxon(DATASET11.getKey(), 1));
    assertEquals((Integer) 0, mapper().countTreatment(DATASET11.getKey(), 1));
    assertEquals((Integer) 0, mapper().countTypeMaterial(DATASET11.getKey(), 1));
    assertEquals((Integer) 0, mapper().countVernacular(DATASET11.getKey(), 1));
  }

  @Test
  public void countByMaps() throws Exception {
    assertEquals(0, mapper().countDistributionsByGazetteer(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countExtinctTaxaByRank(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countIssues(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countMediaByType(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countNameRelationsByType(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countUsagesByOrigin(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countNamesByRank(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countNamesByStatus(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countNamesByType(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countSpeciesInteractionsByType(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countSynonymsByRank(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countTaxaByRank(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countTaxonConceptRelationsByType(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countTypeMaterialByStatus(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countUsagesByStatus(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countVernacularsByLanguage(DATASET11.getKey(), 1).size());
  }

  @Test
  public void attemptsAndBatchDelete() throws Exception {
    // sector ids for COL are recycled to the same small numbers by every test in this class (their per-dataset
    // sequence gets dropped and reset on each @Rule teardown/setup), while sector_import itself is never truncated
    // between tests. roundtrip() commits one row at (COL, s.id, attempt=1), which otherwise collides with the
    // first insert below whenever JUnit happens to run this test after it.
    mapper().deleteByDataset(COL);

    final int attemptA = 10;
    final int attemptB = 20;

    // Both sectors get a row at attempt A and a row at attempt B. The delete batch is the crossed
    // pair (s, A) and (s2, B), so (s, B) and (s2, A) must survive. This is the only shape that can
    // tell a true row-value tuple match "(sector_key, attempt) IN ((?,?),(?,?))" apart from a buggy
    // independent-filter "sector_key IN (...) AND attempt IN (...)": the buggy form collapses to
    // sector_key IN (s, s2) AND attempt IN (A, B), a cross product that also matches (s, B) and
    // (s2, A) even though neither pair is in the batch.
    SectorImport siSA = create(JobStatus.FINISHED, s);
    siSA.setAttempt(attemptA);
    mapper().create(siSA);
    SectorImport siSB = create(JobStatus.FINISHED, s);
    siSB.setAttempt(attemptB);
    mapper().create(siSB);
    SectorImport si2A = create(JobStatus.FINISHED, s2);
    si2A.setAttempt(attemptA);
    mapper().create(si2A);
    SectorImport si2B = create(JobStatus.FINISHED, s2);
    si2B.setAttempt(attemptB);
    mapper().create(si2B);
    commit();

    List<SectorImportMapper.AttemptInfo> all = mapper().listAttempts(COL, null, null, 1000);
    assertEquals(4, all.size());
    // the projection must carry what the retention rule needs
    for (var a : all) {
      assertNotNull(a.getStarted());
      assertTrue(a.getSectorKey() == s.getId() || a.getSectorKey() == s2.getId());
    }

    // delete batch = [(s, A), (s2, B)] - deliberately crossed with (s, B) and (s2, A)
    var doomed = all.stream()
        .filter(a -> (a.getSectorKey() == s.getId() && a.getAttempt() == attemptA)
            || (a.getSectorKey() == s2.getId() && a.getAttempt() == attemptB))
        .toList();
    assertEquals(2, doomed.size());
    assertEquals(2, mapper().deleteAttempts(COL, doomed));
    commit();

    List<SectorImportMapper.AttemptInfo> left = mapper().listAttempts(COL, null, null, 1000);
    assertEquals(2, left.size());
    assertTrue(left.stream().anyMatch(a -> a.getSectorKey() == s.getId() && a.getAttempt() == attemptB));
    assertTrue(left.stream().anyMatch(a -> a.getSectorKey() == s2.getId() && a.getAttempt() == attemptA));

    // (s, B) and (s2, A) survive
    assertNotNull(mapper().get(siSB.getSectorDSID(), attemptB));
    assertNotNull(mapper().get(si2A.getSectorDSID(), attemptA));
    // (s, A) and (s2, B) are gone
    assertNull(mapper().get(siSA.getSectorDSID(), attemptA));
    assertNull(mapper().get(si2B.getSectorDSID(), attemptB));
  }

  @Test
  public void deleteAttemptsEmptyList() throws Exception {
    mapper().deleteByDataset(COL);

    SectorImport si1 = create(JobStatus.FINISHED, s);
    mapper().create(si1);
    commit();

    // must delete nothing and must not throw - an empty IN() list is a Postgres syntax error
    assertEquals(0, mapper().deleteAttempts(COL, List.of()));
    commit();

    List<SectorImportMapper.AttemptInfo> left = mapper().listAttempts(COL, null, null, 1000);
    assertEquals(1, left.size());
  }
}