package org.col.es;

public class InvalidQueryException extends EsException {
  
  public InvalidQueryException(String arg0) {
    super(arg0);
  }
  
  public InvalidQueryException(Throwable arg0) {
    super(arg0);
  }
  
}
