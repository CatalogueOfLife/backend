package life.catalogue.doi.datacite.model;

import javax.validation.constraints.NotNull;

import java.util.Objects;

public class Contributor extends Creator {

  @NotNull
  private ContributorType contributorType;

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
}