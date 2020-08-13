package life.catalogue.db.mapper;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.NameDao;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.es.NameUsageIndexService;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static life.catalogue.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.assertEquals;

public class NameUsageMapperTest extends MapperTestBase<NameUsageMapper> {
  
  TaxonMapper tm;
  SynonymMapper sm;
  NameMapper nm;
  int idGen = 1;
  int datasetKey = TestDataRule.TestData.APPLE.key;

  public NameUsageMapperTest() {
    super(NameUsageMapper.class);
  }
  
  @Before
  public void init() {
    tm = mapper(TaxonMapper.class);
    sm = mapper(SynonymMapper.class);
    nm = mapper(NameMapper.class);
  }

  @Test
  public void copyDataset() throws Exception {
    Partitioner.partition(PgSetupRule.getSqlSessionFactory(), 999);
    mapper().copyDataset(datasetKey, 999);
  }

  @Test
  public void getSimple() throws Exception {
    Taxon t = TestEntityGenerator.TAXON1;
    SimpleName sn = mapper().getSimple(t);
    assertEquals(t.getId(), sn.getId());
    assertEquals(t.getParentId(), sn.getParent());
    assertEquals(t.getStatus(), sn.getStatus());
    assertEquals(t.getName().getRank(), sn.getRank());
    assertEquals(t.getName().getScientificName(), sn.getName());
    assertEquals(t.getName().getAuthorship(), sn.getAuthorship());
  }

  @Test
  public void list() throws Exception {
    NameDao nameDao = new NameDao(PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru());
  
    List<Taxon> taxa = new ArrayList<>();
    taxa.add(TestEntityGenerator.newTaxon("t1"));
    taxa.add(TestEntityGenerator.newTaxon("t2"));
    taxa.add(TestEntityGenerator.newTaxon("t3"));
    taxa.add(TestEntityGenerator.newTaxon("t4"));
    taxa.add(TestEntityGenerator.newTaxon("t5"));
    taxa.add(TestEntityGenerator.newTaxon("t6"));
    taxa.add(TestEntityGenerator.newTaxon("t7"));
    taxa.add(TestEntityGenerator.newTaxon("t8"));
    taxa.add(TestEntityGenerator.newTaxon("t9"));
    for (Taxon t : taxa) {
      tm.create(t);
    }
  
    List<Synonym> syns = new ArrayList<>();
    syns.add(TestEntityGenerator.newSynonym(taxa.get(2)));
    syns.add(TestEntityGenerator.newSynonym(taxa.get(2)));
    syns.add(TestEntityGenerator.newSynonym(taxa.get(5)));
    for (Synonym s : syns) {
      nameDao.create(s.getName(), Users.TESTER);
      sm.create(s);
    }
    commit();
    
    // get first page
    Page p = new Page(0, 3);
    
    List<NameUsageBase> res = mapper().list(DATASET11.getKey(), p);
    assertEquals(3, res.size());
    // First 2 taxa in dataset 11 are pre-inserted taxa
    // next 2 are preinserted synonyms
    // then our 3 created syns
    // finally 9 new taxa
    assertIdClassEquals(TestEntityGenerator.TAXON1, res.get(0));
    assertIdClassEquals(TestEntityGenerator.TAXON2, res.get(1));
    assertIdClassEquals(TestEntityGenerator.SYN1, res.get(2));

    p.next();
    res = mapper().list(DATASET11.getKey(), p);
    assertEquals(3, res.size());
    assertIdClassEquals(TestEntityGenerator.SYN2, res.get(0));
    assertIdClassEquals(syns.get(0), res.get(1));
    assertIdClassEquals(syns.get(1), res.get(2));
  
    p.next();
    res = mapper().list(DATASET11.getKey(), p);
    assertEquals(3, res.size());
    assertIdClassEquals(syns.get(2), res.get(0));
    assertIdClassEquals(taxa.get(0), res.get(1));
    assertIdClassEquals(taxa.get(1), res.get(2));
  }


  private Name createName(Rank rank, DSID<Integer> sectorKey) {
    Name n = TestEntityGenerator.newName(sectorKey.getDatasetKey(), "n-" + idGen++,
      rank.isSpeciesOrBelow() ? RandomUtils.randomSpecies() : RandomUtils.randomGenus(), rank);
    n.setSectorKey(sectorKey.getId());
    nm.create(n);
    return n;
  }

  private Taxon createTaxon(Rank rank, String parentID, DSID<Integer> sectorKey){
    Name n = createName(rank, sectorKey);
    Taxon t = TestEntityGenerator.newTaxon(n, "t-"+idGen++, parentID);
    t.setSectorKey(sectorKey.getId());
    t.setDatasetKey(sectorKey.getDatasetKey());
    tm.create(t);
    return t;
  }

  private Synonym createSynonym(Taxon acc, Rank rank){
    DSID<Integer> secKey = DSID.of(acc.getDatasetKey(), acc.getSectorKey());
    Name n = createName(rank, secKey);
    Synonym s = TestEntityGenerator.newSynonym(n, acc.getId());
    s.setSectorKey(acc.getSectorKey());
    sm.create(s);
    return s;
  }

  @Test
  public void deleteUsages() throws Exception {
    // attach taxa with sector to
    final DSID<Integer> sectorKey = DSID.of(datasetKey, 1);
    for (int sp = 1; sp < 10; sp++) {
      Taxon species = createTaxon(Rank.SPECIES, "root-1", sectorKey);
      createTaxon(Rank.UNRANKED, species.getId(), sectorKey);
      createTaxon(Rank.VARIETY, species.getId(), sectorKey);
      createSynonym(species, Rank.SPECIES_AGGREGATE);
      for (int ssp = 1; ssp < 5; ssp++) {
        Taxon subspecies = createTaxon(Rank.SUBSPECIES, species.getId(), sectorKey);
        createTaxon(Rank.UNRANKED, subspecies.getId(), sectorKey);
        for (int var = 1; var < 3; var++) {
          Taxon variety = createTaxon(Rank.VARIETY, species.getId(), sectorKey);
          createTaxon(Rank.CHEMOFORM, variety.getId(), sectorKey);
        }
        createTaxon(Rank.VARIETY, subspecies.getId(), sectorKey);
        createSynonym(subspecies, Rank.UNRANKED);
        for (int syn = 1; syn < 5; syn++) {
          createSynonym(subspecies, Rank.VARIETY);
        }
      }
    }
    commit();

    final AtomicInteger count = new AtomicInteger(0);
    mapper().processSector(sectorKey).forEach(n -> count.incrementAndGet());
    assertEquals(468, count.get());

    // delete
    int dels = mapper().deleteBySectorAndRank(sectorKey, Rank.SUBSPECIES);
    assertEquals(450, dels);
    count.set(0);
    mapper().processSector(sectorKey).forEach(n -> count.incrementAndGet());
    assertEquals(18, count.get());
  }

  void assertIdClassEquals(NameUsageBase o1, NameUsageBase o2) {
    assertEquals(o1.getId(), o2.getId());
    assertEquals(o1.getClass(), o2.getClass());
  }
}