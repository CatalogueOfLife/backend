package org.col.config;

/**
 * AutoCloseable interface not throwing any checked exception.
 */
public interface AutoCloseableRuntime extends AutoCloseable {

  @Override
  void close();
}
