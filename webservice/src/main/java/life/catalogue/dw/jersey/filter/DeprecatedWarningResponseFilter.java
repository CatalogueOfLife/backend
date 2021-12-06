package life.catalogue.dw.jersey.filter;

import scala.deprecated;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;

@DeprecatedWarning
public class DeprecatedWarningResponseFilter implements ContainerResponseFilter {

  private final String message;
  private final LocalDateTime sunset;
  private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                                                               .withZone(ZoneId.of("GMT"));

  public DeprecatedWarningResponseFilter(String supportEmail, LocalDate sunset) {
    this.message = String.format("Deprecated API: Please update your application and contact %s for help.", supportEmail);
    if (sunset != null) {
      this.sunset = LocalDateTime.of(sunset, LocalTime.MAX);
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
    resp.getHeaders().putSingle("Warning", "299 COLServer \"" + message + "\"");
  }
}
