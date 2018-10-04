package org.col.es.query;

import java.util.EnumMap;
import java.util.List;

import org.col.api.search.NameSearchParameter;

public class NameSearchParamFieldLookup extends EnumMap<NameSearchParameter, List<String>> {
  
  public NameSearchParamFieldLookup() {
    super(NameSearchParameter.class);
  }

}
