package life.catalogue.api.search;

import life.catalogue.api.model.SimpleName;

public class NameUsageSearchResult extends NameUsageWrapper {
  private Double score;

  public NameUsageSearchResult() {
  }

  public NameUsageSearchResult(NameUsageWrapper nuw) {
    super(nuw);
  }

  public Double getScore() {
    return score;
  }

  public void setScore(Double score) {
    this.score = score;
  }

  public SimpleName getAccepted() {
    if (getUsage().isSynonym()) { // a synonym
      return getParent();
    }
    return null;
  }
}
