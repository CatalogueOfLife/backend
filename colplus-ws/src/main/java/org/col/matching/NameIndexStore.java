package org.col.matching;

import java.util.ArrayList;

import org.col.api.model.Name;

public interface NameIndexStore extends AutoCloseable {
  
  int size();
  
  ArrayList<Name> get(String key);
  
  boolean containsKey(String key);
  
  void put(String key, ArrayList<Name> group);
}
