package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Identifier;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.Gender;
import life.catalogue.api.vocab.NomStatus;

import org.gbif.nameparser.api.Rank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.*;
import static org.junit.Assert.*;

/**
 *
 */
public class NameMapperTest extends CRUDDatasetScopedStringTestBase<Name, NameMapper> {
  
  private NameMapper nameMapper;
  
  public NameMapperTest() {
    super(NameMapper.class);
  }
  
  @Before
  public void initMappers() {
    nameMapper = testDataRule.getMapper(NameMapper.class);
  }

  @Test
  public void copyDataset() throws Exception {
    // verbatim and reference are referred to from name records so we also need to copy them
    CopyDatasetTestComponent.copy(mapper(VerbatimRecordMapper.class), testDataRule.testData.key, false);
    CopyDatasetTestComponent.copy(mapper(ReferenceMapper.class), testDataRule.testData.key, false);
    commit();
    CopyDatasetTestComponent.copy(mapper(), testDataRule.testData.key, true);
  }

  @Test
  public void sectorProcessable() throws Exception {
    SectorProcessableTestComponent.test(mapper(), DSID.of(testDataRule.testData.key, 1));
  }

  @Test
  public void processAll() throws Exception {
    mapper().processAll(null);
    mapper().processAll(Rank.GENUS);
  }

  @Test
  public void indexGroup() throws Exception {
    mapper().indexGroup(1);
    mapper().indexGroupIds(2);
  }

  @Test
  public void ambiguousRankNameIds() throws Exception {
    // no real data to delete but tests valid SQL
    mapper().unrankedRankNameIds(datasetKey, null);
    mapper().unrankedRankNameIds(datasetKey, 1);
  }

  @Test
  public void deleteBySectorAndRank() throws Exception {
    // no real data to delete but tests valid SQL
    mapper().deleteBySectorAndRank(DSID.of(datasetKey, 1), Rank.GENUS, null);
    mapper().deleteBySectorAndRank(DSID.of(datasetKey, 1), Rank.SUPERSECTION_BOTANY, Set.of());
    mapper().deleteBySectorAndRank(DSID.of(datasetKey, 1), Rank.FAMILY, Set.of("1,2,3", "abc"));
  }

  @Override
  Name createTestEntity(int dkey) {
    Name n = TestEntityGenerator.newName(dkey, "sk1");
    n.setNomStatus(NomStatus.ACCEPTABLE);
    n.setGender(Gender.FEMININE);
    n.setGenderAgreement(true);
    n.setPublishedInPage("223");
    n.setPublishedInPageLink("http://bhl.org/pub/1234567");
    n.setNomenclaturalNote("nom. illeg.");
    n.setUnparsed("bla bli blub");
    return n;
  }
  
  @Override
  void updateTestObj(Name n) {
    n.setAuthorship("Berta & Tomate");
  }
  
  @Test
  public void roundtrip2() throws Exception {
    Name n1 = TestEntityGenerator.newName("sk1");
    n1.addIdentifier("col:DERF");
    n1.addIdentifier("https://www.wikidata.org/wiki/Q157571");
    nameMapper.create(n1);
    assertNotNull(n1.getId());
    commit();
    
    n1 = removeDbCreatedProps(n1);
    Name n1b = removeDbCreatedProps(nameMapper.get(n1.getKey()));
    printDiff(n1, n1b);
    assertEquals(n1, n1b);
    
    // with explicit homotypic group
    Name n2 = TestEntityGenerator.newName("sk2");
//    n2.setHomotypicNameId(n1.getId());
    nameMapper.create(n2);
    
    commit();
    
    // we use a new instance of n1 with just the keys for the equality tests
    // n1 = new Name();
    // n1.setKey(n2.getBasionymKey());
    // n1.setId(n2.getBasionymKey());
    // n2.setBasionymKey(n1);
    
    Name n2b = removeDbCreatedProps(nameMapper.get(n2.getKey()));
    assertEquals(removeDbCreatedProps(n2), n2b);
  }
  
  @Test
  public void count() throws Exception {
    generateDatasetImport(DATASET11.getKey());
    commit();
    assertEquals(5, nameMapper.count(DATASET11.getKey()));
    
    nameMapper.create(TestEntityGenerator.newName());
    nameMapper.create(TestEntityGenerator.newName());
    generateDatasetImport(DATASET11.getKey());
    commit();
    
    assertEquals(7, nameMapper.count(DATASET11.getKey()));
  }

  @Test
  public void deleteOrphans() throws Exception {
    LocalDateTime bFirst = LocalDateTime.now();
    TimeUnit.SECONDS.sleep(1);
    Name n1 = TestEntityGenerator.newName("n1");
    TestEntityGenerator.nullifyDate(n1);
    nameMapper.create(n1);
    commit();
    assertNotNull(nameMapper.get(n1.getKey()));

    TimeUnit.SECONDS.sleep(1);
    removeNameRelated(n1.getDatasetKey());

    Name n2 = TestEntityGenerator.newName("n2");
    TestEntityGenerator.nullifyDate(n2);
    nameMapper.create(n2);
    commit();
    assertNotNull(nameMapper.get(n2.getKey()));

    int dels = nameMapper.deleteOrphans(n1.getDatasetKey(), bFirst);
    assertEquals(1, dels);
    commit();
    // should still be there
    assertNotNull(nameMapper.get(n1.getKey()));
    assertNotNull(nameMapper.get(n2.getKey()));

    dels = nameMapper.deleteOrphans(n1.getDatasetKey(), null);
    assertEquals(2, dels);
    commit();
    // all should be gone now
    assertNull(nameMapper.get(n1.getKey()));
    assertNull(nameMapper.get(n2.getKey()));
  }

  @Test
  public void listOrphans() throws Exception {
    LocalDateTime bFirst = LocalDateTime.now();

    Name n = TestEntityGenerator.newName("n1");
    nameMapper.create(n);
    nameMapper.create(TestEntityGenerator.newName("n2"));
    commit();

    List<Name> dels = nameMapper.listOrphans(n.getDatasetKey(), bFirst, new Page());
    assertEquals(1, dels.size());

    dels = nameMapper.listOrphans(n.getDatasetKey(), null, new Page());
    assertEquals(3, dels.size());
  }
  
  @Test
  public void hasData() throws Exception {
    assertTrue(nameMapper.hasData(DATASET11.getKey()));
    assertFalse(nameMapper.hasData(3));
  }
  
  @Test
  public void listByReference() throws Exception {
    Name acc1 = newAcceptedName("Nom uno");
    nameMapper.create(acc1);
    assertTrue(nameMapper.listByReference(REF1b.getDatasetKey(), REF1b.getId()).isEmpty());
    
    Name acc2 = newAcceptedName("Nom duo");
    acc2.setPublishedInId(REF1b.getId());
    Name acc3 = newAcceptedName("Nom tres");
    acc3.setPublishedInId(REF1b.getId());
    nameMapper.create(acc2);
    nameMapper.create(acc3);
    
    // we have one ref from the apple.sql
    assertEquals(1, nameMapper.listByReference(REF1.getDatasetKey(), REF1.getId()).size());
    assertEquals(2, nameMapper.listByReference(REF1b.getDatasetKey(), REF1b.getId()).size());
  }

  @Test
  public void addIdentifier() throws Exception {
    Name n = TestEntityGenerator.newName("sk1");
    n.addIdentifier("col:DERF");
    n.addIdentifier("https://www.wikidata.org/wiki/Q157571");
    nameMapper.create(n);
    commit();

    nameMapper.addIdentifier(n, List.of());
    var n2 = nameMapper.get(n);
    assertEquals(n.getIdentifier(), n2.getIdentifier());

    final var id = new Identifier(Identifier.Scope.IPNI, "2345678uio");
    nameMapper.addIdentifier(n, List.of(id));
    n2 = nameMapper.get(n);
    n.getIdentifier().add(id);
    assertEquals(n.getIdentifier(), n2.getIdentifier());
  }

  private static Name newAcceptedName(String scientificName) {
    return newName(DATASET11.getKey(), scientificName.toLowerCase().replace(' ', '-'), scientificName);
  }

  @Override
  Name removeDbCreatedProps(Name obj) {
    obj.setNamesIndexId(null);
    obj.setNamesIndexType(null);
    return super.removeDbCreatedProps(obj);
  }
}
