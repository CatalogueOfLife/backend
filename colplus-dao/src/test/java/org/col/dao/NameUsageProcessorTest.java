package org.col.dao;

import org.col.db.PgSetupRule;
import org.col.db.mapper.NameUsageWrapperMapperTreeTest;
import org.col.db.mapper.TestDataRule;
import org.junit.Assert;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.NAME4;

public class NameUsageProcessorTest extends DaoTestBase {
  
  public NameUsageProcessorTest() {
    super(TestDataRule.tree());
  }
  
  @Test
  public void processDataset() {
    NameUsageWrapperMapperTreeTest.DRH handler = new NameUsageWrapperMapperTreeTest.DRH();
    NameUsageProcessor proc = new NameUsageProcessor(PgSetupRule.getSqlSessionFactory(), NAME4.getDatasetKey());
    proc.processDataset(handler);
    Assert.assertEquals(24, handler.counter.get());
    Assert.assertEquals(4, handler.synCounter.get());
  }
}