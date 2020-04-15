package life.catalogue.dao;

import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.db.MybatisTestUtils;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.TestDataRule;
import org.junit.Assert;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.NAME4;
import static life.catalogue.api.vocab.Datasets.DRAFT_COL;
import static org.junit.Assert.*;

public class NameUsageProcessorTest extends DaoTestBase {
  
  public NameUsageProcessorTest() {
    super(TestDataRule.tree());
  }
  
  @Test
  public void processDataset() {
    DRH handler = new DRH();
    NameUsageProcessor proc = new NameUsageProcessor(PgSetupRule.getSqlSessionFactory());
    proc.processDataset(NAME4.getDatasetKey(), handler);
    Assert.assertEquals(24, handler.counter.get());
    Assert.assertEquals(4, handler.synCounter.get());
  }
  
  public static class DRH implements Consumer<NameUsageWrapper> {
    public AtomicInteger counter = new AtomicInteger(0);
    public AtomicInteger synCounter = new AtomicInteger(0);
    
    @Override
    public void accept(NameUsageWrapper obj) {
      counter.incrementAndGet();
      assertNotNull(obj.getUsage().getId());

      Name n = obj.getUsage().getName();
      assertNotNull(n);
      assertNotNull(n.getId());
      assertNotNull(n.getDatasetKey());
      
      // classification should always include the taxon itself
      // https://github.com/Sp2000/colplus-backend/issues/326
      assertFalse(obj.getClassification().isEmpty());
      SimpleName last = obj.getClassification().get(obj.getClassification().size()-1);
      assertEquals(obj.getUsage().getId(), last.getId());
      
      if ( obj.getUsage().getId().startsWith("t")) {
        assertTrue(obj.getUsage().isTaxon());
        Taxon t = (Taxon) obj.getUsage();
        assertNotNull(t.getId());
        assertEquals((Integer) 1, t.getVerbatimKey());
        if (t.getId().equals("t1")) {
          assertNull(t.getParentId());
        } else {
          assertNotNull(t.getParentId());
        }
        for (VernacularName v : obj.getVernacularNames()) {
          assertNotNull(v.getName());
        }
        
      } else {
        assertTrue(obj.getUsage().isSynonym());
        synCounter.incrementAndGet();
      }
    }
  }
  
  @Test
  public void processSector() throws Exception {
    MybatisTestUtils.populateDraftTree(session());
    AtomicInteger counter = new AtomicInteger(0);
  
    SectorMapper sm = mapper(SectorMapper.class);
  
    Sector s = TestEntityGenerator.setUserDate(new Sector());
    s.setDatasetKey(DRAFT_COL);
    s.setSubjectDatasetKey(DRAFT_COL);
    s.setTarget(new SimpleName("t2", null, null));
    sm.create(s);
    
    // we update the sector key of a few usages so we mock a sync
    try (Connection con = PgSetupRule.getSqlSessionFactory().openSession().getConnection();
        Statement st = con.createStatement();
    ) {
      st.execute("UPDATE name_usage_" + DRAFT_COL + " SET sector_key="+s.getId()+" WHERE id NOT IN ('t1', 't2') ");
      con.commit();
    }
    
    NameUsageProcessor proc = new NameUsageProcessor(PgSetupRule.getSqlSessionFactory());
    proc.processSector(s, new Consumer<NameUsageWrapper>() {
      public void accept(NameUsageWrapper obj) {
        counter.incrementAndGet();
        Name n = obj.getUsage().getName();
        assertNotNull(n);
        assertNotNull(n.getId());
        assertEquals(Datasets.DRAFT_COL, (int) obj.getUsage().getDatasetKey());
        assertEquals(Datasets.DRAFT_COL, (int) n.getDatasetKey());
        
        // classification should always include the taxon itself
        // https://github.com/Sp2000/colplus-backend/issues/326
        assertFalse(obj.getClassification().isEmpty());
        SimpleName last = obj.getClassification().get(obj.getClassification().size()-1);
        assertEquals(obj.getUsage().getId(), last.getId());
        
        // we have no sector, so we just get the root usage back
        assertEquals(obj.getUsage().getId(), last.getId());
      }
    });
    // we do not want the target node that does have sectorKey=NULL !!!
    Assert.assertEquals(3, counter.get());
  }
}