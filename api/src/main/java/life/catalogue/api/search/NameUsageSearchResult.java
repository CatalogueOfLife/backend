package life.catalogue.api.search;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.Synonym;
import life.catalogue.api.model.Taxon;

public class NameUsageSearchResult extends NameUsageWrapper {
  private Double score;

  public NameUsageSearchResult() {
  }

  public NameUsageSearchResult(NameUsageWrapper nuw) {
    super(nuw);
    if (getUsage() instanceof Synonym syn) { // a synonym
      var acc = new Taxon(getParent());
      syn.setAccepted(acc);
    }
  }

  public Double getScore() {
    return score;
  }

  public void setScore(Double score) {
    this.score = score;
  }
}
