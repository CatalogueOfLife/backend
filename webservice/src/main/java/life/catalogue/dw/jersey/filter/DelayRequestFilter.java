package life.catalogue.dw.jersey.filter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;

import com.google.common.base.Preconditions;

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
      Thread.currentThread().interrupt();
    }
  }
}
