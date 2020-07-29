package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DatasetScopedEntity;
import life.catalogue.db.*;

/**
 * Integer based dataset scoped entity mapper
 * @param <T> entity type
 * @param <R> search request type
 */
public interface BaseDecisionMapper<T extends DatasetScopedEntity<Integer>, R> extends CRUD<DSID<Integer>, T>,
  DatasetPageable<T>,
  DatasetProcessable<T>,
  Searchable<T, R>,
  CopyDataset {
}
