package life.catalogue.db.mapper;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.PgSetupRule;

import org.apache.ibatis.session.SqlSession;

public class CopyDatasetTestComponent {

  public static void copy(CopyDataset mapper, int key, boolean mapIds) throws Exception {
    Partitioner.partition(PgSetupRule.getSqlSessionFactory(), 998, DatasetOrigin.MANAGED);
    Partitioner.createManagedObjects(PgSetupRule.getSqlSessionFactory(), 998);
    mapper.copyDataset(key, 998, false);

    if (mapIds) {
      Partitioner.partition(PgSetupRule.getSqlSessionFactory(), 999, DatasetOrigin.MANAGED);
      try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
        DatasetPartitionMapper dmp = session.getMapper(DatasetPartitionMapper.class);
        DatasetPartitionMapper.IDMAP_TABLES.forEach(t -> dmp.createIdMapTable(t, key));
      }
      mapper.copyDataset(key, 999, true);
    }
  }
}
