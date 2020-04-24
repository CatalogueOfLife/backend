package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameClassification;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.search.SimpleDecision;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.db.TestDataRule;
import org.apache.ibatis.cursor.Cursor;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static life.catalogue.api.TestEntityGenerator.NAME4;
import static org.junit.Assert.*;


public class NameUsageWrapperMapperTreeTest extends MapperTestBase<NameUsageWrapperMapper> {
  
  public NameUsageWrapperMapperTreeTest() {
    super(NameUsageWrapperMapper.class, TestDataRule.tree());
  }
  
  private AtomicInteger counter = new AtomicInteger(0);
  
  @Test
  public void getTaxa() throws Exception {
    
    List<?> cl = mapper(TaxonMapper.class).classificationSimple(DSID.of(NAME4.getDatasetKey(), "t15"));
    assertEquals(7, cl.size());
    
    NameUsageWrapper tax = mapper().get(NAME4.getDatasetKey(), "t15");
    assertFalse(tax.getClassification().isEmpty());
    assertEquals(cl, tax.getClassification());
    
    // now add decisions!!
    DecisionMapper dm = mapper(DecisionMapper.class);
    EditorialDecision ed1 = TestEntityGenerator.newDecision(Datasets.DRAFT_COL, NAME4.getDatasetKey(), "t15");
    dm.create(ed1);
    // broken
    EditorialDecision ed2 = TestEntityGenerator.newDecision(Datasets.DRAFT_COL, NAME4.getDatasetKey(), "t1556");
    dm.create(ed2);
    EditorialDecision ed3 = TestEntityGenerator.newDecision(Datasets.NAME_INDEX, NAME4.getDatasetKey(), "t15");
    dm.create(ed3);
    commit();
    
    tax = mapper().get(NAME4.getDatasetKey(), "t15");
    assertFalse(tax.getClassification().isEmpty());
    assertEquals(cl, tax.getClassification());
    assertEquals(2, tax.getDecisions().size());
    Set<Integer> keys = new HashSet<>();
    for (SimpleDecision sd : tax.getDecisions()) {
      keys.add(sd.getId());
      if (sd.getId().equals(ed1.getId())) {
        assertEquals(ed1.asSimpleDecision(), sd);
        
      } else if (sd.getId().equals(ed2.getId())) {
        fail("broken decision");
  
      } else if (sd.getId().equals(ed3.getId())) {
        assertEquals(ed3.asSimpleDecision(), sd);

      } else {
        fail("Unknown decision");
      }
    }
    assertEquals(2, keys.size());
  }

  @Test
  public void processDatasetBareNames() throws Exception {
    Cursor<NameUsageWrapper> c = mapper().processDatasetBareNames(NAME4.getDatasetKey(), null);
    c.forEach(obj -> {
        counter.incrementAndGet();
        assertNotNull(obj);
        assertNotNull(obj.getUsage());
        assertNotNull(obj.getUsage().getName());
    });
    Assert.assertEquals(0, counter.get());
  }
  
  @Test
  public void processSubtree() throws Exception {
    Cursor<SimpleNameClassification> c = mapper().processTree(NAME4.getDatasetKey(), null, "t4");
    c.forEach(obj -> {
        counter.incrementAndGet();
        assertNotNull(obj.getClassification());
        
        // classification should always include the taxon itself
        // https://github.com/Sp2000/colplus-backend/issues/326
        assertFalse(obj.getClassification().isEmpty());
        SimpleName last = obj.getClassification().get(obj.getClassification().size()-1);
        assertEquals(obj.getId(), last.getId());
        
        // classification should always start with the root of the dataset, not the root of the traversal!
        assertEquals("t1", obj.getClassification().get(0).getId());
        assertEquals("t2", obj.getClassification().get(1).getId());
        assertEquals("t3", obj.getClassification().get(2).getId());
    });
    Assert.assertEquals(21, counter.get());
  }
}
