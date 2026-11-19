package life.catalogue.api.model;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * A source dataset of a project that had sectors when a release was built, none of which contributed anything to it:
 * no sector of the release has it as its subject, it is not a source of the release and it did not enrich any record
 * of the release as a secondary source either.
 */
public class NonContributingSource {
  private Integer key;
  private String alias;
  private String title;
  private UUID gbifPublisherKey;
  private Set<Integer> sectorKeys = new TreeSet<>();

  public Integer getKey() {
    return key;
  }

  public void setKey(Integer key) {
    this.key = key;
  }

  public String getAlias() {
    return alias;
  }

  public void setAlias(String alias) {
    this.alias = alias;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public UUID getGbifPublisherKey() {
    return gbifPublisherKey;
  }

  public void setGbifPublisherKey(UUID gbifPublisherKey) {
    this.gbifPublisherKey = gbifPublisherKey;
  }

  /**
   * @return the keys of the project sectors of this source which contributed nothing, ordered
   */
  public Set<Integer> getSectorKeys() {
    return sectorKeys;
  }

  public void setSectorKeys(Set<Integer> sectorKeys) {
    this.sectorKeys = sectorKeys == null ? new TreeSet<>() : new TreeSet<>(sectorKeys);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NonContributingSource that)) return false;
    return Objects.equals(key, that.key) && Objects.equals(alias, that.alias) && Objects.equals(title, that.title)
      && Objects.equals(gbifPublisherKey, that.gbifPublisherKey) && Objects.equals(sectorKeys, that.sectorKeys);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, alias, title, gbifPublisherKey, sectorKeys);
  }

  @Override
  public String toString() {
    return "NonContributingSource{" + key + " " + alias + ", sectors=" + sectorKeys + '}';
  }
}
