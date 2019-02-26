package org.col.db;

import org.apache.ibatis.session.SqlSession;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Name;
import org.col.api.model.Taxon;
import org.col.db.mapper.DatasetPartitionMapper;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.TaxonMapper;
import org.gbif.nameparser.api.Rank;

import static org.col.api.vocab.Datasets.DRAFT_COL;

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
  
    Name n1 = draftName(nm, datasetKey,"n1", "Animalia", Rank.KINGDOM);
    Name n2 = draftName(nm, datasetKey,"n2", "Arthropoda", Rank.KINGDOM);
    Name n3 = draftName(nm, datasetKey,"n3", "Insecta", Rank.CLASS);
    Name n4 = draftName(nm, datasetKey,"n4", "Coleoptera", Rank.ORDER);
    Name n5 = draftName(nm, datasetKey,"n5", "Lepidoptera", Rank.ORDER);
  
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
  }
  
  private static Name draftName(NameMapper nm, int datasetKey, String id, String name, Rank rank) {
    Name n = TestEntityGenerator.newName(datasetKey, id, name, rank);
    nm.create(n);
    return n;
  }
  
  private static Taxon draftTaxon(TaxonMapper tm, int datasetKey, String id, Name n, Taxon parent) {
    Taxon t = TestEntityGenerator.newTaxon(datasetKey, id);
    t.setName(n);
    if (parent == null) {
      t.setParentId(null);
    } else {
      t.setParentId(parent.getId());
    }
    tm.create(t);
    return t;
  }
}
