package life.catalogue.api.search;

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
}
