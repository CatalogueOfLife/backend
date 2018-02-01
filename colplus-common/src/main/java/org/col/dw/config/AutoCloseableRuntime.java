package org.col.dw.config;

/**
 * AutoCloseable interface not throwing any checked exception.
 */
public interface AutoCloseableRuntime extends AutoCloseable {

  @Override
  void close();
}
