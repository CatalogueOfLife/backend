package life.catalogue.dw.jersey;

import jakarta.ws.rs.core.Response;

/**
 *
 */
public enum MoreStatus implements Response.StatusType {
  
  UNPROCESSABLE_ENTITY(422, "Unprocessable Entity"),
  SERVER_TIMEOUT(524, "A timeout occurred");

  private final int status;
  private final String phrase;
  
  MoreStatus(int status, String phrase) {
    this.status = status;
    this.phrase = phrase;
  }
  
  @Override
  public int getStatusCode() {
    return 0;
  }
  
  @Override
  public Response.Status.Family getFamily() {
    return Response.Status.Family.familyOf(status);
  }
  
  @Override
  public String getReasonPhrase() {
    return phrase;
  }
}
