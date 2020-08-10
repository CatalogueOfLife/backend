package life.catalogue.dw;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManagedUtils {
  private static final Logger LOG = LoggerFactory.getLogger(ManagedUtils.class);

  private ManagedUtils() { }

  public static Managed from(final AutoCloseable obj) {
    return new Managed() {
      @Override
      public void start() throws Exception { }

      @Override
      public void stop() throws Exception {
        LOG.info("Shutting down {}", obj);
        obj.close();
      }
    };
  }

  public static Managed from(final life.catalogue.common.Managed obj) {
    return new Managed() {
      @Override
      public void start() throws Exception {
        obj.start();
      }

      @Override
      public void stop() throws Exception {
        obj.stop();
      }
    };
  }

  /**
   * Wrapper for a managed instance that hides the start method so the managed instance is only stopped automatically by dropwizard.
   */
  public static Managed stopOnly(final Managed obj) {
    return new Managed() {
      @Override
      public void start() throws Exception { }

      @Override
      public void stop() throws Exception {
        obj.stop();
      }
    };
  }

  /**
   * Wrapper for a managed instance that hides the start method so the managed instance is only stopped automatically by dropwizard.
   */
  public static Managed stopOnly(final life.catalogue.common.Managed obj) {
    return new Managed() {
      @Override
      public void start() throws Exception { }

      @Override
      public void stop() throws Exception {
        obj.stop();
      }
    };
  }

}
