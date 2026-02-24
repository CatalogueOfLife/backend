package life.catalogue.es.query;

public class InvalidQueryException extends IllegalArgumentException {
  
  public InvalidQueryException(String arg0) {
    super(arg0);
  }
  
  public InvalidQueryException(Throwable arg0) {
    super(arg0);
  }
  
}
