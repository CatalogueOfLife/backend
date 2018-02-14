package org.col.admin.task.importer;

/**
 *
 */
public interface NeoInserter {

  InsertMetadata insertAll() throws NormalizationFailedException;

}
