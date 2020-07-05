package life.catalogue.dw;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper for a managed instance that hides the start method so the managed instance is only stopped automatically by dropwizard.
 */
public class ManagedStopOnly implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(ManagedStopOnly.class);
  private final Managed managed;

  public ManagedStopOnly(Managed managed) {
    this.managed = managed;
  }

  @Override
  public void start() throws Exception {
  
  }
  
  @Override
  public void stop() throws Exception {
    LOG.info("Stopping {}", managed);
    managed.stop();
  }
}
