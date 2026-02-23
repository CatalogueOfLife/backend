package life.catalogue.release;

import life.catalogue.api.model.Dataset;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.es2.indexing.NameUsageIndexService;

import org.apache.ibatis.session.SqlSessionFactory;

import jakarta.validation.Validator;

/**
 * Job to duplicate a managed project with all its data, decisions and metadata
 */
public class ProjectDuplication extends AbstractProjectCopy {

  ProjectDuplication(SqlSessionFactory factory, NameUsageIndexService indexService, DatasetImportDao diDao, DatasetDao dDao, Validator validator,
                     int datasetKey, int userKey, ReleaseConfig cfg) {
    super("duplicating", factory, diDao, dDao, indexService, validator, userKey, datasetKey, false, cfg.deleteOnError);
  }

  @Override
  protected void modifyDataset(Dataset d) {
    super.modifyDataset(d);
    d.setTitle(d.getTitle() + " copy");
  }
}
