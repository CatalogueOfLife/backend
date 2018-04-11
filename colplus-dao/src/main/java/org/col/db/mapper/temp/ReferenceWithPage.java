package org.col.db.mapper.temp;

import java.util.Objects;
import org.col.api.model.CslItemData;
import org.col.api.model.Reference;

public class ReferenceWithPage {

  private Reference reference;
  private String page;

  public String getPage() {
    return page;
  }

  public void setPage(String page) {
    this.page = page;
  }

  public Reference getReference() {
    return reference;
  }

  public void setReference(Reference reference) {
    this.reference = reference;
  }

  public Reference toReference() {
    Reference ref = getReference();
    String page = getPage();
    CslItemData csl = ref.getCsl();
    csl.setPage(page);
    return ref;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    ReferenceWithPage that = (ReferenceWithPage) o;
    return Objects.equals(reference, that.reference) && Objects.equals(page, that.page);
  }

  @Override
  public int hashCode() {
    return Objects.hash(reference, page);
  }
}
