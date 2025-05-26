package life.catalogue.api.event;

import life.catalogue.api.model.DOI;

/**
 * Indicates that the metadata of a DOI in DataCite needs to be updated.
 */
public class ChangeDoi implements Event {
  private DOI doi;
  private boolean delete;

  public static ChangeDoi delete(DOI doi) {
    return new ChangeDoi(doi, true);
  }
  public static ChangeDoi change(DOI doi) {
    return new ChangeDoi(doi, false);
  }

  public ChangeDoi() {
  }

  public ChangeDoi(DOI doi, boolean delete) {
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
