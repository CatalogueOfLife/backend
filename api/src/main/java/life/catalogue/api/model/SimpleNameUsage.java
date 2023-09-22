package life.catalogue.api.model;

import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import java.util.Objects;

/**
 * A small class representing a name usage with an id. It can act as a reference to a scientific name in a dataset.
 * It combines the source usage ID with the full scientific name in order to best deal with changing identifiers in sources.
 */
public class SimpleNameUsage implements NameUsageCore {

  private String id;
  private String parentId;
  private Name name;
  private String phrase;
  private TaxonomicStatus status;
  private Origin origin;

  @Override
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getParentId() {
    return parentId;
  }

  public void setParentId(String parentId) {
    this.parentId = parentId;
  }

  public Name getName() {
    return name;
  }

  public void setName(Name name) {
    this.name = name;
  }

  @Override
  public Rank getRank() {
    return name.getRank();
  }

  public String getPhrase() {
    return phrase;
  }

  public void setPhrase(String phrase) {
    this.phrase = phrase;
  }

  @Override
  public TaxonomicStatus getStatus() {
    return status;
  }

  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }

  public Origin getOrigin() {
    return origin;
  }

  public void setOrigin(Origin origin) {
    this.origin = origin;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SimpleNameUsage)) return false;
    SimpleNameUsage that = (SimpleNameUsage) o;
    return Objects.equals(id, that.id)
           && Objects.equals(parentId, that.parentId)
           && Objects.equals(name, that.name)
           && Objects.equals(phrase, that.phrase)
           && status == that.status
           && origin == that.origin;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, parentId, name, phrase, status, origin);
  }
}
