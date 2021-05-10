package life.catalogue.doi.datacite.model;

import java.util.Objects;

/**
 * Uniquely identifies an affiliation, according to various identifier schemes.
 */
public class Affiliation {

  private String name;
  private String affiliationIdentifier;
  private String affiliationIdentifierScheme;
  private String schemeURI;

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

  public String getSchemeURI() {
    return schemeURI;
  }

  public void setSchemeURI(String schemeURI) {
    this.schemeURI = schemeURI;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Affiliation)) return false;
    Affiliation that = (Affiliation) o;
    return Objects.equals(name, that.name) && Objects.equals(affiliationIdentifier, that.affiliationIdentifier) && Objects.equals(affiliationIdentifierScheme, that.affiliationIdentifierScheme) && Objects.equals(schemeURI, that.schemeURI);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, affiliationIdentifier, affiliationIdentifierScheme, schemeURI);
  }
}
