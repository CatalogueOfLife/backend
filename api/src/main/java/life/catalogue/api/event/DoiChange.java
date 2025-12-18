package life.catalogue.api.event;

import com.fasterxml.jackson.annotation.JsonIgnore;

import life.catalogue.api.model.DOI;

import java.util.Objects;

import static life.catalogue.api.event.DoiChange.DoiEventType.*;

/**
 * Indicates that DOI registered with DataCite needs to be changed.
 * Either a DOI needs to be newly created, it's metadata updated or it needs to be deleted.
 */
public class DoiChange implements Event {
  private DOI doi;
  private DoiEventType type;

  public enum DoiEventType {
    CREATE, UPDATE, DELETE, PUBLISH
  }

  /**
   * This will trigger the creation of a draft DOI in DataCite.
   * In case the dataset is already public, it will also be published!
   */
  public static DoiChange create(DOI doi) {
    return new DoiChange(doi, CREATE);
  }
  public static DoiChange delete(DOI doi) {
    return new DoiChange(doi, DELETE);
  }
  public static DoiChange update(DOI doi) {
    return new DoiChange(doi, UPDATE);
  }
  public static DoiChange publish(DOI doi) {
    return new DoiChange(doi, PUBLISH);
  }

  public DoiChange() {
  }

  public DoiChange(DOI doi, DoiEventType type) {
    this.doi = doi;
    this.type = type;
  }

  public DOI getDoi() {
    return doi;
  }

  public DoiEventType getType() {
    return type;
  }

  @JsonIgnore
  public boolean isDelete(){
    return type == DELETE;
  }

  @JsonIgnore
  public boolean isCreate(){
    return type ==  CREATE;
  }

  @JsonIgnore
  public boolean isUpdate(){
    return type ==  UPDATE;
  }

  @JsonIgnore
  public boolean isPublish(){
    return type ==  PUBLISH;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DoiChange doiChange)) return false;

    return Objects.equals(doi, doiChange.doi) &&
      type == doiChange.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(doi, type);
  }
}
