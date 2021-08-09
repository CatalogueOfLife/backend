package life.catalogue.doi.datacite.model;

import life.catalogue.api.model.Agent;

import java.util.Objects;

/**
 * Uniquely identifies a creator or contributor, according to various identifier schemes.
 */
public class Affiliation {

  private String name;
  private String affiliationIdentifier;
  private String affiliationIdentifierScheme;

  public Affiliation() {
  }

  public Affiliation(String name) {
    this.name = name;
  }

  public Affiliation(Agent a) {
    if (a.getOrganisation() == null) {
      throw new IllegalArgumentException("Agent has no organisation");
    }
    var sb = new StringBuilder();
    if (a.getDepartment() != null) {
      sb.append(a.getDepartment());
      sb.append(", ");
    }
    sb.append(a.getOrganisation());
    this.name = sb.toString();
    if (a.getRorid() != null) {
      this.affiliationIdentifier = a.getRorid();
      this.affiliationIdentifierScheme = "RORID";
    }
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getAffiliationIdentifier() {
    return affiliationIdentifier;
  }

  public void setAffiliationIdentifier(String affiliationIdentifier) {
    this.affiliationIdentifier = affiliationIdentifier;
  }

  public String getAffiliationIdentifierScheme() {
    return affiliationIdentifierScheme;
  }

  public void setAffiliationIdentifierScheme(String affiliationIdentifierScheme) {
    this.affiliationIdentifierScheme = affiliationIdentifierScheme;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Affiliation)) return false;
    Affiliation that = (Affiliation) o;
    return Objects.equals(name, that.name)
           && Objects.equals(affiliationIdentifier, that.affiliationIdentifier)
           && Objects.equals(affiliationIdentifierScheme, that.affiliationIdentifierScheme);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, affiliationIdentifier, affiliationIdentifierScheme);
  }

  @Override
  public String toString() {
    return name;
  }
}
