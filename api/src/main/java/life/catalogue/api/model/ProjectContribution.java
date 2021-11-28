package life.catalogue.api.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

public class ProjectContribution extends TreeSet<ProjectContribution.Contributor> {

  static class Contributor extends Agent {

    private final Map<Integer, String> sources = new HashMap<>();

    public Contributor(Agent p) {
      super(p);
    }

    public Map<Integer, String> getSources() {
      return sources;
    }

    public void addSource(final Dataset d) {
      sources.put(d.getKey(), d.getAlias());
    }

    private void addSources(final Contributor other) {
      sources.putAll(other.sources);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Contributor)) return false;
      if (!super.equals(o)) return false;
      Contributor that = (Contributor) o;
      return Objects.equals(sources, that.sources);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), sources);
    }
  }

  public void add(final Dataset d) {
    add(d, true);
  }

  /**
   * @param addSource if true declares the given dataset as a source for each added contributor
   */
  public void add(final Dataset d, boolean addSource) {
    // we do not include the contact - its not a contributor and often void of a name
    if (d.getCreator() != null) {
      d.getCreator().forEach(a -> this.add(d, a, addSource));
    }
    if (d.getEditor() != null) {
      d.getEditor().forEach(a -> this.add(d, a, addSource));
    }
    if (d.getContributor() != null) {
      d.getContributor().forEach(a -> this.add(d, a, addSource));
    }
    add(d, d.getPublisher(), addSource);
  }

  private void add(Dataset d, Agent p, boolean addSource) {
    if (p != null && p.getName() != null) {
      var c = new Contributor(p);
      if (addSource) {
        c.addSource(d);
      }
      add(c);
    }
  }

  @Override
  public boolean add(Contributor c) {
    if (c != null) {
      // prefer to reuse existing one and add the source
      for (Contributor old : this) {
        if (old.sameAs(c)) {
          old.addSources(c);
          // merge agent infos
          old.merge(c);
          return false;
        }
      }
      // we only reach here if no old instance existed
      super.add(c);
      return true;
    }
    return false;
  }
}
