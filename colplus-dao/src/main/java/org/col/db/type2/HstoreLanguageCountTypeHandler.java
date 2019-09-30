package org.col.db.type2;

public class HstoreLanguageCountTypeHandler extends HstoreCountTypeHandlerBase<String> {
  
  @Override
  String toKey(String x) throws IllegalArgumentException {
    return x;
  }
}
