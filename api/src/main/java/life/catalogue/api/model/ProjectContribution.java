package life.catalogue.api.model;

import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

public class ProjectContribution {

  static class Contributor extends Person implements Comparable<Person> {
    static final Comparator<Person> COMP = Comparator.comparing(Person::getFamilyName, nullsFirst(naturalOrder()))
        .thenComparing(Person::getFamilyName, nullsFirst(naturalOrder()));

    public final Map<Integer, String> sources = new HashMap<>();

    public Contributor() {
    }

    public Contributor(Person p) {
      super(p);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      Person that = (Person) o;
      return Objects.equals(getFamilyName(), that.getFamilyName()) &&
        Objects.equals(getGivenName(), that.getGivenName());
    }

    @Override
    public int hashCode() {
      return Objects.hash(getFamilyName(), getGivenName());
    }

    @Override
    public int compareTo(@NotNull Person o) {
      return COMP.compare(this, o);
    }
  }

  static class ContributorOrg extends Organisation implements Comparable<Organisation> {
    static final Comparator<Organisation> COMP = Comparator.comparing(Organisation::getName, nullsFirst(naturalOrder()))
      .thenComparing(Organisation::getDepartment, nullsFirst(naturalOrder()));

    public final Map<Integer, String> sources = new HashMap<>();

    public ContributorOrg(Organisation o) {
      super(o);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      ContributorOrg that = (ContributorOrg) o;
      return Objects.equals(getName(), that.getName()) &&
        Objects.equals(getDepartment(), that.getDepartment());
    }

    @Override
    public int hashCode() {
      return Objects.hash(getName(), getDepartment());
    }

    @Override
    public int compareTo(@NotNull Organisation o) {
      return COMP.compare(this, o);
    }
  }

  private final TreeSet<ContributorOrg> organisations = new TreeSet<>();
  private final TreeSet<Contributor> contributor = new TreeSet<>();

  public Set<ContributorOrg> getOrganisations() {
    return organisations;
  }

  public Set<Contributor> getContributor() {
    return contributor;
  }

  public void add(ArchivedDataset d) {
    // we do not include the contact - its not a contributor and often void of a name
    if (d.getAuthors() != null) {
      d.getAuthors().forEach(this::add);
    }
    if (d.getEditors() != null) {
      d.getEditors().forEach(this::add);
    }
    if (d.getOrganisations() != null) {
      d.getOrganisations().forEach(this::add);
    }
  }

  public void add(Person p) {
    if (p != null && p.getName() != null) {
      contributor.add(new Contributor(p));
    }
  }

  public void add(Organisation o) {
    if (o != null && !o.isEmpty()) {
      organisations.add(new ContributorOrg(o));
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProjectContribution that = (ProjectContribution) o;
    return Objects.equals(organisations, that.organisations) && Objects.equals(contributor, that.contributor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(organisations, contributor);
  }
}
