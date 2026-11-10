package life.catalogue.matching;

/**
 * The answer to "are these the same?": equal, different or unknown, with a probable shade of equal and different for
 * authorships.
 *
 * Only the authorship comparison returns the two probable values, for authors cited in different roles, one without
 * brackets and the other with: {@link #PROBABLY_DIFFERENT} for two different original authors, {@code Harrington, 1899}
 * against {@code (Kirby, 1837)}, and {@link #PROBABLY_EQUAL} for one side citing nothing but the other side's basionym
 * author, {@code L.} against {@code (L.) Mill.} - the same original author, but often two names that both exist, an
 * original name and a later transfer of a basionym of the same author from another genus.
 *
 * Callers decide how much doubt they can afford. A check for {@code == EQUAL} or {@code == DIFFERENT} only acts on a
 * certain answer and reads a probable one like {@link #UNKNOWN}; {@link #isEqual()} and {@link #isDifferent()} also
 * accept the probable one. Merges and strict matching join two names only when they are certainly equal, while the
 * stable identifiers never share an identifier across a probable difference, see {@code NameIdentity}.
 */
public enum Equality {
  EQUAL,
  DIFFERENT,
  UNKNOWN,
  PROBABLY_DIFFERENT,
  PROBABLY_EQUAL;

  /**
   * Combines two answers about parts of the same thing: the more different answer wins, from DIFFERENT over
   * PROBABLY_DIFFERENT and PROBABLY_EQUAL down to EQUAL, and an unknown part leaves the other answer as it is.
   */
  public Equality and(Equality other) {
    if (this == UNKNOWN) {
      return other;
    }
    if (other == UNKNOWN) {
      return this;
    }
    return difference() >= other.difference() ? this : other;
  }

  // how different a known answer is, from EQUAL upwards
  private int difference() {
    return switch (this) {
      case EQUAL -> 0;
      case PROBABLY_EQUAL -> 1;
      case PROBABLY_DIFFERENT -> 2;
      case DIFFERENT -> 3;
      case UNKNOWN -> throw new IllegalStateException("UNKNOWN has no difference");
    };
  }

  /**
   * @return true for a certain or a probable equality
   */
  public boolean isEqual() {
    return this == EQUAL || this == PROBABLY_EQUAL;
  }

  /**
   * @return true for a certain or a probable difference
   */
  public boolean isDifferent() {
    return this == DIFFERENT || this == PROBABLY_DIFFERENT;
  }
}
