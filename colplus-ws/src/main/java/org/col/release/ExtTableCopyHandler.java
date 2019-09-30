package org.col.release;

import java.util.function.Consumer;

import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.GlobalEntity;
import org.col.api.model.TaxonExtension;
import org.col.db.mapper.TaxonExtensionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtTableCopyHandler<T extends GlobalEntity> extends TableCopyHandlerBase<TaxonExtension<T>> {
  
  private static final Logger LOG = LoggerFactory.getLogger(ExtTableCopyHandler.class);
  private final TaxonExtensionMapper<T> mapper;
  private final int targetDatasetKey;
  
  public ExtTableCopyHandler(SqlSessionFactory factory, String entityName, Class<? extends TaxonExtensionMapper<T>> mapperClass,
                             Consumer<TaxonExtension<T>> updater, int targetDatasetKey) {
    super(factory, entityName, updater);
    mapper = session.getMapper(mapperClass);
    this.targetDatasetKey = targetDatasetKey;
  }
  
  @Override
  void create(TaxonExtension<T> obj) {
    mapper.create(obj.getObj(), obj.getTaxonID(), targetDatasetKey);
  }
}
