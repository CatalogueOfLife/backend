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
    mapper().delete(Datasets.SCRUT_CAT);
    mapper().create(Datasets.SCRUT_CAT);
    mapper().buildIndices(Datasets.SCRUT_CAT);

    mapper().delete(Datasets.SCRUT_CAT);
    mapper().truncateDatasetData(Datasets.SCRUT_CAT);

  }
}