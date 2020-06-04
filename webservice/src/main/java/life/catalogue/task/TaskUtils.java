package life.catalogue.task;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.db.mapper.DatasetMapper;
import org.apache.ibatis.session.SqlSession;

public class TaskUtils {

  /**
   * @param datasetKey
   * @param action for "cannot be xxx" for logging messages only
   * @throws IllegalArgumentException if the dataset key does not qualify for action
   */
  public static Dataset validDataset(SqlSession session, int datasetKey, String action) throws IllegalArgumentException {
    DatasetMapper dm = session.getMapper(DatasetMapper.class);
    Dataset d = dm.get(datasetKey);
    if (d == null) {
      throw NotFoundException.notFound(Dataset.class, datasetKey);
    } else if (d.hasDeletedDate()) {
      throw new IllegalArgumentException("Dataset " + datasetKey + " is deleted and cannot be imported");
    } else if (d.getOrigin() == DatasetOrigin.RELEASED) {
      throw new IllegalArgumentException("Dataset " + datasetKey + " is released and cannot be imported");
    } else if (d.getKey() == Datasets.NAME_INDEX) {
      throw new IllegalArgumentException("Dataset " + datasetKey + " is the names index and cannot be imported");
    }
    return d;
  }
}
