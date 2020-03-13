package life.catalogue.api.model;

/**
 * Entity scoped within a single dataset.
 */
public interface DatasetScoped {
  
  Integer getDatasetKey();
  
  void setDatasetKey(Integer key);

}
