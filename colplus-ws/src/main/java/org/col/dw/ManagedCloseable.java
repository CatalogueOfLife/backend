package org.col.dw;

import com.google.common.base.Preconditions;
import io.dropwizard.lifecycle.Managed;

public class ManagedCloseable implements Managed {
  private final AutoCloseable closeable;
  
  public ManagedCloseable(AutoCloseable closeable) {
    this.closeable = Preconditions.checkNotNull(closeable);
  }
  
  @Override
  public void start() throws Exception {
  
  }
  
  @Override
  public void stop() throws Exception {
    closeable.close();
  }
}
