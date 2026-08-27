package life.catalogue.db.mapper;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SectorImport;
import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.api.vocab.*;
import life.catalogue.junit.MybatisTestUtils;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.*;

public class SectorMapperTest extends BaseDecisionMapperTest<Sector, SectorSearchRequest, SectorMapper> {
  
  private static final int targetDatasetKey = Datasets.COL;
  private static final int subjectDatasetKey = DATASET11.getKey();
  private Sector s1;
  private Sector s2;

  public SectorMapperTest() {
    super(SectorMapper.class);
  }


  private void add2Sectors() {
    // create a few draft taxa to attach sectors to
    MybatisTestUtils.populateDraftTree(session());

    s1 = createTestEntity(targetDatasetKey);
    s1.getSubject().setId(TestEntityGenerator.TAXON1.getId());
    s1.getTarget().setId("t4");
    mapper().create(s1);

    s2 = createTestEntity(targetDatasetKey);
    mapper().create(s2);
    commit();
  }

  private void addImport(Sector s, JobStatus status, LocalDateTime finished) {
    SectorImport si = SectorImportMapperTest.create(status, s);
    si.setFinished(finished);
    si.setCreatedBy(Users.TESTER);
    MapperTestBase.createJob(session(), si);
    mapper(SectorImportMapper.class).create(si);

    if (status == JobStatus.FINISHED) {
      mapper().updateLastSync(s, si.getAttempt());
    }
  }

  @Test
  public void getBySubject() {
    add2Sectors();
    assertNotNull(mapper().getBySubject(targetDatasetKey, DSID.of(subjectDatasetKey, TestEntityGenerator.TAXON1.getId())));
    assertNull(mapper().getBySubject(targetDatasetKey, DSID.of(subjectDatasetKey +1, TestEntityGenerator.TAXON1.getId())));
    assertNull(mapper().getBySubject(targetDatasetKey, DSID.of(subjectDatasetKey, TestEntityGenerator.TAXON1.getId()+"dfrtgzh")));
  }

  @Test
  public void exists() {
    add2Sectors();
    assertTrue(mapper().exists(s1));
    assertTrue(mapper().exists(s2));
    var dsid = DSID.copy(s1);
    dsid.setId(56789);
    assertFalse(mapper().exists(dsid));
  }

  @Test
  public void missingSubject() {
    // create a few draft taxa to attach sectors to
    MybatisTestUtils.populateDraftTree(session());

    s1 = createTestEntity(targetDatasetKey);
    s1.setMode(Sector.Mode.MERGE);
    s1.setSubject(null);
    s1.setTarget(null);
    mapper().create(s1);

    var s2 = mapper().get(s1);
    assertNotNull(s2);
    System.out.println(s2.getSubject());
    assertNull(s2.getSubject());
    assertNull(s2.getTarget());
  }
  
  @Test
  public void listByTarget() {
    add2Sectors();
    assertEquals(1, mapper().listByTarget(DSID.of(targetDatasetKey,"t4")).size());
    assertEquals(0, mapper().listByTarget(DSID.of(targetDatasetKey,"t32134")).size());
  }

  @Test
  public void listWrongSubject() {
    add2Sectors();
    SectorSearchRequest req = SectorSearchRequest.byProject(targetDatasetKey);
    req.setWrongSubject(true);
    var res = mapper().search(req, new Page());
    assertEquals(1, res.size());
    assertEquals(TestEntityGenerator.TAXON1.getId(), res.get(0).getSubjectID());

    req.setWrongSubject(false);
    res = mapper().search(req, new Page());
    assertEquals(2, res.size());
  }

  @Test
  public void list() {
    add2Sectors();
    assertEquals(2, mapper().listByDataset(targetDatasetKey,subjectDatasetKey, null).size());
    assertEquals(0, mapper().listByDataset(targetDatasetKey,-432, null).size());
    assertEquals(2, mapper().listByDataset(targetDatasetKey,subjectDatasetKey, Sector.Mode.ATTACH).size());
    assertEquals(0, mapper().listByDataset(targetDatasetKey,subjectDatasetKey, Sector.Mode.MERGE).size());
    // no results, but make sure sql works
    assertEquals(0, mapper().listByDatasetPublisher(targetDatasetKey,UUID.randomUUID()).size());
  }

  @Test
  public void listOutdatedSectors() {
    add2Sectors();
    assertEquals(0, mapper().listOutdatedSectors(targetDatasetKey,null).size());
    assertEquals(0, mapper().listOutdatedSectors(targetDatasetKey, List.of()).size());
    assertEquals(0, mapper().listOutdatedSectors(targetDatasetKey, List.of(1,2,3)).size());
  }

  @Test
  public void broken() {
    add2Sectors();
  
    SectorSearchRequest req = SectorSearchRequest.byDataset(targetDatasetKey,subjectDatasetKey);
    req.setBroken(true);
    assertEquals(1, mapper().search(req, new Page()).size());
  
    req.setSubjectDatasetKey(543432);
    assertEquals(0, mapper().search(req, new Page()).size());
  }

  @Test
  public void search() {
    add2Sectors();

    addImport(s1, JobStatus.FINISHED, LocalDateTime.of(2019, 12, 24, 12, 0, 0));
    addImport(s1, JobStatus.FINISHED, LocalDateTime.of(2020, 1, 10, 12, 0, 0));
    addImport(s1, JobStatus.FAILED, LocalDateTime.of(2020, 2, 11, 12, 0, 0));

    addImport(s2, JobStatus.FAILED, LocalDateTime.of(2018, 1, 10, 12, 0, 0));
    addImport(s2, JobStatus.FINISHED, LocalDateTime.of(2020, 1, 21, 12, 0, 0));
    commit();

    SectorSearchRequest req = SectorSearchRequest.byProject(targetDatasetKey);
    req.setLastSync(LocalDate.of(2020, 1, 1));
    assertEquals(0, mapper().search(req, new Page()).size());

    req.setLastSync(LocalDate.of(2020, 1, 15));
    assertEquals(1, mapper().search(req, new Page()).size());

    req.setLastSync(LocalDate.of(2020, 2, 1));
    assertEquals(2, mapper().search(req, new Page()).size());

    req.setLastSync(LocalDate.of(2022, 3, 1));
    assertEquals(2, mapper().search(req, new Page()).size());

    req.setLastSync(LocalDate.of(2019, 1, 1));
    assertEquals(0, mapper().search(req, new Page()).size());

    req = SectorSearchRequest.byProject(targetDatasetKey);
    req.setWithoutData(true);
    assertEquals(2, mapper().search(req, new Page()).size());

    req = SectorSearchRequest.byProject(targetDatasetKey);
    req.setMinSize(10);
    assertEquals(2, mapper().search(req, new Page()).size());

    req.setMode(Set.of(Sector.Mode.ATTACH));
    assertEquals(2, mapper().search(req, new Page()).size());

    req.setMode(Set.of(Sector.Mode.ATTACH, Sector.Mode.MERGE));
    assertEquals(2, mapper().search(req, new Page()).size());

    req.setMode(Set.of(Sector.Mode.UNION, Sector.Mode.MERGE));
    assertEquals(0, mapper().search(req, new Page()).size());

    req.setMode(null);
    req.setPublisherKey(UUID.randomUUID());
    assertEquals(0, mapper().search(req, new Page()).size());
  }

  @Test
  public void listTargetDatasetKeys() {
    assertEquals(0, mapper().listTargetDatasetKeys().size());
    add2Sectors();
    assertEquals(1, mapper().listTargetDatasetKeys().size());
  }
  
  @Override
  Sector createTestEntity(int dkey) {
    return create();
  }

  public static Sector create() {
    return create(DSID.colID(UUID.randomUUID().toString()), DSID.of(subjectDatasetKey, UUID.randomUUID().toString()));
  }

  public static Sector create(DSID<String> target, DSID<String> subject) {
    Sector d = new Sector();

    d.setDatasetKey(target.getDatasetKey());
    d.setTarget(TestEntityGenerator.newSimpleNameWithoutStatusParent());
    d.getTarget().setId(target.getId());

    d.setSubjectDatasetKey(subject.getDatasetKey());
    d.setSubject(TestEntityGenerator.newSimpleName());
    d.getSubject().setId(subject.getId());
    d.setOriginalSubjectId("12345678");

    // syncAttempt and datasetImportAttempt is only set separately not via create!

    d.setMode(Sector.Mode.ATTACH);
    d.setCode(NomCode.ZOOLOGICAL);
    d.setPlaceholderRank(Rank.FAMILY);
    d.setRanks(Set.copyOf(Rank.LINNEAN_RANKS));
    d.setEntities(Set.of(EntityType.NAME, EntityType.NAME_USAGE, EntityType.NAME_RELATION));
    d.setNameTypes(Set.of(NameType.SCIENTIFIC, NameType.OTHER));
    d.setNameStatusExclusion(Set.of(NomStatus.CHRESONYM));
    d.setNameFilter("BOLD:.*");
    d.setNote(RandomUtils.randomUnicodeString(1024));
    d.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    d.setModifiedBy(TestEntityGenerator.USER_EDITOR.getKey());
    return d;
  }
  
  @Override
  Sector removeDbCreatedProps(Sector s) {
    // remove newly set property
    s.setOriginalSubjectId(null);
    s.getTarget().setBroken(false);
    s.getSubject().setBroken(false);
    return s;
  }
  
  @Override
  void updateTestObj(Sector s) {
    s.setNote("not my thing");
  }
  
  /**
   * A SOURCE sector is what an aggregating source - WoRMS, WFO, ITIS - declares about a part of its own
   * data so that part can carry metadata. Its id IS the ColDP sourceID, it has no subject at all, and a
   * project sector absorbs it by pointing at it. See issue #1273.
   */
  @Test
  public void sourceSectorsAndSubjectSectorLink() throws Exception {
    MybatisTestUtils.populateDraftTree(session());

    // two source declared sectors inside the external dataset, ids taken straight from the sourceIDs.
    // A fully NULL subject must not trip UNIQUE (dataset_key, subject_dataset_key, subject_id).
    Sector src = new Sector();
    src.setDatasetKey(subjectDatasetKey);
    src.setId(1044);
    src.setMode(Sector.Mode.SOURCE);
    src.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    src.setModifiedBy(TestEntityGenerator.USER_EDITOR.getKey());
    mapper().createWithID(src);

    Sector src2 = new Sector();
    src2.setDatasetKey(subjectDatasetKey);
    src2.setId(1130);
    src2.setMode(Sector.Mode.SOURCE);
    src2.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    src2.setModifiedBy(TestEntityGenerator.USER_EDITOR.getKey());
    mapper().createWithID(src2);
    commit();

    Sector read = mapper().get(DSID.of(subjectDatasetKey, 1044));
    assertEquals(Sector.Mode.SOURCE, read.getMode());
    assertNull(read.getSubjectDatasetKey());
    assertNull(read.getSubjectSectorId());

    // a project sector absorbing the source sector's metadata
    Sector col = createTestEntity(targetDatasetKey);
    col.setSubjectSectorId(1044);
    col.getTarget().setId("t4");
    mapper().create(col);
    commit();

    Sector col2 = mapper().get(col);
    assertEquals(Integer.valueOf(1044), col2.getSubjectSectorId());
    assertEquals(Integer.valueOf(subjectDatasetKey), col2.getSubjectDatasetKey());

    // when the source drops that sub source the link clears, but the sector keeps its source dataset.
    // The FK scopes SET NULL to subject_sector_id; a plain composite one would wipe both.
    mapper().delete(DSID.of(subjectDatasetKey, 1044));
    commit();

    Sector col3 = mapper().get(col);
    assertNull(col3.getSubjectSectorId());
    assertEquals(Integer.valueOf(subjectDatasetKey), col3.getSubjectDatasetKey());
  }

  /**
   * The hasMetadata and subjectSectorId filters, so a source page can list just the sectors that
   * describe themselves. New SQL only fails at runtime, so it needs exercising.
   */
  @Test
  public void searchByMetadata() throws Exception {
    add2Sectors();
    var req = SectorSearchRequest.byProject(targetDatasetKey);

    req.setHasMetadata(true);
    assertTrue(mapper().search(req, new Page()).isEmpty());
    req.setHasMetadata(false);
    assertEquals(2, mapper().search(req, new Page()).size());

    mapper(SectorMetadataMapper.class).create(DSID.of(targetDatasetKey, s1.getId()),
      SectorMetadataMapperTest.metadata("World Porifera Database"));
    commit();

    req.setHasMetadata(true);
    var found = mapper().search(req, new Page());
    assertEquals(1, found.size());
    assertEquals(s1.getId(), found.get(0).getId());

    req.setHasMetadata(false);
    assertEquals(1, mapper().search(req, new Page()).size());

    req.setHasMetadata(null);
    req.setSubjectSectorId(1044);
    assertTrue(mapper().search(req, new Page()).isEmpty());
  }

  @Test(expected = PersistenceException.class)
  public void unique() throws Exception {
    Sector d1 = create();
    mapper().create(d1);
    commit();

    // now it has a id that already exists
    mapper().create(d1);
    commit();
  }
  
  @Test
  public void process(){
    // processing
    DecisionMapperTest.CountHandler handler = new DecisionMapperTest.CountHandler();
    mapper().processDataset(Datasets.COL).forEach(handler);
    assertEquals(0, handler.counter.size());
  }

  @Test
  public void listProjectKeys(){
    // just test valid sql rather than expected outcomes
    mapper().listProjectKeys(appleKey);
  }

}