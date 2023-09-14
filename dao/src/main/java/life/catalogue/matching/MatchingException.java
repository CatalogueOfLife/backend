package life.catalogue.matching;

import life.catalogue.api.model.Name;

public class MatchingException extends RuntimeException {
  public final Name name;

  public MatchingException(Name name, Throwable cause) {
    super(cause);
    this.name = name;
  }
}
