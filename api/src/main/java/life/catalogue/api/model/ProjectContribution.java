package life.catalogue.api.model;

import java.util.*;

import org.jetbrains.annotations.NotNull;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

public class ProjectContribution extends TreeSet<ProjectContribution.Contributor> {

  static class Contributor extends Agent implements Comparable<Agent> {
    static final Comparator<Agent> COMP = Comparator.comparing(Agent::getFamily, nullsFirst(naturalOrder()))
        .thenComparing(Agent::getFamily, nullsFirst(naturalOrder()));

    public final Map<Integer, String> sources = new HashMap<>();

    public Contributor() {
    }

    public Contributor(Agent p) {
      super(p);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      Agent that = (Agent) o;
      return Objects.equals(getFamily(), that.getFamily()) &&
             Objects.equals(getGiven(), that.getGiven());
    }

    @Override
    public int hashCode() {
      return Objects.hash(getFamily(), getGiven());
    }

    @Override
    public int compareTo(@NotNull Agent o) {
      return COMP.compare(this, o);
    }
  }

  public void add(Dataset d) {
    // we do not include the contact - its not a contributor and often void of a name
    if (d.getCreator() != null) {
      d.getCreator().forEach(this::add);
    }
    if (d.getEditor() != null) {
      d.getEditor().forEach(this::add);
    }
    if (d.getContributor() != null) {
      d.getContributor().forEach(this::add);
    }
    add (d.getPublisher());
  }

  public void add(Agent p) {
    if (p != null && p.getName() != null) {
      add(new Contributor(p));
    }
  }
}
