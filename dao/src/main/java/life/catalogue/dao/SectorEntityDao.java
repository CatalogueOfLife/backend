package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DatasetScopedEntity;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SectorScoped;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.mapper.SectorMapper;

import java.util.UUID;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import jakarta.validation.Validator;

public class SectorEntityDao<T extends DatasetScopedEntity<String> & SectorScoped, M extends CRUD<DSID<String>, T> & DatasetPageable<T> & DatasetProcessable<T>>
  extends DatasetEntityDao<String, T, M> {

  protected final LoadingCache<DSID<Integer>, Sector.Mode> sectorModes = Caffeine.newBuilder()
    .maximumSize(1000)
    .build(this::lookupSectorMode);

  private Sector.Mode lookupSectorMode(DSID<Integer> key) {
    try (SqlSession session = factory.openSession(false)) {
      return session.getMapper(SectorMapper.class).getMode(key.getDatasetKey(), key.getId());
    }
  }

  public SectorEntityDao(boolean offerChangedHook, SqlSessionFactory factory, Class<T> entityClass, Class<M> mapperClass, Validator validator) {
    super(offerChangedHook, factory, entityClass, mapperClass, validator);
  }

  @Override
  public T get(DSID<String> key) {
    T obj = super.get(key);
    // add the sector mode without the need for lots of joins in the SQL
    if (obj != null && obj.getSectorKey() != null) {
      obj.setSectorMode(sectorModes.get(obj.getSectorDSID()));
    }
    return obj;
  }

  @Override
  public DSID<String> create(T obj, int user) {
    // provide new key
    if (obj.getId() == null) {
      obj.setId(UUID.randomUUID().toString());
    }
    return super.create(obj, user);
  }

}
