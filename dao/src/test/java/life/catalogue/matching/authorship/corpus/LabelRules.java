package life.catalogue.matching.authorship.corpus;

/**
 * Turns the counts of a key pair into a label. The label says whether the two citations stand for the same
 * nomenclatural act on a name, which is what production asks - not whether they are the same person.
 * <p>
 * The labels are silver, not gold. See docs/2026-09-19-author-comparison-corpus.md for the reasoning behind
 * the rules and their defaults.
 */
public class LabelRules {
  public static final LabelRules DEFAULT = new LabelRules(3, 0.05, 2, 0.2);

  public enum Label {
    /** the same act cited in two ways */
    SAME,
    /** two names a dataset keeps apart and gives different years */
    DIFF,
    /** kept apart by a dataset, but with nothing to tell a homonym from its duplicate record. Reported, never measured */
    DUBIOUS,
    /** too little evidence either way */
    UNLABELLED
  }

  public enum Source {
    /** recurring across datasets */
    CROSS,
    /** across datasets on few names, but with agreeing years */
    CROSS_YEAR,
    /** inside one dataset with conflicting years */
    INTRA_YEARDIFF,
    /** inside one dataset without a year to compare: a homonym, or as often a second record of the name */
    INTRA_NOYEAR,
    /** inside one dataset with the same year: an isonym or a duplicate record */
    INTRA_YEARAGREE,
    /** inside one dataset, but also cited alike across datasets */
    INTRA_CROSS
  }

  /**
   * @param weight number of groups backing the label
   */
  public record Labelled(Label label, Source source, int weight) {
  }

  private final int minSupport;
  private final double minRatio;
  private final int minYearAgree;
  private final double maxConflictShare;

  /**
   * @param minSupport       names a pair has to recur on to be the same ...
   * @param minRatio         ... and the share of the rarer key's names that has to be
   * @param minYearAgree     names with agreeing years that make a pair the same regardless of the above
   * @param maxConflictShare share of conflicting years a pair may have and still be the same
   */
  public LabelRules(int minSupport, double minRatio, int minYearAgree, double maxConflictShare) {
    this.minSupport = minSupport;
    this.minRatio = minRatio;
    this.minYearAgree = minYearAgree;
    this.maxConflictShare = maxConflictShare;
  }

  public Labelled label(PairStat s) {
    boolean fewConflicts = s.yearConflict() <= maxConflictShare * (s.support() + s.yearConflict());
    if (fewConflicts && s.support() >= minSupport && s.ratio() >= minRatio) {
      return new Labelled(Label.SAME, Source.CROSS, s.support());
    }
    if (fewConflicts && s.yearAgree() >= minYearAgree) {
      return new Labelled(Label.SAME, Source.CROSS_YEAR, s.support());
    }
    if (s.intra() == 0) {
      return new Labelled(Label.UNLABELLED, Source.CROSS, s.support());
    }
    if (s.intraYearAgree() > 0) {
      return new Labelled(Label.DUBIOUS, Source.INTRA_YEARAGREE, s.intra());
    }
    if (s.support() > 0) {
      return new Labelled(Label.DUBIOUS, Source.INTRA_CROSS, s.intra());
    }
    if (s.intraYearDiff() == 0) {
      // nothing tells a homonym from a second record of the same name
      return new Labelled(Label.DUBIOUS, Source.INTRA_NOYEAR, s.intra());
    }
    return new Labelled(Label.DIFF, Source.INTRA_YEARDIFF, s.intra());
  }

  @Override
  public String toString() {
    return "minSupport=" + minSupport + ", minRatio=" + minRatio
      + ", minYearAgree=" + minYearAgree + ", maxConflictShare=" + maxConflictShare;
  }
}
