package org.col.api;

import com.google.common.collect.Lists;

import java.util.List;

/**
 *
 */
public class TaxonInfo {

  /**
   * Key to dataset instance. Defines context of the name key.
   */
  private Dataset dataset;

  private List<VernacularName> vernacularNames = Lists.newArrayList();

  private List<Distribution> distributions = Lists.newArrayList();

  private List<Reference> references = Lists.newArrayList();
}
