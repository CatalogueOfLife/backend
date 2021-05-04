package life.catalogue.dao;

import life.catalogue.api.model.DatasetExport;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.db.mapper.DatasetExportMapper;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.UUID;

public class DatasetExportDao extends EntityDao<UUID, DatasetExport, DatasetExportMapper> {

  public DatasetExportDao(SqlSessionFactory factory) {
    super(false, factory, DatasetExportMapper.class);
  }

  public ResultPage<DatasetExport> list(DatasetExport.Search filter, Page page) {
    return null;
  }

}
