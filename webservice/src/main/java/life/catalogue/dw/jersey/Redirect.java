package life.catalogue.dw.jersey;

import java.net.URI;

import jakarta.ws.rs.core.Response;

/**
 *
 */
public class Redirect {

  private Redirect (){}

  public static Response temporary(URI location){
    return Response.status(Response.Status.FOUND).location(location).build();
  }

}
