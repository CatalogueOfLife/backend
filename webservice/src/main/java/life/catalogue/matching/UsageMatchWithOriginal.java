package life.catalogue.matching;

import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameClassified;

public class UsageMatchWithOriginal extends UsageMatch {
  public final SimpleNameClassified<SimpleName> original;
  public final IssueContainer issues;

  public UsageMatchWithOriginal(UsageMatch match, IssueContainer issues, SimpleNameClassified<SimpleName> original) {
    super(match);
    this.original = original;
    this.issues = issues;
  }
}
