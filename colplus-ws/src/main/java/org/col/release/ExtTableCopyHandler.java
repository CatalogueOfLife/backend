package org.col.release;

import java.util.function.Consumer;

import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.DatasetScopedEntity;
import org.col.api.model.TaxonExtension;
import org.col.db.mapper.TaxonExtensionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtTableCopyHandler<T extends DatasetScopedEntity<Integer>> extends TableCopyHandlerBase<TaxonExtension<T>> {
  
  private static final Logger LOG = LoggerFactory.getLogger(ExtTableCopyHandler.class);
  private final TaxonExtensionMapper<T> mapper;
  
  public ExtTableCopyHandler(SqlSessionFactory factory, String entityName, Class<? extends TaxonExtensionMapper<T>> mapperClass,
                             Consumer<TaxonExtension<T>> updater) {
    super(factory, entityName, updater);
    mapper = session.getMapper(mapperClass);
  }
  
  @Override
  void create(TaxonExtension<T> obj) {
    mapper.create(obj.getObj(), obj.getTaxonID());
  }
}
