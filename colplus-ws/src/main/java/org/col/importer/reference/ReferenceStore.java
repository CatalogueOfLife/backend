package org.col.importer.reference;

import org.col.api.model.Reference;

/**
 *
 */
public interface ReferenceStore {
  
  /**
   * Stores a new references and assigns it a unique key
   */
  boolean create(Reference r);

  /**
   * @return queue of all references
   */
  Iterable<Reference> refList();
  
  /**
   * Looks up a stored reference by the identifier given in the source
   */
  Reference refById(String id);
  
  Reference refByCitation(String citation);
}
