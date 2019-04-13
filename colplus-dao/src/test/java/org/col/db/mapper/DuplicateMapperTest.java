package org.col.db.mapper;

import java.sql.Connection;
import java.util.List;

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

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

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
  public void find() throws Exception {
    Page p = new Page(0, 100);
    List<Duplicate> dups = mapper.find(datasetKey, EqualityMode.CANONICAL, null, null, null, null, p);
    assertEquals(28, dups.size());
    for (Duplicate d : dups) {
      assertNotNull(d.getUsage1().getId());
      assertNotNull(d.getUsage2().getId());
      assertNotEquals(d.getUsage1().getId(), d.getUsage2().getId());
      assertNotNull(d.getUsage1().getName().getId());
      assertNotNull(d.getUsage2().getName().getId());
      assertEquals(d.getUsage1().getName().getScientificName(), d.getUsage2().getName().getScientificName());
    }
  
    dups = mapper.find(datasetKey, EqualityMode.CANONICAL, Rank.SUBSPECIES, null, null, null, p);
    assertEquals(6, dups.size());
    for (Duplicate d : dups) {
      assertEquals(d.getUsage1().getName().getScientificName(), d.getUsage2().getName().getScientificName());
      assertEquals(Rank.SUBSPECIES, d.getUsage1().getName().getRank());
    }
  
    dups = mapper.find(datasetKey, EqualityMode.CANONICAL_WITH_AUTHORS, Rank.SUBSPECIES, null, null, null, p);
    assertEquals(2, dups.size());
  
    dups = mapper.find(datasetKey, EqualityMode.CANONICAL_WITH_AUTHORS, Rank.SUBSPECIES, TaxonomicStatus.PROVISIONALLY_ACCEPTED, null, null, p);
    assertEquals(2, dups.size());

    dups = mapper.find(datasetKey, EqualityMode.CANONICAL_WITH_AUTHORS, Rank.SUBSPECIES, TaxonomicStatus.ACCEPTED, null, null, p);
    assertTrue(dups.isEmpty());
  
    dups = mapper.find(datasetKey, EqualityMode.NAMES_INDEX, Rank.SUBSPECIES, null, null, null, p);
    assertEquals(7, dups.size());
  
    dups = mapper.find(datasetKey, EqualityMode.NAMES_INDEX, Rank.SUBSPECIES, null, null, false, p);
    assertEquals(4, dups.size());

    dups = mapper.find(datasetKey, EqualityMode.NAMES_INDEX, Rank.SUBSPECIES, null, null, true, p);
    show(dups);
    assertEquals(3, dups.size());
  }
  
  private void show(List<Duplicate> dups) {
    System.out.println("---  ---  ---  ---");
    int idx = 1;
    for (Duplicate d : dups) {
      System.out.println(" #" + idx++);
      System.out.println(d.getUsage1().getId() + "  " + d.getUsage1().getName().canonicalNameComplete());
      System.out.println(d.getUsage2().getId() + "  " + d.getUsage2().getName().canonicalNameComplete());
    }
  }
}