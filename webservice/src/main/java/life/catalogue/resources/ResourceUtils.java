package life.catalogue.resources;

import javax.ws.rs.RedirectionException;
import javax.ws.rs.core.Response;
import java.net.URI;

public class ResourceUtils {

  public static void redirect(URI location) throws RedirectionException {
    throw new RedirectionException(Response.Status.FOUND, location);
  }
}
