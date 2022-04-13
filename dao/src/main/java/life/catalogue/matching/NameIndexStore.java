package life.catalogue.matching;

import life.catalogue.api.model.IndexName;
import life.catalogue.common.Managed;

import java.util.Collection;
import java.util.List;

public interface NameIndexStore extends Managed {

  /**
   * Lookup IndexName by its key
   */
  IndexName get(Integer key);

  /**
   * @param key the canonical name index key
   * @return all names which have the given canonical key, but not the canonical name itself!
   */
  Collection<IndexName> byCanonical(Integer key);

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
