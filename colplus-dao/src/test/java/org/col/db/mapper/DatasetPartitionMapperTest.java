package org.col.db.mapper;

import org.col.api.vocab.Datasets;
import org.junit.Test;

public class DatasetPartitionMapperTest extends MapperTestBase<DatasetPartitionMapper> {
  
  public DatasetPartitionMapperTest() {
    super(DatasetPartitionMapper.class);
  }
  
  @Test
  public void createDelete() {
    // we only create the prov-cat partition in the InitMybatisRule
    mapper().delete(Datasets.COL);
    mapper().create(Datasets.COL);
    mapper().buildIndices(Datasets.COL);
    mapper().attach(Datasets.COL);
    
    mapper().delete(Datasets.COL);
  }
}