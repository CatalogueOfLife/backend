package life.catalogue.config;

import jakarta.validation.constraints.Min;

/**
 * Configuration for the names diff engine, see life.catalogue.printer.diff.
 */
public class DiffConfig {

  /**
   * The maximum number of items (removed/added/changed) returned per names diff, bounding response
   * size for pathologically large diffs. 0 = unlimited.
   */
  @Min(0)
  public int maxItems = 10_000;

  /**
   * The maximum Levenshtein distance between the two authorship-stripped, normalised canonical names
   * for a removed/added pair to be reported as a single "changed" entry rather than a separate
   * delete + insert. 0 = require identical normalised canonicals.
   */
  @Min(0)
  public int canonicalMaxDistance = 1;

  /**
   * Safety cap on the number of removed/added candidates buffered during pass 1 of the diff before
   * the pairing pass runs. Guards against out-of-memory on huge diffs.
   */
  @Min(1)
  public long maxChangedCandidates = 1_000_000L;
}
