package life.catalogue.api.search;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.Synonym;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.vocab.TaxonomicStatus;

public class NameUsageSearchResult extends NameUsageWrapper {
  private Double score;

  public NameUsageSearchResult() {
  }

  public NameUsageSearchResult(NameUsageWrapper nuw) {
    super(nuw);
    if (getUsage() instanceof Synonym syn) { // a synonym
      var sn = getParent();
      if (sn != null) {
        // null should only happen in tests, still...
        if (sn.getStatus() == null) {
          sn.setStatus(TaxonomicStatus.ACCEPTED);
        }
        syn.setAccepted(new Taxon(sn));
      }
    }
  }

  public Double getScore() {
    return score;
  }

  public void setScore(Double score) {
    this.score = score;
  }
}
