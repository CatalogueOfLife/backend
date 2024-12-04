package life.catalogue.dao;

import life.catalogue.api.BeanPrinter;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.junit.MybatisTestUtils;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.SectorMapperTest;
import life.catalogue.db.mapper.SynonymMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.nidx.NameIndexFactory;

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

public class TaxonDaoIT extends DaoTestBase {
  static int user = TestEntityGenerator.USER_EDITOR.getKey();
  NameDao nDao;
  TaxonDao tDao;

  public TaxonDaoIT() {
    nDao = new NameDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.passThru(), validator);
    tDao = new TaxonDao(SqlSessionFactoryRule.getSqlSessionFactory(), nDao, NameUsageIndexService.passThru(), validator);
    SectorDao sdao = new SectorDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), tDao, validator);
    tDao.setSectorDao(sdao);
  }

  @Test
  public void testInfo() {
    final int datasetKey = DATASET11.getKey();
    UsageInfo info = tDao.getUsageInfo(DSID.of(datasetKey, TAXON1.getId()));
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
          assertEquals(new AreaImpl(Country.GERMANY), d.getArea());
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

    // create relations and check them
    var rm = mapper(NameRelationMapper.class);
    var nr = new NameRelation();
    nr.applyUser(TAXON1.getCreatedBy());
    nr.setDatasetKey(TAXON1.getDatasetKey());
    nr.setNameId(TAXON1.getName().getId());
    nr.setRelatedNameId(TAXON2.getName().getId());
    nr.setType(NomRelType.REPLACEMENT_NAME);
    rm.create(nr);
    commit();

    info = tDao.getUsageInfo(DSID.of(datasetKey, TAXON1.getId()));
    for (var rel : info.getNameRelations()) {
      assertNotNull(rel.getType());
      assertNotNull(rel.getNameId());
      assertNotNull(rel.getRelatedNameId());
      assertNotNull(rel.getUsageId());
      assertNotNull(rel.getRelatedUsageId());
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
      // test cycles
      nDao.createRelation(syn2bas, NomRelType.HOMOTYPIC, syn2bas, user);
      nDao.createRelation(syn2bas, NomRelType.BASIONYM, syn2bas, user);

      Name syn21 = TestEntityGenerator.newName("syn2.1");
      nDao.create(syn21, user);
      nDao.createRelation(syn2bas, NomRelType.BASIONYM, syn21, user);

      Name syn22 = TestEntityGenerator.newName("syn2.2");
      nDao.create(syn22, user);
      nDao.createRelation(syn2bas, NomRelType.BASIONYM, syn22, user);

      // homotypic 3
      Name syn3bas = TestEntityGenerator.newName("syn3bas");
      nDao.create(syn3bas, user);
      
      Name syn31 = TestEntityGenerator.newName("syn3.1");
      nDao.create(syn31, user);
      nDao.createRelation(syn3bas, NomRelType.BASIONYM, syn31, user);

      session.commit();
      
      // no synonym links added yet, expect empty synonymy as no homotypic synonym exists
      synonymy = tDao.getSynonymy(acc);
      assertTrue(synonymy.isEmpty());
      assertEquals(0, synonymy.size());
      
      // now add a single synonym relation to lone group syn1
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
  
      // now also add synonyms for the other 2 groups
      sm.create(updSyn(syn, syn2bas));
      sm.create(updSyn(syn, syn3bas));
      session.commit();

      // at this stage we have 3 explicit synonym relations linking to 3 more names in their homotypic group by name relations alone
      synonymy = tDao.getSynonymy(acc);
      assertEquals(3, synonymy.size());
      assertEquals(0, synonymy.getHomotypic().size());
      assertEquals(3, synonymy.getHeterotypic().size());
      assertEquals(0, synonymy.getMisapplied().size());

      // now add 1 misapplied name
      syn.setStatus(TaxonomicStatus.MISAPPLIED);
      sm.create(updSyn(syn, syn21));
      session.commit();

      synonymy = tDao.getSynonymy(acc);
      assertEquals(4, synonymy.size());
      assertEquals(0, synonymy.getHomotypic().size());
      assertEquals(3, synonymy.getHeterotypic().size());
      assertEquals(1, synonymy.getMisapplied().size());

      // add the remaining homotypic names also as synonyms to make sure this doesn't matter
      syn.setStatus(TaxonomicStatus.SYNONYM);
      sm.create(newSyn(syn, syn21));
      sm.create(newSyn(syn, syn22));
      sm.create(newSyn(syn, syn31));
      session.commit();
  
      synonymy = tDao.getSynonymy(acc);
      assertEquals(7, synonymy.size());
      assertEquals(0, synonymy.getHomotypic().size());
      assertEquals(6, synonymy.getHeterotypic().size());
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
    assertNotNull(t2.getName().getId());
    assertEquals(NameType.SCIENTIFIC, t2.getName().getType());
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

    var s1 = SectorMapperTest.create(t5, t5);
    mapper(SectorMapper.class).create(s1);
    commit();

    t5.setSectorKey(s1.getId());
    tDao.update(t5, USER_EDITOR.getKey());

    t5.setParentId("t4");
    tDao.update(t5, USER_EDITOR.getKey());
  }

  @Test
  public void deleteRecursively() throws Exception {
    MybatisTestUtils.replaceTestData(TestDataRule.TREE);
    final DSIDValue<String> key = DSID.of(TestDataRule.TREE.key, null);

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
    tDao.deleteRecursively(key.id("t4"), false, USER_EDITOR);
  
    assertNull(tDao.get(key.id("t4")));
    assertNull(tDao.get(key.id("t10")));
    assertNotNull(sm.get(s1));
    assertNull(sm.get(s2));
    assertNull(sm.get(s3));
  }
}
