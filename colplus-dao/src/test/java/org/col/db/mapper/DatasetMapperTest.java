package org.col.db.mapper;

import java.net.URI;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import org.col.api.RandomUtils;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Dataset;
import org.col.api.model.Page;
import org.col.api.model.Sector;
import org.col.api.search.DatasetSearchRequest;
import org.col.api.vocab.*;
import org.gbif.nameparser.api.NomCode;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.junit.Assert;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.newNameRef;
import static org.junit.Assert.*;

/**
 *
 */
public class DatasetMapperTest extends MapperTestBase<DatasetMapper> {

  public DatasetMapperTest() {
    super(DatasetMapper.class);
  }
  
  public static Dataset create() {
    Dataset d = new Dataset();
    d.applyUser(Users.DB_INIT);
    d.setType(DatasetType.GLOBAL);
    d.setNamesIndexContributor(true);
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
  
    printDiff(d1, d2);
    assertEquals(d1, d2);
  }

  @Test
  public void delete() throws Exception {
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
    assertEquals(5, mapper().count(null));

    mapper().create(create());
    mapper().create(create());
    // even thogh not committed we are in the same session so we see the new
    // datasets already
    assertEquals(7, mapper().count(null));

    commit();
    assertEquals(7, mapper().count(null));
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
    ds.add(mapper().get(Datasets.COL));
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
    assertEquals(2, res.size());
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
  
  private void createSector(int datasetKey) {
    Sector s = new Sector();
    s.setDatasetKey(datasetKey);
    s.setMode(Sector.Mode.ATTACH);
    s.setSubject(newNameRef());
    s.setTarget(newNameRef());
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
    createSector(d3);
    createSector(d4);
    commit();

    DatasetSearchRequest query = new DatasetSearchRequest();
    query.setCreated(LocalDate.parse("2031-12-31"));
    assertTrue(mapper().search(query, new Page()).isEmpty());

    // apple.sql contains one dataset from 2017
    query.setCreated(LocalDate.parse("2018-02-01"));
    assertEquals(8, mapper().search(query, new Page()).size());

    query.setCreated(LocalDate.parse("2016-02-01"));
    assertEquals(9, mapper().search(query, new Page()).size());

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
  
  }

  private static List<Dataset> removeCreated(List<Dataset> ds) {
    for (Dataset d : ds) {
      // dont compare created stamps
      d.setCreated(null);
      d.setModified(null);
      // we generate this on the fly
      d.setContributesTo(null);
    }
    return ds;
  }

  private int createSearchableDataset(String title, String author, String organisation, String description) {
    Dataset ds = new Dataset();
    ds.setTitle(title);
    if (author != null) {
      ds.setAuthorsAndEditors(Lists.newArrayList(author.split(";")));
    }
    ds.getOrganisations().add(organisation);
    ds.setDescription(description);
    ds.setType(DatasetType.GLOBAL);
    ds.setNamesIndexContributor(true);
    mapper().create(TestEntityGenerator.setUserDate(ds));
    return ds.getKey();
  }
}
