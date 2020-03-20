package life.catalogue.db.mapper;

import com.google.common.collect.Lists;
import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.Sector;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.*;
import org.gbif.nameparser.api.NomCode;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.junit.Assert;
import org.junit.Test;

import java.net.URI;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
    d.applyUser(Users.DB_INIT);
    d.setType(DatasetType.TAXONOMIC);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setGbifKey(UUID.randomUUID());
    d.setTitle(RandomUtils.randomLatinString(80));
    d.setDescription(RandomUtils.randomLatinString(500));
    d.setLicense(License.CC0);
    d.setImportFrequency(Frequency.MONTHLY);
    for (int i = 0; i < 8; i++) {
      d.getAuthorsAndEditors().add(RandomUtils.randomLatinString(100));
    }
    d.setContact("Hans Peter");
    d.setDataAccess(URI.create("https://api.gbif.org/v1/dataset/" + d.getGbifKey()));
    d.setDataFormat(DataFormat.ACEF);
    d.setReleased(LocalDate.now());
    d.setVersion("v123");
    d.setWebsite(URI.create("https://www.gbif.org/dataset/" + d.getGbifKey()));
    d.setNotes("my notes");
    d.setCode(NomCode.ZOOLOGICAL);
    d.getOrganisations().add("my org");
    d.getOrganisations().add("your org");
    d.putSetting(DatasetSettings.REMATCH_DECISIONS, false);
    d.putSetting(DatasetSettings.NOMENCLATURAL_CODE, NomCode.BOTANICAL);
    d.putSetting(DatasetSettings.CSV_DELIMITER, "fun");
    d.putSetting(DatasetSettings.DISTRIBUTION_GAZETTEER, Gazetteer.ISO);
    return d;
  }
  
  @Test
  public void roundtrip() throws Exception {
    Dataset d1 = create();
    mapper().create(d1);

    commit();

    Dataset d2 = TestEntityGenerator.nullifyDate(mapper().get(d1.getKey()));
    // we generate this on the fly
    d2.setContributesTo(null);
  
    //printDiff(d1, d2);
    assertEquals(d1, d2);
  }

  @Test
  public void immutableOrigin() throws Exception {
    Dataset d1 = create();
    mapper().create(d1);
    DatasetOrigin o = d1.getOrigin();
    assertEquals(DatasetOrigin.EXTERNAL, o);

    d1.setOrigin(DatasetOrigin.MANAGED);
    mapper().update(d1);

    commit();

    Dataset d2 = mapper().get(d1.getKey());
    assertEquals(DatasetOrigin.EXTERNAL, d2.getOrigin());
  }
  
  @Test
  public void archive() throws Exception {
    Dataset d1 = create();
    mapper().create(d1);
    commit();
  
    mapper().createArchive(d1.getKey(), Datasets.DRAFT_COL);
    // reload to also get the creation/modified dates
    d1 = mapper().get(d1.getKey());
    
    Dataset d2 = mapper().getArchive(d1.getKey(), Datasets.DRAFT_COL);
    
    //printDiff(d1, d2);
    assertEquals(d1, d2);
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
    assertEquals(3, mapper().count(null));

    mapper().create(create());
    mapper().create(create());
    // even thogh not committed we are in the same session so we see the new
    // datasets already
    assertEquals(5, mapper().count(null));

    commit();
    assertEquals(5, mapper().count(null));
  }

  @Test
  public void keys() throws Exception {
    List<Integer> expected = mapper().list(new Page(100)).stream().map(Dataset::getKey).collect(Collectors.toList());
    Dataset d = create();
    mapper().create(d);
    commit();
    expected.add(d.getKey());
    d = create();
    mapper().create(d);
    commit();
    expected.add(d.getKey());
    d = create();
    mapper().create(d);
    commit();
    expected.add(d.getKey());
    Collections.sort(expected);
    List<Integer> actual = mapper().keys();
    Collections.sort(actual);
    assertEquals(expected, actual);
  }

  private List<Dataset> createExpected() throws Exception {
    List<Dataset> ds = Lists.newArrayList();
    ds.add(mapper().get(Datasets.NAME_INDEX));
    ds.add(mapper().get(Datasets.DRAFT_COL));
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
    removeCreated(ds);

    // get first page
    Page p = new Page(0, 4);

    List<Dataset> res = removeCreated(mapper().list(p));
    assertEquals(4, res.size());
    
    Javers javers = JaversBuilder.javers().build();
    Diff diff = javers.compare(ds.get(0), res.get(0));
    assertEquals(0, diff.getChanges().size());
    assertEquals(ds.subList(0, 4), res);

    // next page (d5-8)
    p.next();
    res = removeCreated(mapper().list(p));
    assertEquals(4, res.size());
    assertEquals(ds.subList(4, 8), res);

    // next page (d9)
    p.next();
    res = removeCreated(mapper().list(p));
    assertEquals(1, res.size());
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
    int count = mapper().count(DatasetSearchRequest.byQuery("worms"));
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
    createSector(Datasets.DRAFT_COL, d3);
    createSector(Datasets.DRAFT_COL, d4);
    commit();

    DatasetSearchRequest query = new DatasetSearchRequest();
    query.setCreated(LocalDate.parse("2031-12-31"));
    assertTrue(mapper().search(query, new Page()).isEmpty());

    // apple.sql contains one dataset from 2017
    query.setCreated(LocalDate.parse("2018-02-01"));
    assertEquals(6, mapper().search(query, new Page()).size());

    query.setCreated(LocalDate.parse("2016-02-01"));
    assertEquals(7, mapper().search(query, new Page()).size());

    query.setReleased(LocalDate.parse("2007-11-21"));
    query.setModified(LocalDate.parse("2031-12-31"));
    assertEquals(0, mapper().search(query, new Page()).size());

    // check different orderings
    query = DatasetSearchRequest.byQuery("worms");
    for (DatasetSearchRequest.SortBy by : DatasetSearchRequest.SortBy.values()) {
      query.setSortBy(by);
      List<Dataset> datasets = mapper().search(query, new Page());
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
      List<Dataset> datasets = mapper().search(query, new Page());
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
    query.setContributesTo(Datasets.DRAFT_COL);
    assertEquals(1, mapper().search(query, new Page()).size());
  
    query.setQ(null);
    assertEquals(2, mapper().search(query, new Page()).size());
  
  
  
    // non existing catalogue
    query.setContributesTo(99);
    assertEquals(0, mapper().search(query, new Page()).size());

    query.setContributesTo(Datasets.DRAFT_COL);
    assertEquals(2, mapper().search(query, new Page()).size());
    
    // partial search
    // https://github.com/Sp2000/colplus-backend/issues/353
    query = DatasetSearchRequest.byQuery("wor");
    List<Dataset> res = mapper().search(query, new Page());
    assertEquals(1, res.size());
  
    // create another catalogue to test non draft sectors
    Dataset cat = TestEntityGenerator.newDataset("cat2");
    TestEntityGenerator.setUser(cat);
    mapper(DatasetMapper.class).create(cat);
    // new sectors
    createSector(cat.getKey(), d1);
    createSector(cat.getKey(), d5);
    commit();
  
    // old query should still be the same
    query = DatasetSearchRequest.byQuery("worms");
    query.setContributesTo(Datasets.DRAFT_COL);
    assertEquals(1, mapper().search(query, new Page()).size());
  
    query = new DatasetSearchRequest();
    query.setContributesTo(Datasets.DRAFT_COL);
    assertEquals(2, mapper().search(query, new Page()).size());
  
    query = new DatasetSearchRequest();
    query.setContributesTo(cat.getKey());
    // d5 was deleted!
    assertEquals(1, mapper().search(query, new Page()).size());
    
    // by origin
    query = new DatasetSearchRequest();
    query.setOrigin(DatasetOrigin.MANAGED);
    assertEquals(2, mapper().search(query, new Page()).size());
  
    query.setOrigin(DatasetOrigin.EXTERNAL);
    assertEquals(6, mapper().search(query, new Page()).size());
  }

  private int createSearchableDataset(String title, String author, String organisation, String description) {
    Dataset ds = new Dataset();
    ds.setTitle(title);
    if (author != null) {
      ds.setAuthorsAndEditors(Lists.newArrayList(author.split(";")));
    }
    ds.getOrganisations().add(organisation);
    ds.setDescription(description);
    ds.setType(DatasetType.TAXONOMIC);
    ds.setOrigin(DatasetOrigin.EXTERNAL);
    mapper().create(TestEntityGenerator.setUserDate(ds));
    return ds.getKey();
  }
  
  @Override
  Dataset createTestEntity(int dkey) {
    return create();
  }
  
  @Override
  Dataset removeDbCreatedProps(Dataset d) {
    // dont compare created stamps
    d.setCreated(null);
    d.setModified(null);
    // we generate this on the fly
    d.setContributesTo(null);
    return d;
  }
  
  private List<Dataset> removeCreated(List<Dataset> ds) {
    for (Dataset d : ds) {
      removeDbCreatedProps(d);
    }
    return ds;
  }
  
  @Override
  void updateTestObj(Dataset d) {
    d.setDescription("brand new thing");
  }
}
