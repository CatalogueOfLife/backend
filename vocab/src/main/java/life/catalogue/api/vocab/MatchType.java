package life.catalogue.api.vocab;

public enum MatchType implements Described {

  EXACT("The canonical name, rank and authorship (if given) matches exactly, " +
    "allowing only for whitespace and punctuation differences."),

  VARIANT("The name matches an orthographic variant of the name, authorship and/or rank " +
    "which is considered to be the same name still."),

  CANONICAL("Name matched to canonical name only even though it either had an authorship, different rank " +
    "or is a binomial with a subgenus given, but could not be inserted. " +
    "Can only happen if insertion is not allowed, e.g. via external API requests."),

  AMBIGUOUS("The name matched several names and could not be clearly disambiguated. " +
    "Usually only happens for canonical monomials without authorship."),

  NONE("No matching name."),

  UNSUPPORTED("A name which is not supported in the names index and can never be matched or added. " +
    "For example placeholder names."),

  HIGHERRANK("The matching algorithm was unable to match a scientific name with sufficient confidence, " +
    "and matched a higher rank instead.");

  private final String description;

  MatchType(String description) {
    this.description = description;
  }

  @Override
  public String getDescription() {
    return description;
  }
}
