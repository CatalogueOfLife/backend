package life.catalogue.api.vocab;

public enum MatchType {

  /**
   * The canonical name, rank and authorship (if given) matches exactly,
   * allowing only for whitespace and punctuation differences.
   */
  EXACT,
  
  /**
   * The name matches an orthographic variant of the name, authorship and/or rank
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
  NONE,

  /**
   * A name which is not supported in the names index and can never be matched or added.
   * For example placeholder names.
   */
  UNSUPPORTED,

  /**
   * The name matched a fuzzy name, i.e. a name that is not an exact match but very similar.
   */
  FUZZY,

  /**
   * The matching alogrithm was unable to match a scientific name with sufficient confidence,
   * and matched a higher rank instead.
   */
  HIGHERRANK;
}
