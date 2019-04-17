package org.col.dw;

import com.google.common.base.Preconditions;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManagedCloseable implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(ManagedCloseable.class);
  private final AutoCloseable closeable;
  
  public ManagedCloseable(AutoCloseable closeable) {
    this.closeable = Preconditions.checkNotNull(closeable);
  }
  
  @Override
  public void start() throws Exception {
  
  }
  
  @Override
  public void stop() throws Exception {
    LOG.info("Shutting down {}", closeable);
    closeable.close();
  }
}
