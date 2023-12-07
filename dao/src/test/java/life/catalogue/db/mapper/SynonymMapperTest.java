package life.catalogue.db.mapper;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.Synonym;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.dao.NameDao;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameIndexFactory;

import java.util.List;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SynonymMapperTest extends CRUDDatasetScopedStringTestBase<Synonym, SynonymMapper> {
  
  private NameDao nameDao;
  private SynonymMapper synonymMapper;
  private TaxonMapper taxonMapper;
  private static int user = TestEntityGenerator.USER_EDITOR.getKey();
  
  private static final int datasetKey = TestEntityGenerator.TAXON1.getDatasetKey();
  
  Taxon tax;
  
  public SynonymMapperTest() {
    super(SynonymMapper.class);
  }
  
  @Before
  public void initMappers() {
    nameDao = new NameDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.passThru(), validator);
    synonymMapper = testDataRule.getMapper(SynonymMapper.class);
    taxonMapper = testDataRule.getMapper(TaxonMapper.class);
    // prepare taxon to hook extensions to
    tax = TestEntityGenerator.TAXON1;
  }
  
  @Override
  Synonym createTestEntity(int dkey) {
    if (dkey != tax.getDatasetKey()) {
      // create a new taxon
      tax = TestEntityGenerator.newTaxon(dkey);
      insertTaxon(tax);
    }
    
    Name n = TestEntityGenerator.newName(tax.getDatasetKey());
    insertName(n);
  
    Synonym syn = TestEntityGenerator.newSynonym(n, tax.getId());
    return syn;
  }
  
  @Override
  Synonym removeDbCreatedProps(Synonym obj) {
    TestEntityGenerator.nullifyUserDate(obj);
    obj.setAccepted(null);
    return obj;
  }
  
  @Override
  void updateTestObj(Synonym obj) {
    obj.setOrigin(Origin.IMPLICIT_NAME);
    obj.setRemarks("traralala");
  }
  
  @Test
  public void roundtrip2() {
    Name n = TestEntityGenerator.newName();
    nameDao.create(n, user); // this does store a None match!

    Name an = TestEntityGenerator.newName();
    nameDao.create(an, user);
    Taxon t = TestEntityGenerator.newTaxon(an.getDatasetKey(), RandomUtils.randomLatinString(25));
    t.setName(an);
    taxonMapper.create(t);
    
    Synonym s1 = TestEntityGenerator.newSynonym(TaxonomicStatus.SYNONYM, n, t.getId());
    s1.setVerbatimKey(1);
    synonymMapper.create(s1);
    commit();
    
    Synonym s2 = synonymMapper.get(s1);
    assertNotNull(s2);
    
    // remove child count for comparison
    s2.setAccepted(null);
    removeDbCreatedProps(s1, s2);
    //printDiff(s1, s2);
    assertEquals(s1, s2);
  }
  
  
  @Test
  public void synonyms() throws Exception {
    
    List<Synonym> synonyms = synonymMapper.listByTaxon(tax);
    assertTrue(synonyms.isEmpty());
    assertEquals(0, synonyms.size());

    // homotypic 1
    Name syn1 = TestEntityGenerator.newName("syn1");
    nameDao.create(syn1, user);
  
    // homotypic 2
    Name syn2bas = TestEntityGenerator.newName("syn2bas");
    nameDao.create(syn2bas, user);
  
    Name syn21 = TestEntityGenerator.newName("syn2.1");
//    syn21.setHomotypicNameId(syn2bas.getId());
    nameDao.create(syn21, user);
  
    Name syn22 = TestEntityGenerator.newName("syn2.2");
//    syn22.setHomotypicNameId(syn2bas.getId());
    nameDao.create(syn22, user);
  
    // homotypic 3
    Name syn3bas = TestEntityGenerator.newName("syn3bas");
    nameDao.create(syn3bas, user);
  
    Name syn31 = TestEntityGenerator.newName("syn3.1");
//    syn31.setHomotypicNameId(syn3bas.getId());
    nameDao.create(syn31, user);
    commit();
    
    // no synonym links added yet, expect empty synonymy even though basionym links
    // exist!
    synonyms = synonymMapper.listByTaxon(tax);
    assertTrue(synonyms.isEmpty());
    assertEquals(0, synonyms.size());
    
    // now add a few synonyms
    Synonym syn = TestEntityGenerator.newSynonym(syn1, tax.getId());
    synonymMapper.create(syn);
    commit();
    synonyms = synonymMapper.listByTaxon(tax);
    assertEquals(1, synonyms.size());
    
    synonymMapper.create(newSyn(syn, syn2bas, tax.getId()));
    synonymMapper.create(newSyn(syn, syn21, tax.getId()));
    synonymMapper.create(newSyn(syn, syn22, tax.getId()));
    synonymMapper.create(newSyn(syn, syn3bas, tax.getId()));
    synonymMapper.create(newSyn(syn, syn31, tax.getId()));
    
    synonyms = synonymMapper.listByTaxon(tax);
    assertEquals(6, synonyms.size());
    assertEquals(2, synonymMapper.listByTaxon(TestEntityGenerator.TAXON2).size());
    
    
    // now also add a misapplied name with the same name
    syn.setId(UUID.randomUUID().toString());
    syn.setParentId(TestEntityGenerator.TAXON2.getId());
    syn.setStatus(TaxonomicStatus.MISAPPLIED);
    syn.setNamePhrase("auct. Döring");
    synonymMapper.create(syn);
    commit();
    
    assertEquals(6, synonymMapper.listByTaxon(tax).size());
    assertEquals(3, synonymMapper.listByTaxon(TestEntityGenerator.TAXON2).size());
  }
  
  static Synonym newSyn(Synonym syn, Name n, String accKey) {
    syn.setId(n.getId());
    syn.setParentId(accKey);
    syn.setName(n);
    return syn;
  }
  
}
