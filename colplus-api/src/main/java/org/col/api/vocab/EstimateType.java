package org.col.api.vocab;

public enum EstimateType {
  /**
   * Estimate number of all extant species described with a linnean name.
   */
  DESCRIBED_SPECIES_LIVING,
  
  /**
   * Estimate number of all extinct species incl fossils described with a linnean name.
   */
  DESCRIBED_SPECIES_EXTINCT,
  
  /**
   * Estimate number of all species including both described and yet to be discovered species.
   */
  ESTIMATED_SPECIES
}
