package org.col.db.mapper;

import java.util.concurrent.atomic.AtomicInteger;

import org.col.api.model.BareName;
import org.col.api.model.Synonym;
import org.col.api.model.TaxonVernacularUsage;
import org.junit.Assert;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.NAME4;


public class NameUsageMapperTest extends MapperTestBase<NameUsageMapper> {

  public NameUsageMapperTest() {
    super(NameUsageMapper.class);
  }
  private AtomicInteger counter = new AtomicInteger(0);
  
  @Test
  public void processDatasetTaxa() throws Exception {
    try (BatchResultHandler<TaxonVernacularUsage> handler = new BatchResultHandler<TaxonVernacularUsage>(
        batch -> counter.addAndGet(batch.size()
        ), 10)
    ) {
      mapper().processDatasetTaxa(NAME4.getDatasetKey(), handler);
    }
    Assert.assertEquals(2, counter.get());
  }
  
  @Test
  public void processDatasetSynonyms() throws Exception {
    try (BatchResultHandler<Synonym> handler = new BatchResultHandler<Synonym>(
        batch -> counter.addAndGet(batch.size()
        ), 10)
    ) {
      mapper().processDatasetSynonyms(NAME4.getDatasetKey(), handler);
    }
    Assert.assertEquals(2, counter.get());
  }
  
  @Test
  public void processDatasetBareNames() throws Exception {
    try (BatchResultHandler<BareName> handler = new BatchResultHandler<BareName>(
        batch -> counter.addAndGet(batch.size()
        ), 10)
    ) {
      mapper().processDatasetBareNames(NAME4.getDatasetKey(), handler);
    }
    Assert.assertEquals(0, counter.get());
  }
}
