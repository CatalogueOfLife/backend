package life.catalogue.api.model;

import life.catalogue.api.vocab.TypeStatus;

import java.util.Objects;

/**
 * A name typified by a type specimen with the status the specimen has for that name.
 * One {@link TypeMaterial} can typify several names, each with their own status.
 */
public class TypifiedName {
  private String nameId;
  private TypeStatus status;

  public TypifiedName() {
  }

  public TypifiedName(String nameId, TypeStatus status) {
    this.nameId = nameId;
    this.status = status;
  }

  public TypifiedName(TypifiedName other) {
    this(other.nameId, other.status);
  }

  public String getNameId() {
    return nameId;
  }

  public void setNameId(String nameId) {
    this.nameId = nameId;
  }

  /**
   * The status of the type material for this name, e.g. holotype
   */
  public TypeStatus getStatus() {
    return status;
  }

  public void setStatus(TypeStatus status) {
    this.status = status;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    TypifiedName that = (TypifiedName) o;
    return Objects.equals(nameId, that.nameId) && status == that.status;
  }

  @Override
  public int hashCode() {
    return Objects.hash(nameId, status);
  }

  @Override
  public String toString() {
    return nameId + ":" + status;
  }
}
