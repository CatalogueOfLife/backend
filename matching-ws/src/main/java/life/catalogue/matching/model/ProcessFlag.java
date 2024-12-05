package life.catalogue.matching.model;

/**
 * Flags to indicate the outcome of certain steps in the process of matching a name/classification to a name usage.
 */
public enum ProcessFlag {

  NO_MATCH,
  /**
   * The name was matched to two name usages, with the same confidence, but different classifications.
   */
  MULTIPLE_MATCHES_SAME_CONFIDENCE,
  /**
   * Unable to match the name to any name usage in the checklist with a confidence above the min threshold.
   */
  LOW_CONFIDENCE,
  /**
   * No scientific name was supplied in the record.
   */
  NO_NAME_SUPPLIED,
  /**
   * Unable to match to the lowest common higher rank from all best equal matches
   */
  NO_LOWEST_DENOMINATOR,
}
