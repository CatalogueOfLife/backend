package life.catalogue.db.mapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import life.catalogue.db.mapper.NameUsageWrapperMapper;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameClassification;
import life.catalogue.api.search.NameUsageWrapper;
import org.junit.Assert;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.NAME4;
import static org.junit.Assert.*;


public class NameUsageWrapperMapperTreeTest extends MapperTestBase<NameUsageWrapperMapper> {
  
  public NameUsageWrapperMapperTreeTest() {
    super(NameUsageWrapperMapper.class, TestDataRule.tree());
  }
  
  private AtomicInteger counter = new AtomicInteger(0);
  
  @Test
  public void getTaxa() throws Exception {
    
    List<?> cl = mapper(TaxonMapper.class).classificationSimple(DSID.key(NAME4.getDatasetKey(), "t15"));
    assertEquals(7, cl.size());
    
    NameUsageWrapper tax = mapper().get(NAME4.getDatasetKey(), "t15");
    assertFalse(tax.getClassification().isEmpty());
    assertEquals(cl, tax.getClassification());
  }

  @Test
  public void processDatasetBareNames() throws Exception {
    mapper().processDatasetBareNames(NAME4.getDatasetKey(), null,new ResultHandler<NameUsageWrapper>() {
      public void handleResult(ResultContext<? extends NameUsageWrapper> ctx) {
        counter.incrementAndGet();
        assertNotNull(ctx.getResultObject());
        assertNotNull(ctx.getResultObject().getUsage());
        assertNotNull(ctx.getResultObject().getUsage().getName());
      }
    });
    Assert.assertEquals(0, counter.get());
  }
  
  @Test
  public void processSubtree() throws Exception {
    mapper().processTree(NAME4.getDatasetKey(), null, "t4",new ResultHandler<SimpleNameClassification>() {
      public void handleResult(ResultContext<? extends SimpleNameClassification> ctx) {
        counter.incrementAndGet();
        SimpleNameClassification obj = ctx.getResultObject();
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
      }
    });
    Assert.assertEquals(21, counter.get());
  }
}
