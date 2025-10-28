package life.catalogue.dw.logging.pg;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.*;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Adds a timestamp to the context when the request was started
 */
@PreMatching
@Priority(1) // very high priority before authentication!
public class PgLogRequestFilter implements ContainerRequestFilter {
  static String REQ_DATE_PROPERTY = "request.date";

  @Override
  public void filter(ContainerRequestContext req) throws IOException {
    req.setProperty(REQ_DATE_PROPERTY, LocalDateTime.now());
  }
}
