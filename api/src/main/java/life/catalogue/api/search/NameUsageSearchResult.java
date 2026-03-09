package life.catalogue.api.search;

import life.catalogue.api.model.SimpleName;

public class NameUsageSearchResult extends NameUsageWrapper {
  private Double score;
  private SimpleName accepted;

  public NameUsageSearchResult() {
  }

  public NameUsageSearchResult(NameUsageWrapper nuw) {
    super(nuw);
    if (getUsage().isSynonym()) { // a synonym
      accepted = nuw.getParent();
    }
  }

  public Double getScore() {
    return score;
  }

  public void setScore(Double score) {
    this.score = score;
  }

  public SimpleName getAccepted() {
    return accepted;
  }
}
