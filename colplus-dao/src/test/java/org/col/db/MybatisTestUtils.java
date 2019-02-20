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
  
  public static void populateDraftTree(SqlSession session) {
    partition(session, DRAFT_COL);
    
    NameMapper nm = session.getMapper(NameMapper.class);
    
    Name n1 = draftName(nm, "n1", "Animalia", Rank.KINGDOM);
    Name n2 = draftName(nm, "n2", "Arthropoda", Rank.KINGDOM);
    Name n3 = draftName(nm, "n3", "Insecta", Rank.CLASS);
    Name n4 = draftName(nm, "n4", "Coleoptera", Rank.ORDER);
    Name n5 = draftName(nm, "n5", "Lepidoptera", Rank.ORDER);
    
    TaxonMapper tm = session.getMapper(TaxonMapper.class);
    Taxon t1 = draftTaxon(tm, "t1", n1, null);
    Taxon t2 = draftTaxon(tm, "t2", n2, t1);
    Taxon t3 = draftTaxon(tm, "t3", n3, t2);
    Taxon t4 = draftTaxon(tm, "t4", n4, t3);
    Taxon t5 = draftTaxon(tm, "t5", n5, t3);
  
    session.commit();
  }
  
  private static Name draftName(NameMapper nm, String id, String name, Rank rank) {
    Name n = TestEntityGenerator.newName(DRAFT_COL, id, name, rank);
    nm.create(n);
    return n;
  }
  
  private static Taxon draftTaxon(TaxonMapper tm, String id, Name n, Taxon parent) {
    Taxon t = TestEntityGenerator.newTaxon(DRAFT_COL, id);
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
