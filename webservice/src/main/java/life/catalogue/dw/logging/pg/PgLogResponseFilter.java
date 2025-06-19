package life.catalogue.dw.logging.pg;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;

import life.catalogue.api.model.ApiLog;
import life.catalogue.api.model.User;

import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.time.LocalDateTime;

import static life.catalogue.dw.auth.PrivateFilter.DATASET_KEY_PROPERTY;

/**
 *
 */
@Priority(1) // very high priority, probably called last
public class PgLogResponseFilter implements ContainerResponseFilter {
  final PgLogCollector collector;

  public PgLogResponseFilter(PgLogCollector collector) {
    this.collector = collector;
  }

  @Override
  public void filter(ContainerRequestContext req, ContainerResponseContext resp) throws IOException {
    var date = req.getProperty(PgLogRequestFilter.REQ_DATE_PROPERTY);
    if (date instanceof LocalDateTime) {
      final ApiLog log = new ApiLog();
      log.setDate((LocalDateTime) date);
      // method & uri
      log.setMethod(ApiLog.HttpMethod.from(req.getMethod()));
      log.setRequest(req.getUriInfo().getRequestUri().toString());
      log.setAgent(req.getHeaderString("User-Agent"));
      // user
      Principal principal = req.getSecurityContext().getUserPrincipal();
      if (principal instanceof User) {
        log.setUser(((User) principal).getKey());
      }
      // response
      log.setResponseCode(resp.getStatus());
      Duration duration = Duration.between(log.getDate(), LocalDateTime.now());
      log.setDuration((int)duration.toMillis());
      // dataset key from request
      Object dkey = req.getProperty(DATASET_KEY_PROPERTY);
      if (dkey != null) {
        log.setDatasetKey((Integer) dkey);
      }
      // keep in memory
      collector.add(log);
    }
  }
}
