package life.catalogue.api.vocab;

public enum MatchType {

  /**
   * The canonical name and authorship (if given) matches exactly
   */
  EXACT(null),
  
  /**
   * The name matches an orthographic variant of the name, authorship and/or rank (for family and above)
   * which is considered to be the same name still.
   */
  VARIANT(Issue.NAME_MATCH_VARIANT),
  
  /**
   * No existing name matching, but the name was newly inserted.
   */
  INSERTED(Issue.NAME_MATCH_INSERTED),
  
  /**
   * The name matched several names and could not be clearly disambiguated.
   * Usually only happens for canonical monomials without authorship.
   */
  AMBIGUOUS(Issue.NAME_MATCH_AMBIGUOUS),
  
  /**
   * No matching name.
   */
  NONE(Issue.NAME_MATCH_NONE);
  
  public final Issue issue;
  
  MatchType(Issue issue) {
    this.issue = issue;
  }
}
