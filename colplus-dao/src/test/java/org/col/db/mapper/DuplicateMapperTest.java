package org.col.db.mapper;

import java.sql.Connection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Duplicate;
import org.col.api.model.Page;
import org.col.api.vocab.EqualityMode;
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
  public void init(){
    session = PgSetupRule.getSqlSessionFactory().openSession(true);
    mapper = session.getMapper(DuplicateMapper.class);
  }
  
  @After
  public void destroy(){
    session.close();
  }
  
  
  @Test
  public void keys() throws Exception {
    Set<TaxonomicStatus> status = new HashSet<>();
    List<Object> keys = mapper.listKeys(datasetKey, EqualityMode.CANONICAL, null, status, null, null, new Page(2,10));
    assertEquals(10, keys.size());
  
    
    Page p = new Page(0, 100);
    keys = mapper.listKeys(datasetKey, EqualityMode.CANONICAL, null,  status, null, null, p);
    assertEquals(19, keys.size());
  
    keys = mapper.listKeys(datasetKey, EqualityMode.CANONICAL, Rank.SUBSPECIES, status, null, null, p);
    assertEquals(4, keys.size());

    keys = mapper.listKeys(datasetKey, EqualityMode.CANONICAL_WITH_AUTHORS, Rank.SUBSPECIES, null, null, null, p);
    assertEquals(2, keys.size());
  
    status.add(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    keys = mapper.listKeys(datasetKey, EqualityMode.CANONICAL_WITH_AUTHORS, Rank.SUBSPECIES, status, null, null, p);
    assertEquals(2, keys.size());
  
    status.clear();
    status.add(TaxonomicStatus.ACCEPTED);
    keys = mapper.listKeys(datasetKey, EqualityMode.CANONICAL_WITH_AUTHORS, Rank.SUBSPECIES, status, null, null, p);
    assertTrue(keys.isEmpty());

    keys = mapper.listKeys(datasetKey, EqualityMode.NAMES_INDEX, Rank.SUBSPECIES, status, null, null, p);
    assertEquals(5, keys.size());

    keys = mapper.listKeys(datasetKey, EqualityMode.NAMES_INDEX, Rank.SUBSPECIES, status, false, null, p);
    assertEquals(4, keys.size());

    keys = mapper.listKeys(datasetKey, EqualityMode.NAMES_INDEX, Rank.SUBSPECIES, status, true, null, p);
    assertEquals(2, keys.size());

    keys = mapper.listKeys(datasetKey, EqualityMode.NAMES_INDEX, Rank.SUBSPECIES, status, true, false, p);
    assertEquals(2, keys.size());

    keys = mapper.listKeys(datasetKey, EqualityMode.NAMES_INDEX, Rank.SUBSPECIES, status, true, true, p);
    assertTrue(keys.isEmpty());
  }
  
  @Test
  public void listUsages() throws Exception {
    Page p = new Page(0, 100);
    Set<TaxonomicStatus> status = new HashSet<>();
    List<Object> keys = mapper.listKeys(datasetKey, EqualityMode.CANONICAL, null, status, null, null, new Page(2,10));

    List<Duplicate> dups = mapper.listUsages(datasetKey, EqualityMode.CANONICAL, null, status, keys);
    show(dups);
    assertEquals(6, dups.size());
  }
  
  private void show(List<Duplicate> dups) {
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