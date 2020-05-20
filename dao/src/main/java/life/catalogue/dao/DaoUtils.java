package life.catalogue.dao;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DaoUtils {

  private static final Logger LOG = LoggerFactory.getLogger(DaoUtils.class);

  public static Dataset requireManaged(int datasetKey, SqlSession session) {
    Dataset d = session.getMapper(DatasetMapper.class).get(datasetKey);
    if (d.getDeleted() != null) {
      throw new IllegalArgumentException("The dataset " + datasetKey + " is deleted and cannot be modified.");
    }
    if (d.getOrigin() != DatasetOrigin.MANAGED) {
      throw new IllegalArgumentException("Only data from managed datasets can be modified. Dataset " + datasetKey + " is of origin " + d.getOrigin());
    }
    return d;
  }

  public static Dataset requireManaged(int datasetKey, SqlSessionFactory factory) {
    try (SqlSession s = factory.openSession()) {
      return requireManaged(datasetKey, s);
    }
  }

  public static void aquireTableLock(int datasetKey, SqlSession session) {
    LOG.info("Try to aquire a table lock for dataset {}", datasetKey);
    session.getMapper(DatasetPartitionMapper.class).lockTables(datasetKey);
  }

}
