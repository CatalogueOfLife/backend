package org.col.admin.importer.neo.printer;

import org.col.admin.importer.neo.model.RankedUsage;

/**
 * Interface to deal with hierarchical trees, receiving start & end calls
 * for each visited usage (taxon/synonym) in the tree similar to SAX parsing.
 * Name nodes are not traversed at all!
 */
public interface TreePrinter extends AutoCloseable {

  /**
   * We prefer no exceptions in close()
   */
  @Override
  void close();
  
  void start(RankedUsage u);
  
  void end(RankedUsage u);
}
