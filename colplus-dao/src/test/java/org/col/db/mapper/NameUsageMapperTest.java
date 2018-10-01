package org.col.db.mapper;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.BareName;
import org.col.api.model.Synonym;
import org.col.api.model.TaxonVernacularUsage;
import org.col.api.model.VernacularName;
import org.junit.Assert;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.NAME4;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class NameUsageMapperTest extends MapperTestBase<NameUsageMapper> {

  public NameUsageMapperTest() {
    super(NameUsageMapper.class);
  }
  private AtomicInteger counter = new AtomicInteger(0);
  
  @Test
  public void processDatasetTaxa() throws Exception {
    mapper().processDatasetTaxa(NAME4.getDatasetKey(), new ResultHandler<TaxonVernacularUsage>() {
      public void handleResult(ResultContext<? extends TaxonVernacularUsage> ctx) {
        counter.incrementAndGet();
        for (VernacularName v : ctx.getResultObject().getVernacularNames()) {
          assertNotNull(v.getName());
        }
      }
    });
    Assert.assertEquals(2, counter.get());
  }
  
  @Test
  public void processDatasetSynonyms() throws Exception {
    mapper().processDatasetSynonyms(NAME4.getDatasetKey(), new ResultHandler<Synonym>() {
      public void handleResult(ResultContext<? extends Synonym> ctx) {
        counter.incrementAndGet();
        assertTrue(ctx.getResultObject().getStatus().isSynonym());
        assertNotNull(ctx.getResultObject().getAccepted());
      }
    });
    Assert.assertEquals(2, counter.get());
  }
  
  @Test
  public void processDatasetBareNames() throws Exception {
    mapper().processDatasetBareNames(NAME4.getDatasetKey(), new ResultHandler<BareName>() {
      public void handleResult(ResultContext<? extends BareName> ctx) {
        counter.incrementAndGet();
        assertNotNull(ctx.getResultObject());
      }
    });
    Assert.assertEquals(0, counter.get());
  }
}
