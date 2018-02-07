package org.col.admin.task.importer;

import org.col.admin.task.importer.dwca.InsertMetadata;

import java.io.File;

/**
 *
 */
public interface NeoInserter {
  InsertMetadata insert(File dwca) throws NormalizationFailedException;
}
