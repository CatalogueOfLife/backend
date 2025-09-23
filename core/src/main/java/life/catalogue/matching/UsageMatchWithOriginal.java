package life.catalogue.matching;

import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameClassified;

public class UsageMatchWithOriginal extends UsageMatch {
  public final SimpleNameClassified<? extends SimpleName> original;
  public final IssueContainer issues;
  public final Long line;

  public UsageMatchWithOriginal(UsageMatch match, IssueContainer issues, SimpleNameClassified<? extends SimpleName> original, Long line) {
    super(match);
    this.original = original;
    this.issues = issues;
    this.line = line;
  }
}
