package life.catalogue.matching;

import java.util.ArrayList;

import life.catalogue.api.model.Name;

public interface NameIndexStore extends AutoCloseable {
  
  /**
   * Counts all name usages. Potentially an expensive operation.
   */
  int count();
  
  ArrayList<Name> get(String key);
  
  boolean containsKey(String key);
  
  void put(String key, ArrayList<Name> group);
}
