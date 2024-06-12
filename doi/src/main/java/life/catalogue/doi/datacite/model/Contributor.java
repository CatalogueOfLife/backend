package life.catalogue.doi.datacite.model;

import java.util.Objects;

import jakarta.validation.constraints.NotNull;

public class Contributor extends Creator {

  @NotNull
  private ContributorType contributorType;


  public Contributor() {
  }

  public Contributor(@NotNull String name, NameType nameType, ContributorType type) {
    super(name, nameType);
    this.contributorType = type;
  }

  public Contributor(String givenName, String familyName, ContributorType type) {
    super(givenName, familyName);
    this.contributorType = type;
  }

  public Contributor(String givenName, String familyName, String orcid, ContributorType type) {
    super(givenName, familyName, orcid);
    this.contributorType = type;
  }

  public ContributorType getContributorType() {
    return contributorType;
  }

  public void setContributorType(ContributorType contributorType) {
    this.contributorType = contributorType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Contributor)) return false;
    Contributor that = (Contributor) o;
    return contributorType == that.contributorType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(contributorType);
  }

  @Override
  public String toString() {
    return "Contributor{" +
           "type=" + contributorType +
           ", name='" + name + '\'' +
           ", nameType=" + nameType +
           ", givenName='" + givenName + '\'' +
           ", familyName='" + familyName + '\'' +
           ", nameIdentifier=" + nameIdentifiers +
           ", affiliation=" + affiliation +
           '}';
  }
}