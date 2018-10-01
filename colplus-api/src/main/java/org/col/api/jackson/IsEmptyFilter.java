package org.col.api.jackson;

import org.gbif.nameparser.api.Authorship;

/**
 * Filter for custom jackson inclusion to exclude Authorship instances or classes implementing isEmpty
 * which then claim to be empty.
 */
public class IsEmptyFilter {
  
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof IsEmpty) {
      IsEmpty empt = (IsEmpty) obj;
      return empt.isEmpty();
      
    } else if (obj instanceof Authorship) {
      Authorship a = (Authorship) obj;
      return a.isEmpty();
    }
    return false;
  }
}
