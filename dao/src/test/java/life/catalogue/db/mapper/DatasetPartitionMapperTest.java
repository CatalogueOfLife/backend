package life.catalogue.db.mapper;

import life.catalogue.db.TestDataRule;

import java.util.Random;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DatasetPartitionMapperTest extends MapperTestBase<DatasetPartitionMapper> {
  Random rnd = new Random();
  
  public DatasetPartitionMapperTest() {
    super(DatasetPartitionMapper.class);
  }

  @Test
  public void updateCounter() {
    int x = mapper().updateUsageCounter(TestDataRule.APPLE.key);
    assertEquals(4, x);
  }

}