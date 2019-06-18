package org.col.api.vocab;

public enum EstimateType {
  /**
   * Estimate number of all non fossil species described with a linnean name.
   */
  DESCRIBED_SPECIES_LIVING,
  
  /**
   * Estimate number of all fossil species described with a linnean name.
   */
  DESCRIBED_SPECIES_FOSSIL,
  
  /**
   * Estimate number of all species including both described and yet to be discovered species.
   */
  ESTIMATED_SPECIES
}
