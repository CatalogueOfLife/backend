package org.col.es;

public class EsException extends Exception {

  public EsException(String arg0, Throwable arg1) {
    super(arg0, arg1);
  }

  public EsException(String arg0) {
    super(arg0);
  }

  public EsException(Throwable arg0) {
    super(arg0);
  }

}
