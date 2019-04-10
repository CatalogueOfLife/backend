package org.col.db.mapper;

import java.sql.Connection;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Duplicate;
import org.col.api.model.Page;
import org.col.api.vocab.EqualityMode;
import org.col.db.PgSetupRule;
import org.col.postgres.PgCopyUtils;
import org.junit.*;
import org.postgresql.jdbc.PgConnection;

public class DuplicateMapperTest {
  
  final static int datasetKey = 1000;
  DuplicateMapper mapper;
  SqlSession session;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @BeforeClass
  public static void setup() throws Exception {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      final DatasetPartitionMapper pm = session.getMapper(DatasetPartitionMapper.class);
      pm.create(datasetKey);
      pm.buildIndices(datasetKey);
      pm.attach(datasetKey);
      session.commit();
    }
    
    try (Connection c = pgSetupRule.connect()) {
      PgConnection pgc = (PgConnection) c;
  
      PgCopyUtils.copy(pgc, "dataset", "/duplicates/dataset.csv");
      PgCopyUtils.copy(pgc, "verbatim_1000", "/duplicates/verbatim.csv");
      PgCopyUtils.copy(pgc, "name_1000", "/duplicates/name.csv");
      PgCopyUtils.copy(pgc, "taxon_1000", "/duplicates/taxon.csv");
      PgCopyUtils.copy(pgc, "synonym_1000", "/duplicates/synonym.csv");
  
      c.commit();
    }
  }
  
  @Before
  public void init(){
    session = PgSetupRule.getSqlSessionFactory().openSession(true);
    mapper = session.getMapper(DuplicateMapper.class);
  }
  
  @After
  public void destroy(){
    session.close();
  }
  
  
  @Test
  public void find() throws Exception {
    Page p = new Page(0, 10);
    List<Duplicate> dups = mapper.find(datasetKey, EqualityMode.CANONICAL, null, null, null, null, p);
    for (Duplicate d : dups) {
      Assert.assertNotNull(d.getUsage1().getId());
      Assert.assertNotNull(d.getUsage2().getId());
      Assert.assertNotEquals(d.getUsage1().getId(), d.getUsage2().getId());
      Assert.assertNotNull(d.getUsage1().getName().getId());
      Assert.assertNotNull(d.getUsage2().getName().getId());
      Assert.assertEquals(d.getUsage1().getName().getScientificName(), d.getUsage2().getName().getScientificName());
    }
  }
  
}