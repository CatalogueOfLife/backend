package life.catalogue.api.model;

import java.util.List;
import java.util.Objects;

/**
 * Even though ArchivedNameUsage inherits from NameUsageBase, not all properties are persisted.
 * Especially there are no sector & verbatim keys and modified/created timestamps.
 * See ArchivedNameUsageMapper for details.
 *
 * Otherwise this class is meant to store all core literal properties and not just ID links out, e.g.
 * it adds publishedIn, acceptedName & the classification hierarchy
 *
 */
public class ArchivedNameUsage extends NameUsageBase {
  private String acceptedName; // parentId holds the accepted name usage id
  private String basionym;
  private List<SimpleName> classification;
  private String publishedIn; // citation
  private Boolean extinct;
  private Integer firstReleaseKey; // release datasetKey
  private Integer lastReleaseKey; // release datasetKey

  public ArchivedNameUsage() {
  }

  public ArchivedNameUsage(NameUsageBase other) {
    super(other);
  }

  public ArchivedNameUsage(ArchivedNameUsage other) {
    super(other);
    this.acceptedName = other.acceptedName;
    this.basionym = other.basionym;
    this.classification = other.classification;
    this.publishedIn = other.publishedIn;
    this.extinct = other.extinct;
    this.firstReleaseKey = other.firstReleaseKey;
    this.lastReleaseKey = other.lastReleaseKey;
  }

  public String getAcceptedName() {
    return acceptedName;
  }

  public void setAcceptedName(String acceptedName) {
    this.acceptedName = acceptedName;
  }

  public String getBasionym() {
    return basionym;
  }

  public void setBasionym(String basionym) {
    this.basionym = basionym;
  }

  public List<SimpleName> getClassification() {
    return classification;
  }

  public void setClassification(List<SimpleName> classification) {
    this.classification = classification;
  }

  public String getPublishedIn() {
    return publishedIn;
  }

  public void setPublishedIn(String publishedIn) {
    this.publishedIn = publishedIn;
  }

  public Boolean getExtinct() {
    return extinct;
  }

  public void setExtinct(Boolean extinct) {
    this.extinct = extinct;
  }

  public Integer getFirstReleaseKey() {
    return firstReleaseKey;
  }

  public void setFirstReleaseKey(Integer firstReleaseKey) {
    this.firstReleaseKey = firstReleaseKey;
  }

  public Integer getLastReleaseKey() {
    return lastReleaseKey;
  }

  public void setLastReleaseKey(Integer lastReleaseKey) {
    this.lastReleaseKey = lastReleaseKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ArchivedNameUsage)) return false;
    if (!super.equals(o)) return false;
    ArchivedNameUsage that = (ArchivedNameUsage) o;
    return Objects.equals(acceptedName, that.acceptedName)
           && Objects.equals(basionym, that.basionym)
           && Objects.equals(classification, that.classification)
           && Objects.equals(publishedIn, that.publishedIn)
           && Objects.equals(extinct, that.extinct)
           && Objects.equals(firstReleaseKey, that.firstReleaseKey)
           && Objects.equals(lastReleaseKey, that.lastReleaseKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), acceptedName, basionym, classification, publishedIn, extinct, firstReleaseKey, lastReleaseKey);
  }
}
