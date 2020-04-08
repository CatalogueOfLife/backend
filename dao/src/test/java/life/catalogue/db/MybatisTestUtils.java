package life.catalogue.db;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.vocab.Origin;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.TaxonMapper;
import org.apache.ibatis.session.SqlSession;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.sql.SQLException;

import static life.catalogue.api.vocab.Datasets.DRAFT_COL;

public class MybatisTestUtils {
  
  public static void partition(SqlSession session, int datasetKey) {
    final DatasetPartitionMapper pm = session.getMapper(DatasetPartitionMapper.class);
    pm.delete(datasetKey);
    pm.create(datasetKey);
    pm.attach(datasetKey);
    session.commit();
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
    Name n2 = uninomial(nm, datasetKey,"n2", "Arthropoda", Rank.KINGDOM);
    Name n3 = uninomial(nm, datasetKey,"n3", "Insecta", Rank.CLASS);
    Name n4 = uninomial(nm, datasetKey,"n4", "Coleoptera", Rank.ORDER);
    Name n5 = uninomial(nm, datasetKey,"n5", "Lepidoptera", Rank.ORDER);
  
    TaxonMapper tm = session.getMapper(TaxonMapper.class);
    Taxon t1 = draftTaxon(tm, datasetKey,"t1", n1, null);
    Taxon t2 = draftTaxon(tm, datasetKey,"t2", n2, t1);
    Taxon t3 = draftTaxon(tm, datasetKey,"t3", n3, t2);
    Taxon t4 = draftTaxon(tm, datasetKey,"t4", n4, t3);
    Taxon t5 = draftTaxon(tm, datasetKey,"t5", n5, t3);
    
    session.commit();
  }
  
  public static void populateDraftTree(SqlSession session) {
    populateTestTree(DRAFT_COL, session);
  
    TaxonMapper tm = session.getMapper(TaxonMapper.class);
    tm.incDatasetSectorCount(DSID.draftID("t4"), 11, 1);
    tm.incDatasetSectorCount(DSID.draftID("t5"), 11, 1);
    session.commit();
  }
  
  public static void populateTestData(TestDataRule.TestData data, boolean skipGlobalTables) throws IOException, SQLException {
    try (TestDataRule rule = new TestDataRule(data)) {
      rule.initSession();
      rule.partition();
      rule.loadData(skipGlobalTables);
    }
  }
  
  private static Name uninomial(NameMapper nm, int datasetKey, String id, String name, Rank rank) {
    Name n = new Name();
    n.applyUser(TestDataRule.TEST_USER);
    n.setId(id);
    n.setNameIndexId(RandomUtils.randomLatinString(10));
    n.setHomotypicNameId(id);
    n.setDatasetKey(datasetKey);
    n.setUninomial(name);
    n.setRank(rank);
    n.setOrigin(Origin.SOURCE);
    n.setType(NameType.SCIENTIFIC);
    n.updateNameCache();
    nm.create(n);
    return n;
  }
  
  private static Taxon draftTaxon(TaxonMapper tm, int datasetKey, String id, Name n, Taxon parent) {
    Taxon t = TestEntityGenerator.newTaxon(n, id, parent==null ? null : parent.getId());
    tm.create(t);
    return t;
  }
}
