package life.catalogue.api.event;

import life.catalogue.api.model.DOI;

import java.util.Objects;

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

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ChangeDoi)) return false;
    ChangeDoi changeDoi = (ChangeDoi) o;
    return delete == changeDoi.delete && Objects.equals(doi, changeDoi.doi);
  }

  @Override
  public int hashCode() {
    return Objects.hash(doi, delete);
  }
}
