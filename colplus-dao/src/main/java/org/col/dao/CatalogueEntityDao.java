package org.col.dao;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.CatalogueEntity;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.db.mapper.CatalogueCRUDMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic CRUD DAO for globally scoped entities with integer keys
 * that allows to hook post actions for create, update and delete
 * with access to the old version of the object.
 */
public class CatalogueEntityDao<T extends CatalogueEntity, M extends CatalogueCRUDMapper<T>> extends GlobalEntityDao<T, M>{
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(CatalogueEntityDao.class);
  
  /**
   * @param offerChangedHook if true loads the old version of the updated or deleted object and offers it to the before and after methods.
   *                         If false the old value will always be null but performance will be better
   */
  public CatalogueEntityDao(boolean offerChangedHook, SqlSessionFactory factory, Class<M> mapperClass) {
    super(offerChangedHook, factory, mapperClass);
  }
  
  public ResultPage<T> list(final int catalogueKey, Page page) {
    Page p = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()) {
      M mapper = session.getMapper(mapperClass);
      List<T> result = mapper.list(catalogueKey, p);
      return new ResultPage<>(p, result, () -> mapper.count(catalogueKey));
    }
  }
  
}
