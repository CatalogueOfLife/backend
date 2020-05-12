package life.catalogue.dao;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.mapper.DatasetMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

public class DaoUtils {

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

}
