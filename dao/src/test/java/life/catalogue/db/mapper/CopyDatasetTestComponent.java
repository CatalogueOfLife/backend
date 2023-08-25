package life.catalogue.db.mapper;

import life.catalogue.db.CopyDataset;
import life.catalogue.db.SqlSessionFactoryRule;

import org.apache.ibatis.session.SqlSession;

public class CopyDatasetTestComponent {

  public static void copy(CopyDataset mapper, int key, boolean mapIds) throws Exception {
    if (mapIds) {
      try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
        DatasetPartitionMapper dmp = session.getMapper(DatasetPartitionMapper.class);
        DatasetPartitionMapper.IDMAP_TABLES.forEach(t -> {
          dmp.dropTable(t, key);
          dmp.createIdMapTable(t, key);
        });
      }
    }
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      DatasetPartitionMapper dmp = session.getMapper(DatasetPartitionMapper.class);
      dmp.createSequences(998);
    }
    mapper.copyDataset(key, 998, mapIds);
  }
}
