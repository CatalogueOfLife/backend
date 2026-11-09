package life.catalogue.api.event;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A dataset was created, updated or deleted.
 * It carries the facts about the dataset after the change that listeners need to keep their caches right, not the
 * dataset itself: a listener that needs more reloads it.
 */
public class DatasetChanged implements Event {
  public EventType type;
  public int key;
  /** the user who made the change */
  public int user;
  public DatasetOrigin origin;
  public Integer sourceKey;
  public UUID publisherKey;
  public Integer createdBy;
  /** true if the dataset is deleted after the change */
  public boolean deleted;
  public boolean privat;
  /** true if the change switched the private flag */
  public boolean privatChanged;
  /** the users with access rights to a deleted dataset, whose cached permissions are outdated */
  public Set<String> usernamesToInvalidate = new HashSet<>();

  public DatasetChanged() {
  }

  private DatasetChanged(EventType type, Dataset d, int user) {
    this.type = type;
    this.key = Objects.requireNonNull(d.getKey(), "dataset key required");
    this.user = user;
    this.origin = d.getOrigin();
    this.sourceKey = d.getSourceKey();
    this.publisherKey = d.getGbifPublisherKey();
    this.createdBy = d.getCreatedBy();
    this.deleted = d.getDeleted() != null;
    this.privat = d.isPrivat();
  }

  public static DatasetChanged created(Dataset d, int user) {
    return new DatasetChanged(EventType.CREATE, d, user);
  }

  public static DatasetChanged changed(Dataset d, Dataset old, int user) {
    var e = new DatasetChanged(EventType.UPDATE, d, user);
    e.privatChanged = old != null && old.isPrivat() != d.isPrivat();
    return e;
  }

  /**
   * @param d the dataset before it was deleted
   */
  public static DatasetChanged deleted(Dataset d, int user) {
    var e = new DatasetChanged(EventType.DELETE, d, user);
    e.deleted = true;
    return e;
  }

  @JsonIgnore
  public boolean isDeletion(){
    return type == EventType.DELETE;
  }

  @JsonIgnore
  public boolean isCreated(){
    return type == EventType.CREATE;
  }

  @JsonIgnore
  public boolean isUpdated(){
    return type == EventType.UPDATE;
  }

  /**
   * @return true if the change made a private dataset public
   */
  @JsonIgnore
  public boolean isMadePublic(){
    return isUpdated() && privatChanged && !privat;
  }

  @JsonIgnore
  public boolean isRelease(){
    return origin != null && origin.isRelease();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DatasetChanged that)) return false;
    return key == that.key && user == that.user && deleted == that.deleted && privat == that.privat
      && privatChanged == that.privatChanged && type == that.type && origin == that.origin
      && Objects.equals(sourceKey, that.sourceKey) && Objects.equals(publisherKey, that.publisherKey)
      && Objects.equals(createdBy, that.createdBy) && Objects.equals(usernamesToInvalidate, that.usernamesToInvalidate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, key, user, origin, sourceKey, publisherKey, createdBy, deleted, privat, privatChanged, usernamesToInvalidate);
  }

  @Override
  public String toString() {
    return "DatasetChanged{" + type + " " + key + " " + origin + (privatChanged ? " privat=" + privat : "") + " by " + user + '}';
  }
}
