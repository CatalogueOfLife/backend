package life.catalogue.doi.datacite.model;

import java.util.Objects;

/**
 * Uniquely identifies a creator or contributor, according to various identifier schemes.
 */
public class NameIdentifier {

  private String nameIdentifier;
  private String nameIdentifierScheme;

  public static NameIdentifier orcid(String orcid) {
    NameIdentifier id = new NameIdentifier();
    id.nameIdentifier = orcid;
    id.nameIdentifierScheme = "ORCID";
    return id;
  }

  public String getNameIdentifier() {
    return nameIdentifier;
  }

  public void setNameIdentifier(String nameIdentifier) {
    this.nameIdentifier = nameIdentifier;
  }

  public String getNameIdentifierScheme() {
    return nameIdentifierScheme;
  }

  public void setNameIdentifierScheme(String nameIdentifierScheme) {
    this.nameIdentifierScheme = nameIdentifierScheme;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NameIdentifier)) return false;
    NameIdentifier that = (NameIdentifier) o;
    return Objects.equals(nameIdentifier, that.nameIdentifier) && Objects.equals(nameIdentifierScheme, that.nameIdentifierScheme);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nameIdentifier, nameIdentifierScheme);
  }
}
