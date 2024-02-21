package life.catalogue.db;

import life.catalogue.api.model.DSID;

import java.util.List;

import life.catalogue.db.mapper.*;

import org.apache.ibatis.annotations.Param;

/**
 * Minimal mapper to deal with entities of type V that are related to a single name.
 * @param <T> entity type
 */
public interface NameProcessable<T> extends TempNameUsageRelated {

  /**
   * All NameProcessable mappers.
   */
  List<Class<? extends NameProcessable<?>>> MAPPERS = List.of(
    NameRelationMapper.class,
    TypeMaterialMapper.class,
    NameMatchMapper.class
  );

  List<T> listByName(@Param("key") DSID<String> key);

  int deleteByName(@Param("key") DSID<String> key);

}