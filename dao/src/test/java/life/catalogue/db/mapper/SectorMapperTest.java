package life.catalogue.db.mapper;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SectorImport;
import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.MybatisTestUtils;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

  private void addImport(Sector s, ImportState state, LocalDateTime finished) {
    SectorImport si = SectorImportMapperTest.create(state, s);
    si.setFinished(finished);
    si.setCreatedBy(Users.TESTER);
    mapper(SectorImportMapper.class).create(si);

    if (state == ImportState.FINISHED) {
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
  public void list() {
    add2Sectors();
    assertEquals(2, mapper().listByDataset(targetDatasetKey,subjectDatasetKey).size());
    assertEquals(0, mapper().listByDataset(targetDatasetKey,-432).size());
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

    addImport(s1, ImportState.FINISHED, LocalDateTime.of(2019, 12, 24, 12, 0, 0));
    addImport(s1, ImportState.FINISHED, LocalDateTime.of(2020, 1, 10, 12, 0, 0));
    addImport(s1, ImportState.FAILED, LocalDateTime.of(2020, 2, 11, 12, 0, 0));

    addImport(s2, ImportState.FAILED, LocalDateTime.of(2018, 1, 10, 12, 0, 0));
    addImport(s2, ImportState.FINISHED, LocalDateTime.of(2020, 1, 21, 12, 0, 0));
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