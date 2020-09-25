package life.catalogue.matching;

import life.catalogue.api.model.IndexName;
import life.catalogue.common.Managed;

import java.util.List;

public interface NameIndexStore extends Managed {

  /**
   * Lookup IndexName by its key
   */
  IndexName get(Integer key);

  Iterable<IndexName> all();

  /**
   * Counts all name usages. Potentially an expensive operation.
   */
  int count();

  /**
   * Remove all entries of the names index store
   */
  void clear();

  List<IndexName> get(String key);
  
  boolean containsKey(String key);
  
  void add(String key, IndexName name);
}
