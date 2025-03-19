package life.catalogue.dw.jersey.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import org.apache.commons.lang3.time.StopWatch;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.UUID;

/**
 * Filter that adds a timer http header that counts the time between the request started and the response finished.
 */
@Provider
public class TimedResponseFilter implements ContainerResponseFilter, ContainerRequestFilter {
  private static final String PROP_NAME = "requestTimer";

  @Override
  public void filter(ContainerRequestContext ctxt) throws IOException {
    ctxt.setProperty(PROP_NAME, StopWatch.createStarted());
  }

  @Override
  public void filter(ContainerRequestContext ctxt, ContainerResponseContext response) throws IOException {
    StopWatch watch = (StopWatch) ctxt.getProperty(PROP_NAME);
    watch.stop();
    response.getHeaders().putSingle("X-Request-Timer", watch.toString());
  }
}
