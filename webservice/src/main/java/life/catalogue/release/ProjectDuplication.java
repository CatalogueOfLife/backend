package life.catalogue.release;

import life.catalogue.api.model.Dataset;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Job to duplicate a managed project with all its data, decisions and metadata
 */
public class ProjectDuplication extends ProjectRunnable {

  ProjectDuplication(SqlSessionFactory factory, NameUsageIndexService indexService, DatasetImportDao diDao, int datasetKey, Dataset copy, int userKey) {
    super("duplicating", factory, diDao, indexService, userKey, datasetKey, copy);
  }

}
