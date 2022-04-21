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
   * Name matched to canonical name only even though it either had an authorship, different rank or is a binomial with a subgenus given, but could not be inserted.
   * Can only happen if insertion is not allowed, e.g. via external API requests.
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
