package org.col.api;

import org.col.api.vocab.NomenclaturalActType;

import java.util.Objects;

/**
 * A nomenclatural act such as a species description, type designation or conservation of a name.
 */
public class NameAct {
  private Integer key;
  private NomenclaturalActType type;

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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NameAct nameAct = (NameAct) o;
    return Objects.equals(key, nameAct.key) &&
        type == nameAct.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, type);
  }
}
