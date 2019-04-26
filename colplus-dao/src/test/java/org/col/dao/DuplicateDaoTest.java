package org.col.dao;

import java.sql.Connection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Duplicate;
import org.col.api.model.Page;
import org.col.api.model.Synonym;
import org.col.api.model.Taxon;
import org.col.api.vocab.MatchingMode;
import org.col.api.vocab.TaxonomicStatus;
import org.col.db.PgSetupRule;
import org.col.db.mapper.DatasetPartitionMapper;
import org.col.postgres.PgCopyUtils;
import org.gbif.nameparser.api.Rank;
import org.junit.*;
import org.postgresql.jdbc.PgConnection;

import static org.junit.Assert.*;

public class DuplicateDaoTest {
    final static int datasetKey = 1000;
    DuplicateDao dao;
    SqlSession session;
    StopWatch watch = new StopWatch();
    
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
      dao = new DuplicateDao(session);
      watch.reset();
    }
    
    @After
    public void destroy() {
      session.close();
    }
    
    @Test
    public void duplicates() {
      Set<TaxonomicStatus> status = new HashSet<>();
      int minSize = 2;
      List<Duplicate> dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, null, null, null, null, new Page(0, 10));
      show(dups);
      assertComplete(10, dups, minSize);
      System.out.println(watch);
      
      
      Page p = new Page(0, 100);
      dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, status, null, null, null, p);
      show(dups);
      assertComplete(19, dups, minSize);
      
      dups = find(MatchingMode.STRICT, minSize, datasetKey, null, Rank.SUBSPECIES, status, null, null, null, p);
      assertComplete(4, dups, minSize);
      
      dups = find(MatchingMode.STRICT, minSize, datasetKey, null, Rank.SUBSPECIES, status, true, null, null, p);
      assertComplete(2, dups, minSize);
      
      status.add(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
      dups = find(MatchingMode.STRICT, minSize, datasetKey, null, Rank.SUBSPECIES, status, true, null, null, p);
      assertComplete(2, dups, minSize);
      
      dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, status, true, null, null, p);
      assertComplete(5, dups, minSize);
      
      status.add(TaxonomicStatus.SYNONYM);
      dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, status, true, null, null, p);
      assertComplete(9, dups, minSize);
      
      dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, status, false, null, null, p);
      assertComplete(0, dups, minSize);
      
      dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, null, false, null, null, p);
      assertComplete(5, dups, minSize);
      
      dups = find(MatchingMode.STRICT, 3, datasetKey, null, null, status, true, null, null, p);
      assertComplete(2, dups, 3);
      
      dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, status, true, true, null, p);
      assertComplete(9, dups, minSize);
      
      dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, status, null, true, null, p);
      assertComplete(9, dups, minSize);
      
      dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, status, null, false, null, p);
      assertComplete(0, dups, minSize);
      
      
      status.clear();
      status.add(TaxonomicStatus.ACCEPTED);
      dups = find(MatchingMode.STRICT, minSize, datasetKey, null, Rank.SUBSPECIES, status, true, null, null, p);
      show(dups);
      assertTrue(dups.isEmpty());
      
      System.out.println(watch);
    }
    
    private List<Duplicate> find(MatchingMode mode, Integer minSize, int datasetKey, Integer sectorDatasetKey, Rank rank, Set<TaxonomicStatus> status, Boolean authorshipDifferent, Boolean parentDifferent, Boolean withDecision, Page page) {
      if (!watch.isStarted()) {
        watch.start();
      } else {
        watch.resume();
      }
      List<Duplicate> result = dao.find(mode, minSize, datasetKey, sectorDatasetKey, rank, status, authorshipDifferent, parentDifferent, withDecision, page);
      watch.suspend();
      return result;
    }
    
    private static void assertComplete(int expectedSize, List<Duplicate> dups, int minSize) {
      assertEquals(expectedSize, dups.size());
      for (Duplicate d : dups) {
        assertTrue(d.getUsages().size() >= minSize);
        for (Duplicate.UsageDecision u : d.getUsages()) {
          assertNotNull(u.getUsage().getId());
          assertNotNull(u.getUsage().getName());
          assertNotNull(u.getUsage().getName().getScientificName());
          if (u.getUsage().isSynonym()) {
            Synonym s = (Synonym) u.getUsage();
            assertNotNull(s.getAccepted());
            assertNotNull(s.getAccepted().getName());
            assertEquals(s.getAccepted().getId(), ((Synonym) u.getUsage()).getParentId());
          }
        }
      }
    }
    
    private static void show(List<Duplicate> dups) {
      System.out.println("---  ---  ---  ---");
      int idx = 1;
      for (Duplicate d : dups) {
        System.out.println("\n#" + idx++ + "  " + d.getKey() + " ---");
        for (Duplicate.UsageDecision u : d.getUsages()) {
          System.out.print(" " + u.getUsage().getId() + "  " + u.getUsage().getName().canonicalNameComplete() + "  " + u.getUsage().getStatus());
          if (u.getUsage().isSynonym()) {
            Synonym s = (Synonym) u.getUsage();
            System.out.println(", pid="+s.getParentId() + ", acc="+s.getAccepted().getName().getScientificName());
          } else {
            Taxon t = (Taxon) u.getUsage();
            System.out.println(", pid="+t.getParentId());
          }
        }
      }
    }
}