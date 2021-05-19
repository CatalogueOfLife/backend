package life.catalogue.api.event;

import life.catalogue.api.model.DOI;

/**
 * Indicates that the metadata of a DOI in DataCite needs to be updated.
 */
public class DoiChange {
  private final DOI doi;
  private final boolean delete;

  public static DoiChange delete(DOI doi) {
    return new DoiChange(doi, true);
  }
  public static DoiChange change(DOI doi) {
    return new DoiChange(doi, false);
  }

  public DoiChange(DOI doi, boolean delete) {
    this.doi = doi;
    this.delete = delete;
  }

  public DOI getDoi() {
    return doi;
  }

  public boolean isDelete() {
    return delete;
  }
}
