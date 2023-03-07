package life.catalogue.matching.authorship;

import org.gbif.nameparser.api.Authorship;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.common.base.Joiner;


/**
 * A group of recombined names and their original basionym with the epithet and original publication author.
 */
public class BasionymGroup<T> {
  private static final Joiner joiner = Joiner.on("; ").skipNulls();
  private final String epithet;
  private final Authorship authorship;
  private final List<T> recombinations = new ArrayList<>();
  private T basionym;

  public BasionymGroup(String epithet, Authorship authorship) {
    this.epithet = epithet;
    this.authorship = authorship;
  }
  
  public T getBasionym() {
    return basionym;
  }
  
  public void setBasionym(T basionym) {
    this.basionym = basionym;
  }

  public void addRecombination(T recomb) {
    recombinations.add(recomb);
  }

  public List<T> getRecombinations() {
    return recombinations;
  }
  
  public boolean hasBasionym() {
    return basionym != null;
  }
  
  public boolean hasRecombinations() {
    return !recombinations.isEmpty();
  }
  
  public Authorship getAuthorship() {
    return authorship;
  }
  
  public String getEpithet() {
    return epithet;
  }
  
  public List<T> getAll() {
    var all = new ArrayList<>(recombinations);
    if (hasBasionym()) {
      all.add(basionym);
    }
    return all;
  }

  public int size() {
    return recombinations.size() + (hasBasionym() ? 1 : 0);
  }

  public boolean isEmpty() {
    return size() < 1;
  }

  @Override
  public int hashCode() {
    return Objects.hash(basionym, recombinations, epithet, authorship);
  }
  
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final BasionymGroup other = (BasionymGroup) obj;
    return Objects.equals(this.basionym, other.basionym)
        && Objects.equals(this.recombinations, other.recombinations)
        && Objects.equals(this.epithet, other.epithet)
        && Objects.equals(this.authorship, other.authorship);
  }
  
  @Override
  public String toString() {
    return "BasionymGroup{" + epithet + ' ' + authorship + " | " +
        basionym + ": " + joiner.join(recombinations) + '}';
  }
}
