package org.col.api.vocab;

/**
 * Enumeration of issues for all processed names encountered during processing.
 */
public enum NameIssue {
  /**
   * The name string could not be parsed.
   */
  UNPARSABLE,

  /**
   * The name parts contain unusual characters.
   */
  UNUSUAL_CHARACTERS,

  /**
   * At least one epithet equals "null" or "none".
   */
  NULL_EPITHET,

  /**
   * The rank of the name does not match the given name parts or suffices.
   */
  RANK_MISMATCH,

  /**
   * dwc:nomenclaturalStatus could not be interpreted
   */
  NOMENCLATURAL_STATUS_INVALID,

  /**
   * A recombination with a basionym authorship which does not match the authorship of the linked basionym.
   */
  BASIONYM_AUTHOR_MISMATCH,

  /**
   * Record has a verbatim original name (basionym) which is not unique and refers to several records.
   */
  ORIGINAL_NAME_NOT_UNIQUE,

  /**
   * Record has a original name (basionym) relationship which was derived from name & authorship comparison, but did not exist explicitly in the data.
   * This should only be flagged in programmatically generated GBIF backbone usages.
   * GBIF backbone specific issue.
   */
  ORIGINAL_NAME_DERIVED,

  /**
   * There have been more than one accepted name in a homotypical basionym group of names.
   * GBIF backbone specific issue.
   */
  CONFLICTING_BASIONYM_COMBINATION,

  /**
   * A potential orthographic variant exists in the dataset.
   */
  POTENTIAL_ORTHOGRAPHIC_VARIANT,

  /**
   * A canonical homonym exists for this name in the dataset.
   */
  HOMONYM,

  /**
   * A bi/trinomial name published earlier than the parent genus was published.
   * This might indicate that the name should rather be a recombination.
   */
  PUBLISHED_BEFORE_GENUS;

}
