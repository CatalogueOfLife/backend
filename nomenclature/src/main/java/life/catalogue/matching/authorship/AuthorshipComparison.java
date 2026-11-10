package life.catalogue.matching.authorship;

import life.catalogue.matching.Equality;

/**
 * The outcome of comparing two authorships, together with the reason for it.
 *
 * The reason never changes a decision. It says which authors were lined up against each other and why they agree or
 * not, so that a changed stable identifier or a missing redirect can be explained.
 *
 * @param equality the answer, a probable one only for authors cited in different roles, with and without brackets
 * @param reason why, never null
 */
public record AuthorshipComparison(Equality equality, Reason reason) {

  public enum Reason {
    /** one side has no author or year that could be compared */
    MISSING,
    /** the same authors, also when abbreviated, spelled or transliterated differently or with years up to 11 apart */
    SAME_AUTHOR,
    /** only the years could be compared and they agree, e.g. an anonymous work against a named author */
    SAME_YEAR,
    /** one side cites the work the name was published in: {@code Natterer in Kuhl, 1817} against {@code Kuhl, 1817} */
    PUBLISHED_IN,
    /** two anonymous works */
    ANONYMOUS,
    /**
     * the same combination author, one side leaving out the basionym author in brackets:
     * {@code Suksd.} against {@code (G.S.Torr.) Suksd.}
     */
    BASIONYM_OMITTED,
    /**
     * the same original author, cited once with and once without brackets: {@code Pascoe, 1864} against
     * {@code (Pascoe, 1859)}
     */
    BRACKETS,
    /**
     * one side cites nothing but the other side's basionym author, without brackets: {@code L.} against
     * {@code (L.) Mill.}. Only {@link Equality#PROBABLY_EQUAL}: the same original author, but both names often exist,
     * an original name and a later transfer of a basionym of that author from another genus,
     * {@code Aechmea blanchetii Baker} and {@code Aechmea blanchetii (Baker) Mez}. Merges keep them apart (#1660).
     */
    BASIONYM_AUTHOR_CITED,
    /** different authors */
    DIFFERENT_AUTHOR,
    /**
     * the years differ: more than 11 apart while the authors do not differ, or not the same year where only the years
     * can be compared, {@code 1837} against {@code Kirby, 1840}
     */
    DIFFERENT_YEAR
  }

  /** nothing to compare */
  public static final AuthorshipComparison MISSING = new AuthorshipComparison(Equality.UNKNOWN, Reason.MISSING);

  public AuthorshipComparison {
    if (equality == null || reason == null) {
      throw new IllegalArgumentException("Equality and reason are required");
    }
  }

  /**
   * @return a short human readable explanation, e.g. {@code different author across brackets}
   */
  public String explain() {
    String r = reason.name().toLowerCase().replace('_', ' ');
    return equality == Equality.PROBABLY_DIFFERENT ? r + " across brackets" : r;
  }

  @Override
  public String toString() {
    return equality + " (" + explain() + ")";
  }
}
