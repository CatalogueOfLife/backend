package org.col.es.model;

import org.col.api.model.Taxon;
import org.col.api.model.TaxonVernacularUsage;
import org.col.api.vocab.TaxonomicStatus;

public class EsNameUsage extends TaxonVernacularUsage {

  private TaxonomicStatus status;
  private Taxon accepted;

  public TaxonomicStatus getStatus() {
    return status;
  }

  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }

  public Taxon getAccepted() {
    return accepted;
  }

  public void setAccepted(Taxon accepted) {
    this.accepted = accepted;
  }

}
