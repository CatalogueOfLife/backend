package life.catalogue.db.mapper.legacy.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class LSynonym extends LSpeciesName {
  private LSpeciesName acceptedName;

  @JsonProperty("accepted_name")
  public LSpeciesName getAcceptedName() {
    return acceptedName;
  }

  public void setAcceptedName(LSpeciesName acceptedName) {
    this.acceptedName = acceptedName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LSynonym)) return false;
    if (!super.equals(o)) return false;
    LSynonym lSynonym = (LSynonym) o;
    return Objects.equals(acceptedName, lSynonym.acceptedName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), acceptedName);
  }
}
