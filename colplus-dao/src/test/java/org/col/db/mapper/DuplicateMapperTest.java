package org.col.db.mapper;

import java.sql.Connection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Duplicate;
import org.col.api.model.Page;
import org.col.api.vocab.MatchingMode;
import org.col.api.vocab.TaxonomicStatus;
import org.col.db.PgSetupRule;
import org.col.postgres.PgCopyUtils;
import org.gbif.nameparser.api.Rank;
import org.junit.*;
import org.postgresql.jdbc.PgConnection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Ignore
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
      PgCopyUtils.copy(pgc, "name_usage_1000", "/duplicates/name_usage.csv");
      
      c.commit();
    }
  }
  
  @Before
  public void init() {
    session = PgSetupRule.getSqlSessionFactory().openSession(true);
    mapper = session.getMapper(DuplicateMapper.class);
  }
  
  @After
  public void destroy() {
    session.close();
  }
  
  @Test
  public void usagesByIds() {
    List<Duplicate.UsageDecision> res = mapper.usagesByIds(datasetKey, "'45', '46'");
    showU(res);
    assertEquals(2, res.size());
  }
  
  @Test
  public void duplicates() {
    Set<TaxonomicStatus> status = new HashSet<>();
    List<Duplicate> dups = mapper.duplicates(MatchingMode.STRICT, 2, datasetKey, null, null, null, null, null, null, new Page(0, 10));
    show(dups);
    assertEquals(10, dups.size());
    
    
    Page p = new Page(0, 100);
    dups = mapper.duplicates(MatchingMode.STRICT, 2, datasetKey, null, null, status, null, null, null, p);
    assertEquals(19, dups.size());
    
    dups = mapper.duplicates(MatchingMode.STRICT, 2, datasetKey, null, Rank.SUBSPECIES, status, null, null, null, p);
    assertEquals(4, dups.size());
    
    dups = mapper.duplicates(MatchingMode.STRICT, 2, datasetKey, null, Rank.SUBSPECIES, status, true, null, null, p);
    assertEquals(2, dups.size());
    
    status.add(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    dups = mapper.duplicates(MatchingMode.STRICT, 2, datasetKey, null, Rank.SUBSPECIES, status, true, null, null, p);
    assertEquals(2, dups.size());
    
    status.clear();
    status.add(TaxonomicStatus.ACCEPTED);
    dups = mapper.duplicates(MatchingMode.STRICT, 2, datasetKey, null, Rank.SUBSPECIES, status, true, null, null, p);
    assertTrue(dups.isEmpty());
    
    
    //dups = mapper.duplicates(MatchingMode.FUZZY, 2, datasetKey, null, Rank.SUBSPECIES, status, null, null, null, p);
    //assertEquals(5, dups.size());
    //
    //dups = mapper.duplicates(MatchingMode.FUZZY, 2, datasetKey, null, Rank.SUBSPECIES, status, null, false, null, p);
    //assertEquals(4, dups.size());
    //
    //dups = mapper.duplicates(MatchingMode.FUZZY, 2, datasetKey, null, Rank.SUBSPECIES, status, null, true, null, p);
    //assertEquals(2, dups.size());
  }
  
  
  private static void showU(List<Duplicate.UsageDecision> dups) {
    System.out.println("---  ---  ---  ---");
    for (Duplicate.UsageDecision u : dups) {
      System.out.println(u.getUsage().getId() + "  " + u.getUsage().getName().canonicalNameComplete());
    }
  }
  
  private static void show(List<Duplicate> dups) {
    System.out.println("---  ---  ---  ---");
    int idx = 1;
    for (Duplicate d : dups) {
      System.out.println(" #" + idx++);
      System.out.println(" KEY: " + d.getKey());
      for (Duplicate.UsageDecision u : d.getUsages()) {
        System.out.println(u.getUsage().getId() + "  " + u.getUsage().getName().canonicalNameComplete());
      }
    }
  }
}