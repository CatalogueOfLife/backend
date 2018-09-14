package org.col.db.mapper;

import com.google.common.collect.Lists;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.*;
import static org.junit.Assert.assertEquals;

/**
 *
 */
public class NameUsageMapperTest extends MapperTestBase<NameUsageMapper> {

  public NameUsageMapperTest() {
    super(NameUsageMapper.class);
  }

  @Test
  public void listByName() throws Exception {
    assertEquals(Lists.newArrayList(SYN2), mapper().listByName(NAME4.getDatasetKey(), NAME4.getKey()));
    assertEquals(Lists.newArrayList(TAXON1), mapper().listByName(NAME1.getDatasetKey(), NAME1.getKey()));
  }

}
