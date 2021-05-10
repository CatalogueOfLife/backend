package life.catalogue.doi.datacite.model;

import life.catalogue.api.model.DOI;

import java.util.Objects;

public class Identifier {
  public static final String DOI_TYPE = "DOI";
  private String identifier;
  private String identifierType;

  public Identifier() {
  }

  public Identifier(DOI doi) {
    this.identifier = doi.getDoiName();
    this.identifierType = DOI_TYPE;
  }

  public String getIdentifier() {
    return identifier;
  }

  public void setIdentifier(String identifier) {
    this.identifier = identifier;
  }

  public String getIdentifierType() {
    return identifierType;
  }

  public void setIdentifierType(String identifierType) {
    this.identifierType = identifierType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Identifier)) return false;
    Identifier that = (Identifier) o;
    return Objects.equals(identifier, that.identifier) && identifierType == that.identifierType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(identifier, identifierType);
  }
}
