package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DatasetScopedEntity;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.Searchable;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;
import org.checkerframework.checker.units.qual.K;

/**
 * Integer based dataset scoped entity mapper
 * @param <T> entity type
 * @param <R> search request type
 */
public interface BaseDecisionMapper<T extends DatasetScopedEntity<Integer>, R> extends CRUD<DSID<Integer>, T>,
  DatasetPageable<T>,
  ProcessableDataset<T>,
  Searchable<T, R> {

  default T get(Integer key) {
    return get(DSID.idOnly(key));
  }

  default int delete(Integer key) {
    return delete(DSID.idOnly(key));
  }

}
