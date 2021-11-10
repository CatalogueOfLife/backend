package life.catalogue.dao;

import life.catalogue.api.BeanPrinter;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.db.MybatisTestUtils;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.SectorMapperTest;
import life.catalogue.db.mapper.SynonymMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameIndexFactory;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.ibatis.session.SqlSession;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.*;
import static org.junit.Assert.*;

public class TaxonDaoTest extends DaoTestBase {
  static int user = TestEntityGenerator.USER_EDITOR.getKey();
  NameDao nDao;
  TaxonDao tDao;

  public TaxonDaoTest() {
    nDao = new NameDao(PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.passThru(), validator);
    tDao = new TaxonDao(PgSetupRule.getSqlSessionFactory(), nDao, NameUsageIndexService.passThru(), validator);
    SectorDao sdao = new SectorDao(PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), tDao, validator);
    tDao.setSectorDao(sdao);
  }

  @Test
  public void testInfo() {
    final int datasetKey = DATASET11.getKey();
    TaxonInfo info = tDao.getTaxonInfo(DSID.of(datasetKey, TAXON1.getId()));
    BeanPrinter.out(info);

    // See apple.sql
    assertEquals("root-1", info.getTaxon().getId());
    assertEquals(1, info.getTaxon().getReferenceIds().size());
    assertEquals(3, info.getVernacularNames().size());
    assertEquals(2, info.getReferences().size());

    Set<String> refKeys1 = new HashSet<>();
    info.getReferences().values().forEach(r -> refKeys1.add(r.getId()));

    Set<String> refKeys2 = new HashSet<>(info.getTaxon().getReferenceIds());

    Stream<Referenced> refStream = Stream.concat(
      info.getDistributions().stream(),
      Stream.concat(
        info.getMedia().stream(),
        info.getVernacularNames().stream()
      )
    );
    refStream
      .filter(r -> r != null && r.getReferenceId() != null)
      .forEach(r -> refKeys2.add(r.getReferenceId()));

		assertEquals(refKeys1, refKeys2);

    assertEquals(4, info.getDistributions().size());
    for (Distribution d : info.getDistributions()) {
      switch (d.getId()) {
        case 1:
          assertEquals("Berlin", d.getArea().getName());
          assertEquals(Gazetteer.TEXT, d.getArea().getGazetteer());
          assertNull(d.getStatus());
          assertNull(d.getArea().getId());
          assertEquals("ref-1", d.getReferenceId());
          break;
        case 2:
          assertEquals("Leiden", d.getArea().getName());
          assertEquals(Gazetteer.TEXT, d.getArea().getGazetteer());
          assertNull(d.getStatus());
          assertNull(d.getArea().getId());
          assertEquals("ref-1b" ,d.getReferenceId());
          break;
        case 4:
          assertEquals(Country.GERMANY, d.getArea());
          assertEquals(Gazetteer.ISO, d.getArea().getGazetteer());
          assertNull(d.getStatus());
          assertNotNull(d.getArea().getId());
          assertNotNull(d.getArea().getName());
          assertNull(d.getReferenceId());
          break;
        case 5:
          assertEquals(TdwgArea.of("BZE"), d.getArea());
          assertEquals(Gazetteer.TDWG, d.getArea().getGazetteer());
          assertNull(d.getStatus());
          assertEquals("BZE", d.getArea().getId());
          assertNotNull(d.getArea().getName());
          assertNull(d.getReferenceId());
          break;
        default:
          fail("Unexpected distribution");
      }
    }
  }

  @Test
  public void synonyms() {
    try (SqlSession session = session()) {
      SynonymMapper sm = session.getMapper(SynonymMapper.class);
      
      final Taxon acc = TestEntityGenerator.TAXON1;
      final int datasetKey = acc.getDatasetKey();
      
      Synonymy synonymy = tDao.getSynonymy(acc);
      assertTrue(synonymy.isEmpty());
      assertEquals(0, synonymy.size());
      
      // homotypic 1
      Name syn1 = TestEntityGenerator.newName("syn1");
      nDao.create(syn1, user);
      
      // homotypic 2
      Name syn2bas = TestEntityGenerator.newName("syn2bas");
      nDao.create(syn2bas, user);
      
      Name syn21 = TestEntityGenerator.newName("syn2.1");
      syn21.setHomotypicNameId(syn2bas.getId());
      nDao.create(syn21, user);
      
      Name syn22 = TestEntityGenerator.newName("syn2.2");
      syn22.setHomotypicNameId(syn2bas.getId());
      nDao.create(syn22, user);
      
      // homotypic 3
      Name syn3bas = TestEntityGenerator.newName("syn3bas");
      nDao.create(syn3bas, user);
      
      Name syn31 = TestEntityGenerator.newName("syn3.1");
      syn31.setHomotypicNameId(syn3bas.getId());
      nDao.create(syn31, user);
      
      session.commit();
      
      // no synonym links added yet, expect empty synonymy as no homotypic synnym exists
      synonymy = tDao.getSynonymy(acc);
      assertTrue(synonymy.isEmpty());
      assertEquals(0, synonymy.size());
      
      // now add a single synonym relation
      Synonym syn = setUserDate(new Synonym());
      syn.setDatasetKey(datasetKey);
      syn.setId(UUID.randomUUID().toString());
      syn.setStatus(TaxonomicStatus.SYNONYM);
      syn.setOrigin(Origin.SOURCE);
      syn.setName(syn1);
      syn.setParentId(acc.getId());
      sm.create(syn);
      session.commit();
      
      synonymy = tDao.getSynonymy(acc);
      assertFalse(synonymy.isEmpty());
      assertEquals(1, synonymy.size());
      assertEquals(0, synonymy.getMisapplied().size());
      assertEquals(0, synonymy.getHomotypic().size());
  
      sm.create(updSyn(syn, syn2bas));
      sm.create(updSyn(syn, syn3bas));
      syn.setStatus(TaxonomicStatus.MISAPPLIED);
      sm.create(updSyn(syn, syn21));
      session.commit();
      
      // at this stage we have 4 explicit synonym relations
      synonymy = tDao.getSynonymy(acc);
      assertEquals(4, synonymy.size());
      assertEquals(0, synonymy.getHomotypic().size());
      assertEquals(3, synonymy.getHeterotypic().size());
      assertEquals(1, synonymy.getMisapplied().size());
      
      // add the remaining homotypic names as synonyms
      syn.setStatus(TaxonomicStatus.SYNONYM);
      sm.create(newSyn(syn, syn21));
      sm.create(newSyn(syn, syn22));
      sm.create(newSyn(syn, syn31));
      session.commit();
  
      synonymy = tDao.getSynonymy(acc);
      assertEquals(7, synonymy.size());
      assertEquals(0, synonymy.getHomotypic().size());
      // still the same number of heterotypic synonym groups
      assertEquals(3, synonymy.getHeterotypic().size());
      assertEquals(1, synonymy.getMisapplied().size());
      
      synonymy = tDao.getSynonymy(TAXON2);
      assertEquals(2, synonymy.size());
    }
  }
  
  static Synonym updSyn(Synonym syn, Name n) {
    syn.setId(n.getId());
    syn.setName(n);
    return syn;
  }
  
  static Synonym newSyn(Synonym syn, Name n) {
    syn.setId(UUID.randomUUID().toString());
    syn.setName(n);
    return syn;
  }

  @Test
  public void create() {
    final int datasetKey = DATASET11.getKey();
    // parsed
    Name n = new Name();
    n.setUninomial("Abies");
    n.setCombinationAuthorship(Authorship.authors("Miller"));
    n.setRank(Rank.GENUS);
    Taxon t = new Taxon();
    t.setName(n);
    t.setDatasetKey(datasetKey);
    t.setStatus(TaxonomicStatus.ACCEPTED);
    
    DSID<String> id = tDao.create(t, USER_EDITOR.getKey());
    
    Taxon t2 = tDao.get(id);
    assertNotNull(t2.getId());
    assertEquals(USER_EDITOR.getKey(), t2.getCreatedBy());
    assertEquals(USER_EDITOR.getKey(), t2.getModifiedBy());
    assertEquals(USER_EDITOR.getKey(), t2.getName().getCreatedBy());
    assertEquals(USER_EDITOR.getKey(), t2.getName().getModifiedBy());
    assertEquals(Rank.GENUS, t2.getName().getRank());
    assertEquals("Abies", t2.getName().getScientificName());
    assertEquals("Abies", t2.getName().getUninomial());
    assertEquals("Miller", t2.getName().getAuthorship());
    assertEquals(Authorship.authors("Miller"), t2.getName().getCombinationAuthorship());
    assertTrue(t2.getName().getBasionymAuthorship().isEmpty());
    assertNull(t2.getName().getGenus());
    assertNull(t2.getName().getSpecificEpithet());
    assertEquals(t2.getName().getId(), t2.getName().getHomotypicNameId());
    assertNotNull(t2.getName().getId());
    assertEquals(NameType.SCIENTIFIC, t2.getName().getType());


    // unparsed
    n = new Name();
    n.setRank(Rank.SPECIES);
    n.setScientificName("Abies alba");
    n.setAuthorship("Miller 1999");
    t = new Taxon();
    t.setName(n);
    t.setDatasetKey(datasetKey);
    t.setStatus(TaxonomicStatus.ACCEPTED);

    id = tDao.create(t, USER_EDITOR.getKey());

    t2 = tDao.get(id);
    assertNotNull(t2.getId());
    assertEquals(USER_EDITOR.getKey(), t2.getCreatedBy());
    assertEquals(USER_EDITOR.getKey(), t2.getModifiedBy());
    assertEquals(USER_EDITOR.getKey(), t2.getName().getCreatedBy());
    assertEquals(USER_EDITOR.getKey(), t2.getName().getModifiedBy());
    assertEquals(Rank.SPECIES, t2.getName().getRank());
    assertEquals("Abies alba", t2.getName().getScientificName());
    assertEquals("Miller, 1999", t2.getName().getAuthorship());
    assertEquals(Authorship.yearAuthors("1999", "Miller"), t2.getName().getCombinationAuthorship());
    assertTrue(t2.getName().getBasionymAuthorship().isEmpty());
    assertNull(t2.getName().getUninomial());
    assertEquals("Abies", t2.getName().getGenus());
    assertEquals("alba", t2.getName().getSpecificEpithet());
    assertNull(t2.getName().getInfraspecificEpithet());
    assertEquals(t2.getName().getId(), t2.getName().getHomotypicNameId());
    assertNotNull(t2.getName().getId());
    assertEquals(NameType.SCIENTIFIC, t2.getName().getType());
  }
  
  @Test
  public void updateAllSectorCounts(){
    MybatisTestUtils.populateDraftTree(session());
    tDao.updateAllSectorCounts(Datasets.COL);
  }
  
  @Test
  public void updateParentChange(){
    MybatisTestUtils.populateDraftTree(session());
    Taxon t5 = tDao.get(DSID.colID("t5"));
    assertEquals("t3", t5.getParentId());
    t5.setParentId("t4");
    tDao.update(t5, USER_EDITOR.getKey());
  }

  @Test(expected = IllegalArgumentException.class)
  public void updateIllegalParentChange(){
    MybatisTestUtils.populateDraftTree(session());
    Taxon t5 = tDao.get(DSID.colID("t5"));
    assertEquals("t3", t5.getParentId());
    t5.setSectorKey(1);
    tDao.update(t5, USER_EDITOR.getKey());

    t5.setParentId("t4");
    tDao.update(t5, USER_EDITOR.getKey());
  }

  @Test
  public void deleteRecursively() throws Exception {
    final DSIDValue<String> key = DSID.of(TestDataRule.TREE.key, null);
    MybatisTestUtils.populateTestData(TestDataRule.TREE, true);
  
    // create some sectors in the subtree to make sure they also get removed
    SectorMapper sm = mapper(SectorMapper.class);
    Sector s1 = SectorMapperTest.create(key.id("t2"), DSID.of(TestDataRule.TREE.datasetKeys.iterator().next(), "x"));
    sm.create(s1);
    Sector s2 = SectorMapperTest.create(key.id("t4"), DSID.of(TestDataRule.TREE.datasetKeys.iterator().next(), "xy"));
    sm.create(s2);
    Sector s3 = SectorMapperTest.create(key.id("t10"), DSID.of(TestDataRule.TREE.datasetKeys.iterator().next(), "xyz"));
    sm.create(s3);

    commit();

    assertNotNull(tDao.get(key.id("t10")));
    assertNotNull(sm.get(s1));
    assertNotNull(sm.get(s2));
    assertNotNull(sm.get(s3));
    tDao.deleteRecursively(key.id("t4"), USER_EDITOR);
  
    assertNull(tDao.get(key.id("t4")));
    assertNull(tDao.get(key.id("t10")));
    assertNotNull(sm.get(s1));
    assertNull(sm.get(s2));
    assertNull(sm.get(s3));
  }
}
