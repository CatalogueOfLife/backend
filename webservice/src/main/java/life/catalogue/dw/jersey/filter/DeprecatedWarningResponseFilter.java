package life.catalogue.dw.jersey.filter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;

import life.catalogue.resources.legacy.LegacyConfig;

/**
 * Marks resource as derecated in response headers
 * https://datatracker.ietf.org/doc/rfc9745
 */
@LegacyAPI
public class DeprecatedWarningResponseFilter implements ContainerResponseFilter {

  private final String message;
  private final LocalDateTime sunset;
  private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                                                               .withZone(ZoneId.of("GMT"));

  public DeprecatedWarningResponseFilter(LegacyConfig cfg) {
    this.message = String.format("Deprecated API: Please update your application and contact %s for help.", cfg.support);
    if (cfg.sunset != null) {
      this.sunset = LocalDateTime.of(cfg.sunset, LocalTime.MAX);
    } else {
      this.sunset = null;
    }
  }

  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext resp) throws IOException {
    // draft header, see https://imhoratiu.wordpress.com/2021/01/20/respectful-rest-apis-sunset-and-deprecation-http-headers/
    resp.getHeaders().putSingle("Deprecation", "true");
    if (sunset != null) {
      resp.getHeaders().putSingle("Sunset", formatter.format(sunset));
    }
    resp.getHeaders().putSingle("Warning", "299 - \"" + message + "\"");
  }
}
