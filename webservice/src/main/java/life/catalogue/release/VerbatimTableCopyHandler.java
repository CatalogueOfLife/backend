package life.catalogue.release;

import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.db.mapper.VerbatimRecordMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerbatimTableCopyHandler extends TableCopyHandlerBase<VerbatimRecord> {

  private static final Logger LOG = LoggerFactory.getLogger(VerbatimTableCopyHandler.class);
  private final VerbatimRecordMapper mapper;
  private final int datasetKey;

  public VerbatimTableCopyHandler(int datasetKey, SqlSessionFactory factory) {
    super(factory, VerbatimRecord.class.getSimpleName());
    mapper = session.getMapper(VerbatimRecordMapper.class);
    this.datasetKey=datasetKey;
  }
  
  @Override
  void create(VerbatimRecord obj) {
    obj.setDatasetKey(datasetKey);
    mapper.createWithKey(obj);
  }
}
