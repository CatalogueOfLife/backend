package org.col.db.mapper;

import com.google.common.base.Splitter;
import org.col.api.RandomUtils;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Dataset;
import org.col.api.model.Name;
import org.col.api.model.Synonym;
import org.col.api.model.Taxon;
import org.col.api.vocab.TaxonomicStatus;
import org.col.db.dao.NameDao;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 *
 */
public class SynonymMapperTest extends MapperTestBase<SynonymMapper> {

  private static final Splitter SPACE_SPLITTER = Splitter.on(" ").trimResults();

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
  public void roundtrip() throws Exception {
    Name n = TestEntityGenerator.newName();
    nameDao.create(n);

    Name an = TestEntityGenerator.newName();
    nameDao.create(an);
    Taxon t = TestEntityGenerator.newTaxon(an.getDatasetKey(), RandomUtils.randomString(25));
    t.setName(an);
    taxonMapper.create(t);

    Synonym s1 = TestEntityGenerator.newSynonym(TaxonomicStatus.SYNONYM, n, t);
    synonymMapper.create(n.getDatasetKey(), s1.getName().getKey(), s1.getAccepted().getKey(), s1.getStatus(), s1.getAccordingTo());
    commit();

    List<Synonym> syns = synonymMapper.listByName(s1.getName().getKey());
    assertEquals(1, syns.size());
    Synonym s2 = syns.get(0);

    assertEquals(s1, s2);
  }

  @Test
  public void synonyms() throws Exception {
    final int accKey = TestEntityGenerator.TAXON1.getKey();
    final int datasetKey = TestEntityGenerator.TAXON1.getDatasetKey();

    List<Synonym> synonyms = synonymMapper.listByTaxon(accKey);
    assertTrue(synonyms.isEmpty());
    assertEquals(0, synonyms.size());

    // homotypic 1
    Name syn1 = TestEntityGenerator.newName("syn1");
    nameDao.create(syn1);

    // homotypic 2
    Name syn2bas = TestEntityGenerator.newName("syn2bas");
    nameDao.create(syn2bas);

    Name syn21 = TestEntityGenerator.newName("syn2.1");
    syn21.setHomotypicNameKey(syn2bas.getKey());
    nameDao.create(syn21);

    Name syn22 = TestEntityGenerator.newName("syn2.2");
    syn22.setHomotypicNameKey(syn2bas.getKey());
    nameDao.create(syn22);

    // homotypic 3
    Name syn3bas = TestEntityGenerator.newName("syn3bas");
    nameDao.create(syn3bas);

    Name syn31 = TestEntityGenerator.newName("syn3.1");
    syn31.setHomotypicNameKey(syn3bas.getKey());
    nameDao.create(syn31);

    commit();

    // no synonym links added yet, expect empty synonymy even though basionym links
    // exist!
    synonyms = synonymMapper.listByTaxon(accKey);
    assertTrue(synonyms.isEmpty());
    assertEquals(0, synonyms.size());

    // now add a few listByTaxon
    synonymMapper.create(datasetKey,syn1.getKey(), accKey, TaxonomicStatus.SYNONYM, null);
    commit();
    synonyms = synonymMapper.listByTaxon(accKey);
    assertFalse(synonyms.isEmpty());
    assertEquals(1, synonyms.size());

    synonymMapper.create(datasetKey, syn2bas.getKey(), accKey, TaxonomicStatus.SYNONYM, null);
    synonymMapper.create(datasetKey, syn21.getKey(), accKey, TaxonomicStatus.SYNONYM, null);
    synonymMapper.create(datasetKey, syn22.getKey(), accKey, TaxonomicStatus.SYNONYM, null);
    synonymMapper.create(datasetKey, syn3bas.getKey(), accKey, TaxonomicStatus.SYNONYM, null);
    synonymMapper.create(datasetKey, syn31.getKey(), accKey, TaxonomicStatus.SYNONYM, null);

    synonyms = synonymMapper.listByTaxon(accKey);
    assertEquals(6, synonyms.size());
    assertEquals(2, synonymMapper.listByTaxon(TestEntityGenerator.TAXON2.getKey()).size());


    // now also add a misapplied name with the same name
    synonymMapper.create(datasetKey, syn21.getKey(), TestEntityGenerator.TAXON2.getKey(), TaxonomicStatus.MISAPPLIED, "auct. Döring");
    commit();

    assertEquals(6, synonymMapper.listByTaxon(accKey).size());
    assertEquals(3, synonymMapper.listByTaxon(TestEntityGenerator.TAXON2.getKey()).size());
  }

}
