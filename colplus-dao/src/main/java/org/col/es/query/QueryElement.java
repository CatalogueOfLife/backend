package org.col.es.query;

abstract class QueryElement {
  
  public String toString() {
    return QueryUtil.toString(this);
  }
  
}
