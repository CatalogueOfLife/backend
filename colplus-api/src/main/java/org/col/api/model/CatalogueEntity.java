package org.col.api.model;

/**
 * Global entity that is keyed on a simple, globally unique integer but which is in addition
 * scoped to a datasetKey
 */
public interface CatalogueEntity extends GlobalEntity, DatasetEntity {
  
}
