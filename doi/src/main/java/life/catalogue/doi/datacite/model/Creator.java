package life.catalogue.doi.datacite.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

import javax.validation.constraints.NotNull;

public class Creator {

  @NotNull
  protected String name;
  protected NameType nameType;
  protected String givenName;
  protected String familyName;
  protected List<NameIdentifier> nameIdentifiers;
  protected List<Affiliation> affiliation;

  public Creator() {
  }

  public Creator(@NotNull String name, NameType nameType) {
    this.name = name;
    this.nameType = nameType;
  }

  public Creator(String givenName, String familyName) {
    this.givenName = givenName;
    this.familyName = familyName;
    this.nameType = NameType.PERSONAL;
  }

  public Creator(String givenName, String familyName, String orcid) {
    this(givenName, familyName);
    if (orcid != null) {
      this.nameIdentifiers = List.of(NameIdentifier.orcid(orcid));
    }
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public NameType getNameType() {
    return nameType;
  }

  public void setNameType(NameType nameType) {
    this.nameType = nameType;
  }

  public String getGivenName() {
    return givenName;
  }

  public void setGivenName(String givenName) {
    this.givenName = givenName;
  }

  public String getFamilyName() {
    return familyName;
  }

  public void setFamilyName(String familyName) {
    this.familyName = familyName;
  }

  public List<NameIdentifier> getNameIdentifiers() {
    return nameIdentifiers;
  }

  public void setNameIdentifiers(List<NameIdentifier> nameIdentifiers) {
    this.nameIdentifiers = nameIdentifiers;
  }

  public List<Affiliation> getAffiliation() {
    return affiliation;
  }

  public void setAffiliation(List<Affiliation> affiliation) {
    this.affiliation = affiliation;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Creator)) return false;
    Creator creator = (Creator) o;
    return Objects.equals(name, creator.name) && nameType == creator.nameType && Objects.equals(givenName, creator.givenName) && Objects.equals(familyName, creator.familyName) && Objects.equals(nameIdentifiers, creator.nameIdentifiers) && Objects.equals(affiliation, creator.affiliation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, nameType, givenName, familyName, nameIdentifiers, affiliation);
  }

  @Override
  public String toString() {
    return "Creator{" +
           "name='" + name + '\'' +
           ", nameType=" + nameType +
           ", givenName='" + givenName + '\'' +
           ", familyName='" + familyName + '\'' +
           ", nameIdentifier=" + nameIdentifiers +
           ", affiliation=" + affiliation +
           '}';
  }
}
