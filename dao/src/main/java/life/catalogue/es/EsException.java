package life.catalogue.es;

/**
 * Generic runtime exception indicating that an error originated from somewhere inside the es package.
 *
 */
public class EsException extends RuntimeException {
  
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
