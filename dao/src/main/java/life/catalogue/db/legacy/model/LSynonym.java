package life.catalogue.db.legacy.model;

import java.util.Objects;

public class LSynonym extends LSpeciesName {
  private LSpeciesName accepted_name;

  public LSpeciesName getAccepted_name() {
    return accepted_name;
  }

  public void setAccepted_name(LSpeciesName accepted_name) {
    this.accepted_name = accepted_name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LSynonym)) return false;
    if (!super.equals(o)) return false;
    LSynonym lSynonym = (LSynonym) o;
    return Objects.equals(accepted_name, lSynonym.accepted_name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), accepted_name);
  }
}
