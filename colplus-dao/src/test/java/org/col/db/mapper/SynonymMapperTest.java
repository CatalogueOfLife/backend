package org.col.db.mapper;

import java.util.List;

import org.col.api.RandomUtils;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Name;
import org.col.api.model.Synonym;
import org.col.api.model.Taxon;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;
import org.col.db.dao.NameDao;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 */
public class SynonymMapperTest extends MapperTestBase<SynonymMapper> {
  
  private NameDao nameDao;
  private SynonymMapper synonymMapper;
  private TaxonMapper taxonMapper;
  
  public SynonymMapperTest() {
    super(SynonymMapper.class);
  }
  
  @Before
  public void initMappers() {
    nameDao = new NameDao(initMybatisRule.getSqlSession());
    synonymMapper = initMybatisRule.getMapper(SynonymMapper.class);
    taxonMapper = initMybatisRule.getMapper(TaxonMapper.class);
  }

  @Test
  public void roundtrip() {
    Name n = TestEntityGenerator.newName();
    nameDao.create(n);
    
    Name an = TestEntityGenerator.newName();
    nameDao.create(an);
    Taxon t = TestEntityGenerator.newTaxon(an.getDatasetKey(), RandomUtils.randomString(25));
    t.setName(an);
    taxonMapper.create(t);
    
    Synonym s1 = TestEntityGenerator.newSynonym(TaxonomicStatus.SYNONYM, n, t);
    s1.setVerbatimKey(1);
    synonymMapper.create(n.getDatasetKey(), s1.getName().getId(), s1.getAccepted().getId(), s1);
    commit();
    
    List<Synonym> syns = synonymMapper.listByName(s1.getName().getDatasetKey(), s1.getName().getId());
    assertEquals(1, syns.size());
    Synonym s2 = syns.get(0);
    
    // remove child count for comparison
    t.setChildCount(null);
    TestEntityGenerator.nullifyUserDate(s1);
    TestEntityGenerator.nullifyUserDate(s2);
    printDiff(s1, s2);
    assertEquals(s1, s2);
  }
  
  @Test
  public void synonyms() throws Exception {
    final String accKey = TestEntityGenerator.TAXON1.getId();
    final int datasetKey = TestEntityGenerator.TAXON1.getDatasetKey();
    
    List<Synonym> synonyms = synonymMapper.listByTaxon(datasetKey, accKey);
    assertTrue(synonyms.isEmpty());
    assertEquals(0, synonyms.size());
    
    // homotypic 1
    Name syn1 = TestEntityGenerator.newName("syn1");
    nameDao.create(syn1);
    
    // homotypic 2
    Name syn2bas = TestEntityGenerator.newName("syn2bas");
    nameDao.create(syn2bas);
    
    Name syn21 = TestEntityGenerator.newName("syn2.1");
    syn21.setHomotypicNameId(syn2bas.getId());
    nameDao.create(syn21);
    
    Name syn22 = TestEntityGenerator.newName("syn2.2");
    syn22.setHomotypicNameId(syn2bas.getId());
    nameDao.create(syn22);
    
    // homotypic 3
    Name syn3bas = TestEntityGenerator.newName("syn3bas");
    nameDao.create(syn3bas);
    
    Name syn31 = TestEntityGenerator.newName("syn3.1");
    syn31.setHomotypicNameId(syn3bas.getId());
    nameDao.create(syn31);
    
    commit();
    
    // no synonym links added yet, expect empty synonymy even though basionym links
    // exist!
    synonyms = synonymMapper.listByTaxon(datasetKey, accKey);
    assertTrue(synonyms.isEmpty());
    assertEquals(0, synonyms.size());
    
    // now add a few synonyms
    Synonym syn = TestEntityGenerator.setUserDate(new Synonym());
    syn.setOrigin(Origin.SOURCE);
    syn.setStatus(TaxonomicStatus.SYNONYM);
    synonymMapper.create(datasetKey, syn1.getId(), accKey, syn);
    commit();
    synonyms = synonymMapper.listByTaxon(datasetKey, accKey);
    assertFalse(synonyms.isEmpty());
    assertEquals(1, synonyms.size());
    
    synonymMapper.create(datasetKey, syn2bas.getId(), accKey, syn);
    synonymMapper.create(datasetKey, syn21.getId(), accKey, syn);
    synonymMapper.create(datasetKey, syn22.getId(), accKey, syn);
    synonymMapper.create(datasetKey, syn3bas.getId(), accKey, syn);
    synonymMapper.create(datasetKey, syn31.getId(), accKey, syn);
    
    synonyms = synonymMapper.listByTaxon(datasetKey, accKey);
    assertEquals(6, synonyms.size());
    assertEquals(2, synonymMapper.listByTaxon(datasetKey, TestEntityGenerator.TAXON2.getId()).size());
    
    
    // now also add a misapplied name with the same name
    syn.setStatus(TaxonomicStatus.MISAPPLIED);
    syn.setAccordingTo("auct. Döring");
    synonymMapper.create(datasetKey, syn21.getId(), TestEntityGenerator.TAXON2.getId(), syn);
    commit();
    
    assertEquals(6, synonymMapper.listByTaxon(datasetKey, accKey).size());
    assertEquals(3, synonymMapper.listByTaxon(datasetKey, TestEntityGenerator.TAXON2.getId()).size());
  }
  
}
