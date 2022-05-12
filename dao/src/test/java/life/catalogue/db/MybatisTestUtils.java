package life.catalogue.db;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Origin;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.TaxonMapper;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.sql.SQLException;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static life.catalogue.api.vocab.Datasets.COL;

public class MybatisTestUtils {
  private static final Logger LOG = LoggerFactory.getLogger(MybatisTestUtils.class);

  public static void partition(SqlSession session, int datasetKey) {
    DatasetOrigin origin = DatasetInfoCache.CACHE.info(datasetKey).origin;
    Partitioner.partition(session, datasetKey, origin);
    Partitioner.attach(session, datasetKey, origin);
  }

  /**
   * t1
   *   t2
   *     t3
   *       t4
   *       t5
   * @param datasetKey
   * @param session
   */
  public static void populateTestTree(int datasetKey, SqlSession session) {
    partition(session, datasetKey);
    
    NameMapper nm = session.getMapper(NameMapper.class);
  
    Name n1 = uninomial(nm, datasetKey,"n1", "Animalia", Rank.KINGDOM);
    Name n2 = uninomial(nm, datasetKey,"n2", "Arthropoda", Rank.PHYLUM);
    Name n3 = uninomial(nm, datasetKey,"n3", "Insecta", Rank.CLASS);
    Name n4 = uninomial(nm, datasetKey,"n4", "Coleoptera", Rank.ORDER);
    Name n5 = uninomial(nm, datasetKey,"n5", "Lepidoptera", Rank.ORDER);
  
    TaxonMapper tm = session.getMapper(TaxonMapper.class);
    Taxon t1 = draftTaxon(tm,"t1", n1, null);
    Taxon t2 = draftTaxon(tm,"t2", n2, t1);
    Taxon t3 = draftTaxon(tm,"t3", n3, t2);
    Taxon t4 = draftTaxon(tm,"t4", n4, t3);
    Taxon t5 = draftTaxon(tm,"t5", n5, t3);
    
    session.commit();
  }

  public static void createManagedSequences(SqlSession session, int datasetKey) {
    session.getMapper(DatasetPartitionMapper.class).createManagedSequences(datasetKey);
  }

  public static Dataset createDataset(SqlSessionFactory factor, int key, DatasetOrigin origin) {
    try (SqlSession session = factor.openSession(true)) {
      return createDataset(session, key, origin);
    }
  }

  public static Dataset createDataset(SqlSession session, int key, DatasetOrigin origin) {
    Dataset d = TestEntityGenerator.newDataset("test dataset "+key);
    d.setOrigin(origin);
    d.setKey(key);
    d.applyUser(TestEntityGenerator.USER_USER);
    session.getMapper(DatasetMapper.class).create(d);
    partition(session, key);
    session.getMapper(DatasetPartitionMapper.class).createManagedSequences(key);
    return d;
  }

  public static void populateDraftTree(SqlSession session) {
    populateTestTree(COL, session);
  
    TaxonMapper tm = session.getMapper(TaxonMapper.class);
    tm.incDatasetSectorCount(DSID.colID("t4"), 11, 1);
    tm.incDatasetSectorCount(DSID.colID("t5"), 11, 1);
    session.commit();
  }
  
  public static void populateTestData(TestDataRule.TestData data, boolean skipGlobalTables) throws IOException, SQLException {
    TestDataRule rule = new TestDataRule(data);
    rule.initSession();
    try {
      if (!skipGlobalTables) {
        rule.loadGlobalData();
      }
      rule.partition();
      rule.loadData();
    } finally {
      rule.getSqlSession().close();
    }
  }
  
  private static Name uninomial(NameMapper nm, int datasetKey, String id, String name, Rank rank) {
    Name n = new Name();
    n.applyUser(TestDataRule.TEST_USER);
    n.setId(id);
    n.setDatasetKey(datasetKey);
    n.setUninomial(name);
    n.setRank(rank);
    n.setOrigin(Origin.SOURCE);
    n.setType(NameType.SCIENTIFIC);
    n.rebuildScientificName();
    nm.create(n);
    return n;
  }
  
  private static Taxon draftTaxon(TaxonMapper tm, String id, Name n, Taxon parent) {
    Taxon t = TestEntityGenerator.newTaxon(n, id, parent==null ? null : parent.getId());
    t.setAccordingToId(null);
    tm.create(t);
    return t;
  }
}
