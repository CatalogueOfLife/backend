package life.catalogue.matching.authorship.corpus;

/**
 * Everything the corpus knows about one pair of author keys. All numbers count groups, i.e. names of one rank, never
 * datasets: several of the datasets share a lineage and would otherwise vote more than once.
 *
 * @param support        groups in which two datasets cite the name with exactly these two keys and whose years do not conflict
 * @param yearAgree      the share of support with a year on both sides, at most one apart
 * @param yearConflict   groups like support, but with years more than one apart. Not part of support
 * @param freqA          groups spanning several datasets that hold the first key
 * @param freqB          groups spanning several datasets that hold the second key
 * @param intraYearDiff  groups in which one dataset holds both keys with years more than one apart
 * @param intraNoYear    groups in which one dataset holds both keys and a year is missing
 * @param intraYearAgree groups in which one dataset holds both keys with years at most one apart
 */
public record PairStat(int support, int yearAgree, int yearConflict, int freqA, int freqB,
                       int intraYearDiff, int intraNoYear, int intraYearAgree) {

  public int intra() {
    return intraYearDiff + intraNoYear + intraYearAgree;
  }

  /**
   * @return the share of the rarer key's names on which the pair shows up
   */
  public double ratio() {
    int min = Math.min(freqA, freqB);
    return min == 0 ? 0 : (double) support / min;
  }
}
