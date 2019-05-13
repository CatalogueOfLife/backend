package org.col.common.lang;

/**
 * AutoCloseable interface not throwing any checked exception.
 */
public interface AutoCloseableRuntime extends AutoCloseable {

  @Override
  void close();
}
