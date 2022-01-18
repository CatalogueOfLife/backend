package life.catalogue.db.mapper;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.*;

import life.catalogue.common.date.FuzzyDate;

import org.gbif.nameparser.api.NomCode;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 */
public class DatasetMapperTest extends CRUDTestBase<Integer, Dataset, DatasetMapper> {

  public DatasetMapperTest() {
    super(DatasetMapper.class);
  }

  public static Dataset create() {
    Dataset d = new Dataset();
    populate(d);
    return d;
  }

  public static Dataset populate(Dataset d) {
    d.setGbifKey(UUID.randomUUID());
    d.setGbifPublisherKey(UUID.randomUUID());
    d.applyUser(Users.DB_INIT);
    d.setType(DatasetType.TAXONOMIC);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setTitle(RandomUtils.randomLatinString(80));
    d.setDescription(RandomUtils.randomLatinString(500));
    d.setLicense(License.CC0);
    d.setCreator(new ArrayList<>());
    d.setEditor(new ArrayList<>());
    d.setContributor(new ArrayList<>());
    for (int i = 0; i < 8; i++) {
      d.getCreator().add(Agent.parse(RandomUtils.randomLatinString(100)));
      d.getEditor().add(Agent.parse(RandomUtils.randomLatinString(100)));
      d.getContributor().add(Agent.parse(RandomUtils.randomLatinString(100)));
    }
    d.setContact(Agent.parse("Hans Peter"));
    d.setPublisher(Agent.parse("Peter Publish"));
    d.setIssued(FuzzyDate.now());
    d.setVersion("v123");
    d.setUrl(URI.create("https://www.gbif.org/dataset/" + d.getVersion()));
    d.setNotes("my notes");
    d.setDoi(DOI.test(UUID.randomUUID().toString()));
    d.setSize(0);
    // we dont add source citations as the DatasetMapper does not persist them
    // this is done in the DAO only and should be tested there!
    return d;
  }

  @Test
  public void settings() throws Exception {
    Dataset d1 = create();
    mapper().create(d1);
    commit();

    DatasetSettings ds = mapper().getSettings(d1.getKey());
    assertNotNull(ds);
    assertTrue(ds.isEmpty());

    ds = new DatasetSettings();
    ds.put(Setting.IMPORT_FREQUENCY, Frequency.MONTHLY);
    ds.put(Setting.DATA_ACCESS, URI.create("https://api.gbif.org/v1/dataset/" + d1.getGbifKey()));
    ds.put(Setting.DATA_FORMAT, DataFormat.ACEF);

    mapper().updateSettings(d1.getKey(), ds, Users.TESTER);
    commit();

    DatasetSettings ds2 = mapper().getSettings(d1.getKey());
    printDiff(ds2, ds);
    assertEquals(ds2, ds);

    ds.put(Setting.REMATCH_DECISIONS, true);
    ds.remove(Setting.DATA_ACCESS);
    mapper().updateSettings(d1.getKey(), ds, Users.TESTER);

    ds2 = mapper().getSettings(d1.getKey());
    assertEquals(ds2, ds);
  }

  @Test
  public void roundtrip() throws Exception {
    Dataset d1 = create();
    // this also tests the custom PersonTypeHandler and PersonArrayTypeHandler
    // thats why we need lots of strange potentially data that needs to be escaped properly
    d1.getContact().setFamily("O'Hara");
    d1.getContact().setGiven("Œre-Fölíñgé");
    d1.getContact().setEmail("Maxi\t<oere@foo.bar>\nhidden");
    d1.getContact().setOrcid("1234,\"5678\".90/x");
    mapper().create(d1);

    commit();

    Dataset d2 = TestEntityGenerator.nullifyDate(mapper().get(d1.getKey()));

    assertEquals(d1.getContact(), d2.getContact());
    assertEquals(d1, d2);
  }


  @Test
  public void privateProjectReleases() throws Exception {
    // use a real user (not a bot) as a creator
    User user = new User();
    user.setUsername("abcd");
    session().getMapper(UserMapper.class).create(user);
    final int ukey = user.getKey();

    Dataset proj = create();
    proj.setOrigin(DatasetOrigin.MANAGED);
    proj.setCreatedBy(ukey);
    mapper().create(proj);
    commit();
    final int projKey = proj.getKey();

    Dataset rel1 = create();
    rel1.setOrigin(DatasetOrigin.RELEASED);
    rel1.setSourceKey(projKey);
    mapper().create(rel1);

    Dataset rel2 = create();
    rel2.setOrigin(DatasetOrigin.RELEASED);
    rel2.setSourceKey(projKey);
    rel2.setPrivat(true);
    mapper().create(rel2);
    commit();

    List<Dataset> resp = mapper().search(null, null, new Page());
    assertEquals(5, resp.size()); // 1 public, 1 private

    final DatasetSearchRequest req = new DatasetSearchRequest();
    req.setReleasedFrom(projKey);

    resp = mapper().search(req, null, new Page());
    assertEquals(1, resp.size()); // 1 public, 1 private

    // this user has access to the project and should therefore have access to all private releases!
    resp = mapper().search(req, ukey, new Page());
    assertEquals(2, resp.size());

    // add a reviewer
    User reviewer = new User();
    reviewer.setUsername("reviewer");
    session().getMapper(UserMapper.class).create(reviewer);
    final int rkey = reviewer.getKey();

    resp = mapper().search(req, rkey, new Page());
    assertEquals(1, resp.size());

    mapper().addReviewer(projKey, rkey, ukey);
    resp = mapper().search(req, rkey, new Page());
    assertEquals(2, resp.size());
  }

  @Test
  public void isPrivateAndExists() throws Exception {
    Dataset d1 = create();
    mapper().create(d1);
    commit();

    final DatasetSearchRequest req = new DatasetSearchRequest();
    List<Dataset> resp = mapper().search(req, null, new Page());
    assertEquals(4, resp.size());

    assertTrue(mapper().exists(d1.getKey()));
    assertFalse(mapper().exists(-3456));

    assertFalse(d1.isPrivat());
    assertFalse(mapper().isPrivate(d1.getKey()));
    assertFalse(mapper().isPrivate(-528));

    // now add a real user (not a bot) as a creator
    User user = new User();
    user.setUsername("abcd");
    session().getMapper(UserMapper.class).create(user);
    final int ukey = user.getKey();

    d1.setPrivat(true);
    mapper().update(d1);
    mapper().addEditor(d1.getKey(), ukey, Users.DB_INIT);
    commit();

    assertTrue(d1.isPrivat());
    assertTrue(mapper().isPrivate(d1.getKey()));
    assertFalse(mapper().isPrivate(-528));


    // 5 datasets, 1 names index, 3 creator=DB_INIT, 1 creator ukey and private
    resp = mapper().search(req, null, new Page());
    assertEquals(3, resp.size());

    resp = mapper().search(req, Users.DB_INIT, new Page());
    assertEquals(3, resp.size());

    resp = mapper().search(req, Users.TESTER, new Page());
    assertEquals(3, resp.size());

    // this user has access to the private dataset!
    resp = mapper().search(req, ukey, new Page());
    assertEquals(4, resp.size());

    // add editor to private dataset
    mapper().addEditor(d1.getKey(), Users.TESTER, Users.TESTER);
    resp = mapper().search(req, Users.TESTER, new Page());
    assertEquals(4, resp.size());
  }

  @Test
  public void immutableOriginAndSourceKey() throws Exception {
    Dataset d1 = create();
    d1.setSourceKey(Datasets.COL);
    d1.setOrigin(DatasetOrigin.RELEASED);
    mapper().create(d1);
    DatasetOrigin o = d1.getOrigin();
    assertEquals(DatasetOrigin.RELEASED, o);
    assertEquals((Integer)Datasets.COL, d1.getSourceKey());

    d1.setOrigin(DatasetOrigin.MANAGED);
    d1.setSourceKey(null);
    mapper().update(d1);

    commit();

    Dataset d2 = mapper().get(d1.getKey());
    assertEquals(DatasetOrigin.RELEASED, d2.getOrigin());
    assertEquals((Integer)Datasets.COL, d2.getSourceKey());
  }

  /**
   * We only logically delete datasets, dont run super test
   */
  @Test
  @Override
  public void deleted() throws Exception {
    Dataset d1 = create();
    mapper().create(d1);

    commit();

    // not deleted yet
    Dataset d = mapper().get(d1.getKey());
    assertNull(d.getDeleted());
    assertNotNull(d.getCreated());

    // mark deleted
    mapper().delete(d1.getKey());
    d = mapper().get(d1.getKey());
    assertNotNull(d.getDeleted());
  }

  @Test
  public void count() throws Exception {
    assertEquals(3, mapper().count(null, null));

    mapper().create(create());
    mapper().create(create());
    // even thogh not committed we are in the same session so we see the new
    // datasets already
    assertEquals(5, mapper().count(null, null));

    commit();
    assertEquals(5, mapper().count(null, null));
  }

  @Test
  public void keys() throws Exception {
    List<Integer> external = new ArrayList<>();
    Dataset d = create();
    mapper().create(d);
    commit();
    external.add(d.getKey());
    d = create();
    mapper().create(d);
    commit();
    external.add(d.getKey());
    d = create();
    mapper().create(d);
    commit();
    external.add(d.getKey());
    external.add(12); // this is a preexsting dataset
    Collections.sort(external);

    List<Integer> all = mapper().list(new Page(100)).stream().map(Dataset::getKey).collect(Collectors.toList());
    Collections.sort(all);

    List<Integer> actual = mapper().keys();
    Collections.sort(actual);
    assertEquals(all, actual);

    actual = mapper().keys(DatasetOrigin.EXTERNAL);
    Collections.sort(actual);
    assertEquals(external, actual);

    actual = mapper().keys(DatasetOrigin.EXTERNAL, DatasetOrigin.RELEASED);
    Collections.sort(actual);
    assertEquals(external, actual);

    actual = mapper().keys(DatasetOrigin.RELEASED);
    assertTrue(actual.isEmpty());

    actual = mapper().keys(DatasetOrigin.EXTERNAL, DatasetOrigin.MANAGED);
    Collections.sort(actual);
    assertEquals(all, actual);
  }

  private List<Dataset> createExpected() throws Exception {
    List<Dataset> ds = new ArrayList<>();
    ds.add(mapper().get(Datasets.COL));
    ds.add(mapper().get(TestEntityGenerator.DATASET11.getKey()));
    ds.add(mapper().get(TestEntityGenerator.DATASET12.getKey()));
    ds.add(create());
    ds.add(create());
    ds.add(create());
    ds.add(create());
    ds.add(create());
    return ds;
  }

  @Test
  public void list() throws Exception {
    List<Dataset> ds = createExpected();
    for (Dataset d : ds) {
      if (d.getKey() == null) {
        mapper().create(d);
      }
      // dont compare created stamps
      d.setCreated(null);
    }
    commit();
    removeDbCreatedProps(ds);

    // get first page
    Page p = new Page(0, 4);

    List<Dataset> res = removeDbCreatedProps(mapper().list(p));
    assertEquals(4, res.size());

    assertEquals(ds.get(0), res.get(0));
    assertEquals(ds.subList(0, 4), res);

    // next page (d5-8)
    p.next();
    res = removeDbCreatedProps(mapper().list(p));
    assertEquals(4, res.size());
    assertEquals(ds.subList(4, 8), res);

    p.next();
    res = removeDbCreatedProps(mapper().list(p));
    assertTrue(res.isEmpty());
  }

  @Test
  public void listNeverImported() throws Exception {
    List<Dataset> ds = createExpected();
    for (Dataset d : ds) {
      if (d.getKey() == null) {
        mapper().create(d);
      }
    }
    commit();

    List<Dataset> never = mapper().listNeverImported(3);
    assertEquals(3, never.size());

    List<Dataset> tobe = mapper().listToBeImported(3);
    assertEquals(0, tobe.size());
  }

  @Test
  public void countSearchResults() throws Exception {
    createSearchableDataset("BIZ", "markus", "CUIT", "A sentence with worms and stuff");
    createSearchableDataset("ITIS", "markus", "ITIS", "Also contains worms");
    createSearchableDataset("WORMS", "markus", "WORMS", "The Worms dataset");
    createSearchableDataset("FOO", "markus", "BAR", null);
    commit();
    int count = mapper().count(DatasetSearchRequest.byQuery("worms"), null);
    assertEquals("01", 3, count);
  }

  private void createSector(int datasetKey, int subjectDatasetKey) {
    Sector s = new Sector();
    s.setDatasetKey(datasetKey);
    s.setSubjectDatasetKey(subjectDatasetKey);
    s.setMode(Sector.Mode.ATTACH);
    s.setSubject(TestEntityGenerator.newSimpleName());
    s.setTarget(TestEntityGenerator.newSimpleName());
    s.setNote(RandomUtils.randomUnicodeString(128));
    s.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    s.setModifiedBy(TestEntityGenerator.USER_EDITOR.getKey());

    mapper(SectorMapper.class).create(s);
  }

  @Test
  public void search() throws Exception {
    final Integer d1 = createSearchableDataset("ITIS", "Mike;Bob", "ITIS", "Also contains worms");
    commit();

    final Integer d2 = createSearchableDataset("BIZ", "bob;jim", "CUIT", "A sentence with worms and worms");
    commit();

    final Integer d3 = createSearchableDataset("WORMS", "Bart", "WORMS", "The Worms dataset");
    final Integer d4 = createSearchableDataset("FOO", "bar", "BAR", null);
    final Integer d5 = createSearchableDataset("WORMS worms", "beard", "WORMS", "Worms with even more worms than worms");
    mapper().delete(d5);
    createSector(Datasets.COL, d3);
    createSector(Datasets.COL, d4);
    createSector(d4, d3);
    commit();

    DatasetSearchRequest query = new DatasetSearchRequest();
    query.setCreated(LocalDate.parse("2031-12-31"));
    assertTrue(mapper().search(query, null, new Page()).isEmpty());

    // apple.sql contains one dataset from 2017
    query.setCreated(LocalDate.parse("2018-02-01"));
    assertEquals(6, mapper().search(query, null, new Page()).size());

    query.setCreated(LocalDate.parse("2016-02-01"));
    assertEquals(7, mapper().search(query, null, new Page()).size());

    query.setIssued(FuzzyDate.of("2007-11-21"));
    query.setModified(LocalDate.parse("2031-12-31"));
    assertEquals(0, mapper().search(query, null, new Page()).size());

    // check different orderings
    query = DatasetSearchRequest.byQuery("worms");
    for (DatasetSearchRequest.SortBy by : DatasetSearchRequest.SortBy.values()) {
      query.setSortBy(by);
      List<Dataset> datasets = mapper().search(query, null, new Page());
      assertEquals(3, datasets.size());
      datasets.forEach(c -> Assert.assertNotEquals("FOO", c.getTitle()));
      switch (by) {
        case CREATED:
        case RELEVANCE:
          assertEquals("Bad ordering by " + by, d3, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d1, datasets.get(2).getKey());
          break;
        case TITLE:
          assertEquals("Bad ordering by " + by, d2, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d1, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d3, datasets.get(2).getKey());
          break;
        case AUTHORS:
          assertEquals("Bad ordering by " + by, d3, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d1, datasets.get(2).getKey());
          break;
        case KEY:
          assertEquals("Bad ordering by " + by, d1, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d3, datasets.get(2).getKey());
        case SIZE:
        case MODIFIED:
          // nothing, from import and all null
      }
    }

    // now try reversed
    query.setReverse(true);
    for (DatasetSearchRequest.SortBy by : DatasetSearchRequest.SortBy.values()) {
      query.setSortBy(by);
      List<Dataset> datasets = mapper().search(query, null, new Page());
      assertEquals(3, datasets.size());
      switch (by) {
        case CREATED:
        case RELEVANCE:
          assertEquals("Bad ordering by " + by, d1, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d3, datasets.get(2).getKey());
          break;
        case TITLE:
          assertEquals("Bad ordering by " + by, d3, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d1, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(2).getKey());
          break;
        case AUTHORS:
          assertEquals("Bad ordering by " + by, d1, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d3, datasets.get(2).getKey());
          break;
        case KEY:
          assertEquals("Bad ordering by " + by, d3, datasets.get(0).getKey());
          assertEquals("Bad ordering by " + by, d2, datasets.get(1).getKey());
          assertEquals("Bad ordering by " + by, d1, datasets.get(2).getKey());
        case SIZE:
        case MODIFIED:
          // nothing, from import and all null
      }
    }


    query = DatasetSearchRequest.byQuery("worms");
    query.setContributesTo(Datasets.COL);
    assertEquals(1, mapper().search(query, null, new Page()).size());

    query.setQ(null);
    assertEquals(2, mapper().search(query, null, new Page()).size());

    query.setContributesTo(d4);
    assertEquals(1, mapper().search(query, null, new Page()).size());

    // non existing catalogue
    query.setContributesTo(99);
    assertEquals(0, mapper().search(query, null, new Page()).size());

    query.setContributesTo(Datasets.COL);
    assertEquals(2, mapper().search(query, null, new Page()).size());

    // by source dataset
    query = new DatasetSearchRequest();
    query.setHasSourceDataset(d3);
    assertEquals(2, mapper().search(query, null, new Page()).size());

    query.setHasSourceDataset(d4);
    assertEquals(1, mapper().search(query, null, new Page()).size());

    query.setHasSourceDataset(99); // non existing
    assertEquals(0, mapper().search(query, null, new Page()).size());

    // partial search
    // https://github.com/Sp2000/colplus-backend/issues/353
    query = DatasetSearchRequest.byQuery("wor");
    List<Dataset> res = mapper().search(query, null, new Page());
    assertEquals(1, res.size());

    // create another catalogue to test non draft sectors
    Dataset cat = TestEntityGenerator.newDataset("cat2");
    TestEntityGenerator.setUser(cat);
    mapper(DatasetMapper.class).create(cat);
    mapper(DatasetPartitionMapper.class).createManagedSequences(cat.getKey());
    // new sectors
    createSector(cat.getKey(), d1);
    createSector(cat.getKey(), d5);
    commit();

    // old query should still be the same
    query = DatasetSearchRequest.byQuery("worms");
    query.setContributesTo(Datasets.COL);
    assertEquals(1, mapper().search(query, null, new Page()).size());

    query = new DatasetSearchRequest();
    query.setContributesTo(Datasets.COL);
    assertEquals(2, mapper().search(query, null, new Page()).size());

    query = new DatasetSearchRequest();
    query.setContributesTo(cat.getKey());
    // d5 was deleted!
    assertEquals(1, mapper().search(query, null, new Page()).size());

    // by origin
    query = new DatasetSearchRequest();
    query.setOrigin(List.of(DatasetOrigin.MANAGED));
    assertEquals(7, mapper().search(query, null, new Page()).size());

    query.setOrigin(List.of(DatasetOrigin.EXTERNAL));
    assertEquals(1, mapper().search(query, null, new Page()).size());

    query.setOrigin(List.of(DatasetOrigin.MANAGED, DatasetOrigin.EXTERNAL));
    assertEquals(8, mapper().search(query, null, new Page()).size());

    // by code
    query = new DatasetSearchRequest();
    query.setCode(NomCode.CULTIVARS);
    assertEquals(0, mapper().search(query, null, new Page()).size());

    // by release
    query = new DatasetSearchRequest();
    query.setReleasedFrom(Datasets.COL);
    assertEquals(0, mapper().search(query, null, new Page()).size());

    // private only
    query = new DatasetSearchRequest();
    query.setPrivat(true);
    assertEquals(0, mapper().search(query, null, new Page()).size());
    query.setPrivat(false);
    assertEquals(8, mapper().search(query, null, new Page()).size());
  }

  private int createSearchableDataset(String title, String author, String organisation, String description) {
    Dataset ds = new Dataset();
    ds.setTitle(title);
    if (author != null) {
      ds.setCreator(Agent.parse(List.of(author.split(";"))));
    }
    ds.setContributor(List.of(Agent.parse(organisation)));
    ds.setDescription(description);
    ds.setType(DatasetType.TAXONOMIC);
    ds.setOrigin(DatasetOrigin.MANAGED);
    ds.setContact(Agent.person("Frank", "Furter", "frank@mailinator.com", "0000-0003-0857-1679"));
    ds.setEditor(List.of(
      Agent.person("Karl", "Marx", "karl@mailinator.com", "0000-0000-0000-0001"),
      Agent.person("Chuck", "Berry", "chuck@mailinator.com", "0000-0666-0666-0666")
    ));
    mapper().create(TestEntityGenerator.setUserDate(ds));

    mapper(DatasetPartitionMapper.class).createManagedSequences(ds.getKey());
    return ds.getKey();
  }

  @Override
  Dataset createTestEntity(int dkey) {
    return create();
  }

  @Override
  Dataset removeDbCreatedProps(Dataset d) {
    return rmDbCreatedProps(d);
  }

  public static Dataset rmDbCreatedProps(Dataset d) {
    return d;
  }

  @Override
  void updateTestObj(Dataset d) {
    d.setDescription("brand new thing");
  }
}
