package life.catalogue.es;

public class TooManyRequestsException extends EsRequestException {

  public TooManyRequestsException() {
    super("Too many requests");
  }

}
