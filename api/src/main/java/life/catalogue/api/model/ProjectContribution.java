package life.catalogue.api.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

public class ProjectContribution extends TreeSet<ProjectContribution.Contributor> {

  static class Contributor extends Agent {

    private final Map<Integer, String> sources = new HashMap<>();

    public Contributor(Agent p, Dataset d) {
      super(p);
      addSource(d);
    }

    public Map<Integer, String> getSources() {
      return sources;
    }

    public void addSource(final Dataset d) {
      sources.put(d.getKey(), d.getAlias());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Agent that = (Agent) o;
      return Objects.equals(getName(), that.getName());
    }

    @Override
    public int hashCode() {
      return Objects.hash(getName());
    }

  }

  public void add(final Dataset d) {
    // we do not include the contact - its not a contributor and often void of a name
    if (d.getCreator() != null) {
      d.getCreator().forEach(a -> this.add(d, a));
    }
    if (d.getEditor() != null) {
      d.getEditor().forEach(a -> this.add(d, a));
    }
    if (d.getContributor() != null) {
      d.getContributor().forEach(a -> this.add(d, a));
    }
    add(d, d.getPublisher());
  }

  public void add(Dataset d, Agent p) {
    if (p != null && p.getName() != null) {
      Contributor c = new Contributor(p, d);
      // prefer to reuse existing one and add the source
      for (Contributor old : this) {
        if (old.equals(c)) {
          old.addSource(d);
          return;
        }
      }
      add(c);
    }
  }
}
