package org.col.admin.importer.neo.printer;

import org.col.admin.importer.neo.traverse.StartEndHandler;

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
