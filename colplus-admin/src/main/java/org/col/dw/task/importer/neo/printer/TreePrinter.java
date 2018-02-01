package org.col.dw.task.importer.neo.printer;

import org.col.dw.task.importer.neo.traverse.StartEndHandler;

/**
 *
 */
public interface TreePrinter extends StartEndHandler, AutoCloseable {

  /**
   * We prefer no exceptions in close()
   */
  @Override
  void close();

}
