package life.catalogue.api.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A simple wrapper to hold homotypic & heterotypic synonyms separately.
 * heterotypic names are presented twice - once as a complete list in heterotypic
 * and once in a list of lists that group homotypic names in heterotypicGroups
 */
public class Synonymy implements Iterable<Synonym> {
  private final List<Synonym> homotypic = new ArrayList<>();
  private final List<Synonym> heterotypic = new ArrayList<>();
  private final List<Synonym> misapplied = new ArrayList<>();
  private final List<List<Synonym>> heterotypicGroups = new ArrayList<>();

  @JsonIgnore
  public boolean isEmpty() {
    return homotypic.isEmpty() && heterotypic.isEmpty() && misapplied.isEmpty();
  }

  public List<Synonym> getHomotypic() {
    return homotypic;
  }

  public List<Synonym> getHeterotypic() {
    return heterotypic;
  }

  public List<Synonym> getMisapplied() {
    return misapplied;
  }

  public List<List<Synonym>> getHeterotypicGroups() {
    return heterotypicGroups;
  }

  @JsonIgnore
  public List<Synonym> all() {
    return Stream.concat(
      homotypic.stream(),
      Stream.concat(
        heterotypic.stream(),
        misapplied.stream()
      )
    ).collect(Collectors.toList());
  }

  @Override
  public Iterator<Synonym> iterator() {
    return all().iterator();
  }

  public int size() {
    return homotypic.size() + heterotypic.size() + misapplied.size();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Synonymy)) return false;
    Synonymy synonymy = (Synonymy) o;
    return Objects.equals(homotypic, synonymy.homotypic)
           && Objects.equals(heterotypic, synonymy.heterotypic)
           && Objects.equals(misapplied, synonymy.misapplied);
  }

  @Override
  public int hashCode() {
    return Objects.hash(homotypic, heterotypic, misapplied);
  }
}
