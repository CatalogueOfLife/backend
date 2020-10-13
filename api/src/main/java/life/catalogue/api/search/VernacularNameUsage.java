package life.catalogue.api.search;

import life.catalogue.api.model.VernacularName;

import java.util.Objects;

/**
 * A merger of a simple name usage with a vernacular name to use as search results.
 */
public class VernacularNameUsage extends VernacularName {
  private String taxonID;

  public String getTaxonID() {
    return taxonID;
  }

  public void setTaxonID(String taxonID) {
    this.taxonID = taxonID;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof VernacularNameUsage)) return false;
    if (!super.equals(o)) return false;
    VernacularNameUsage that = (VernacularNameUsage) o;
    return Objects.equals(taxonID, that.taxonID);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), taxonID);
  }
}
