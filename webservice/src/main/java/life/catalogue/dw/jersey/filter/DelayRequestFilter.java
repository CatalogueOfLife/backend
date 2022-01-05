package life.catalogue.dw.jersey.filter;

import com.google.common.base.Preconditions;

import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@LegacyAPI
public class DelayRequestFilter implements ContainerRequestFilter {

  private final int delay; // in milliseconds

  /**
   * @param delay in milliseconds
   */
  public DelayRequestFilter(int delay) {
    Preconditions.checkArgument(delay >= 0, "Delay in milliseconds needs to be zero or positive");
    this.delay = delay;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    try {
      TimeUnit.MILLISECONDS.sleep(delay);
    } catch (InterruptedException e) {
      // just continue
    }
  }
}
