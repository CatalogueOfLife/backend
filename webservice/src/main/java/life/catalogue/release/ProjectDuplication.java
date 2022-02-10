package life.catalogue.release;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.es.NameUsageIndexService;

import javax.validation.Validator;

import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Job to duplicate a managed project with all its data, decisions and metadata
 */
public class ProjectDuplication extends AbstractProjectCopy {

  ProjectDuplication(SqlSessionFactory factory, NameUsageIndexService indexService, DatasetImportDao diDao, DatasetDao dDao, Validator validator,
                     int datasetKey, int userKey) {
    super("duplicating", factory, diDao, dDao, indexService, validator, userKey, datasetKey, false);
  }

  @Override
  protected void modifyDataset(Dataset d, DatasetSettings ds) {
    super.modifyDataset(d, ds);
    d.setTitle(d.getTitle() + " copy");
  }
}
