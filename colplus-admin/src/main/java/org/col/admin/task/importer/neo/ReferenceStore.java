package org.col.admin.task.importer.neo;

import org.col.api.model.Reference;

/**
 *
 */
public interface ReferenceStore {

  /**
   * Stores a new references and assigns it a unique key
   */
  Reference put(Reference r);

  /**
   * @return list of all references
   */
  Iterable<Reference> refList();

  /**
   * Looks up a stored reference by the internal store assigned key
   */
  Reference refByKey(int key);

  /**
   * Looks up a stored reference by the identifier given in the source
   */
  Reference refById(String key);

}
