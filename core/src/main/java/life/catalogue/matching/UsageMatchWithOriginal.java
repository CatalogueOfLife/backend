package life.catalogue.matching;

import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;

public class UsageMatchWithOriginal extends UsageMatch<SimpleNameCached> {
  public final SimpleNameClassified<? extends SimpleName> original;
  public final IssueContainer issues;

  public UsageMatchWithOriginal(UsageMatch<SimpleNameCached> match, IssueContainer issues, SimpleNameClassified<? extends SimpleName> original) {
    super(match);
    this.original = original;
    this.issues = issues;
  }
}
