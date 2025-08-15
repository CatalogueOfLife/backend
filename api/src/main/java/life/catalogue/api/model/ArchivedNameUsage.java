package life.catalogue.api.model;

import java.util.Arrays;
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
  private SimpleName accepted;
  private SimpleName basionym;
  private List<SimpleName> classification; // in case of synonyms the first entry is the accepted name
  private String publishedIn; // citation
  private Boolean extinct;
  private int[] releaseKeys; // release datasetKey

  public ArchivedNameUsage() {
  }

  public ArchivedNameUsage(NameUsageBase other) {
    super(other);
  }

  public ArchivedNameUsage(ArchivedNameUsage other) {
    super(other);
    this.accepted = other.accepted;
    this.basionym = other.basionym;
    this.classification = other.classification;
    this.publishedIn = other.publishedIn;
    this.extinct = other.extinct;
    this.releaseKeys = other.releaseKeys;
  }

  @Override
  public NameUsageBase copy() {
    return new ArchivedNameUsage(this);
  }

  /**
   * Expose flag for clients to tell archived usages easily apart from others
   */
  public boolean isArchived() {
    return true;
  }

  public SimpleName getAccepted() {
    return accepted;
  }

  public void setAccepted(SimpleName accepted) {
    this.accepted = accepted;
  }

  public SimpleName getBasionym() {
    return basionym;
  }

  public void setBasionym(SimpleName basionym) {
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

  public int getFirstReleaseKey() {
    return releaseKeys[0];
  }

  public int getLastReleaseKey() {
    return releaseKeys[releaseKeys.length-1];
  }

  public int[] getReleaseKeys() {
    return releaseKeys;
  }

  public void setReleaseKeys(int[] releaseKeys) {
    this.releaseKeys = releaseKeys;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ArchivedNameUsage)) return false;
    if (!super.equals(o)) return false;
    ArchivedNameUsage that = (ArchivedNameUsage) o;
    return Objects.equals(accepted, that.accepted)
           && Objects.equals(basionym, that.basionym)
           && Objects.equals(classification, that.classification)
           && Objects.equals(publishedIn, that.publishedIn)
           && Objects.equals(extinct, that.extinct)
           && Arrays.equals(releaseKeys, that.releaseKeys);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), accepted, basionym, classification, publishedIn, extinct, Arrays.hashCode(releaseKeys));
  }
}
