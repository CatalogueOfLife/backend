package org.col.api;

import org.col.api.vocab.NomenclaturalActType;
import org.col.api.vocab.NomenclaturalStatus;

import java.util.Objects;

/**
 * A nomenclatural act such as a species description, type designation or conservation of a name.
 */
public class NameAct {
  private Integer key;
  private NomenclaturalActType type;

  /**
   * The new status established through this act.
   */
  private NomenclaturalStatus status;

  private Integer nameKey;
  private Integer relatedNameKey;
  private Integer referenceKey;

  public Integer getKey() {
    return key;
  }

  public void setKey(Integer key) {
    this.key = key;
  }

  public NomenclaturalActType getType() {
    return type;
  }

  public void setType(NomenclaturalActType type) {
    this.type = type;
  }

  public NomenclaturalStatus getStatus() {
    return status;
  }

  public void setStatus(NomenclaturalStatus status) {
    this.status = status;
  }

  public Integer getNameKey() {
    return nameKey;
  }

  public void setNameKey(Integer nameKey) {
    this.nameKey = nameKey;
  }

  public Integer getRelatedNameKey() {
    return relatedNameKey;
  }

  public void setRelatedNameKey(Integer relatedNameKey) {
    this.relatedNameKey = relatedNameKey;
  }

  public Integer getReferenceKey() {
    return referenceKey;
  }

  public void setReferenceKey(Integer referenceKey) {
    this.referenceKey = referenceKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NameAct nameAct = (NameAct) o;
    return Objects.equals(key, nameAct.key) &&
        type == nameAct.type &&
        status == nameAct.status &&
        Objects.equals(nameKey, nameAct.nameKey) &&
        Objects.equals(relatedNameKey, nameAct.relatedNameKey) &&
        Objects.equals(referenceKey, nameAct.referenceKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, type, status, nameKey, relatedNameKey, referenceKey);
  }
}
