package life.catalogue.api.vocab;

public enum MatchType {

  /**
   * The canonical name and authorship (if given) matches exactly
   */
  EXACT,
  
  /**
   * The name matches an orthographic variant of the name, authorship and/or rank (for family and above)
   * which is considered to be the same name still.
   */
  VARIANT,

  /**
   * Name matched to canonical name only, but could not be inserted.
   */
  CANONICAL,

  /**
   * The name matched several names and could not be clearly disambiguated.
   * Usually only happens for canonical monomials without authorship.
   */
  AMBIGUOUS,

  /**
   * No matching name.
   */
  NONE;

}
