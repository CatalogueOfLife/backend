package life.catalogue.api.model;

import life.catalogue.api.vocab.TypeStatus;

import java.util.Objects;

/**
 * A read only, name scoped view of a type specimen: the name, the status the specimen has for that name
 * and the specimen itself, which can typify further names.
 * Not persisted, the link lives in {@link TypeMaterial#getNames()}.
 */
public class TypeDesignation extends TypifiedName {
  private TypeMaterial typeMaterial;

  public TypeDesignation() {
  }

  public TypeDesignation(String nameId, TypeStatus status, TypeMaterial typeMaterial) {
    super(nameId, status);
    this.typeMaterial = typeMaterial;
  }

  /**
   * @return the designation of the given specimen for the given name or null if the specimen does not typify it
   */
  public static TypeDesignation of(String nameId, TypeMaterial tm) {
    TypifiedName tn = tm.getName(nameId);
    return tn == null ? null : new TypeDesignation(nameId, tn.getStatus(), tm);
  }

  public TypeMaterial getTypeMaterial() {
    return typeMaterial;
  }

  public void setTypeMaterial(TypeMaterial typeMaterial) {
    this.typeMaterial = typeMaterial;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    TypeDesignation that = (TypeDesignation) o;
    return Objects.equals(typeMaterial, that.typeMaterial);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), typeMaterial);
  }
}
