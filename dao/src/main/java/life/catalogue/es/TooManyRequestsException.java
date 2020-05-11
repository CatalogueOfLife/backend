package life.catalogue.es;

/**
 * Exception thrown when Elasticsearch responds with 429 (TOO MANY REQUESTS). If this happens (which is exception but not hypothetical), is
 * seriously swamped (busy merging segments). We must back off and use long wait times between polling whether the server is ready to
 * continue.
 */
public class TooManyRequestsException extends EsRequestException {

  public static int WAIT_INTERVAL_MILLIS = 1000 * 60 * 5;

  public TooManyRequestsException() {
    super("Too many requests");
  }

}
