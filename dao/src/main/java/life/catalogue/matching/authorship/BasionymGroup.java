package life.catalogue.matching.authorship;

import org.gbif.nameparser.api.Authorship;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Joiner;


/**
 * A group of recombined names and their original basionym with the epithet and original publication author.
 * Duplicate basionyms can be given if the name is considered the same combination & rank, but with potentially minor misspellings.
 */
public class BasionymGroup<T> {
  private static final Joiner joiner = Joiner.on("; ").skipNulls();
  private final String epithet;
  private final Authorship authorship;
  private final List<T> recombinations = new ArrayList<>();
  private final List<T> basionymDuplicates = new ArrayList<>();
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

  public List<T> getBasionymDuplicates() {
    return basionymDuplicates;
  }

  public void addBasionymDuplicates(T original) {
    basionymDuplicates.add(original);
  }

  public boolean hasBasionymDuplicates() {
    return !basionymDuplicates.isEmpty();
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

  @JsonIgnore
  public List<T> getAll() {
    var all = new ArrayList<>(recombinations);
    all.addAll(basionymDuplicates);
    if (hasBasionym()) {
      all.add(basionym);
    }
    return all;
  }

  public int size() {
    return recombinations.size() + basionymDuplicates.size() + (hasBasionym() ? 1 : 0);
  }

  public boolean isEmpty() {
    return size() < 1;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BasionymGroup)) return false;
    BasionymGroup<?> that = (BasionymGroup<?>) o;
    return Objects.equals(epithet, that.epithet)
           && Objects.equals(authorship, that.authorship)
           && Objects.equals(recombinations, that.recombinations)
           && Objects.equals(basionymDuplicates, that.basionymDuplicates)
           && Objects.equals(basionym, that.basionym);
  }

  @Override
  public int hashCode() {
    return Objects.hash(epithet, authorship, recombinations, basionymDuplicates, basionym);
  }

  @Override
  public String toString() {
    return "BasionymGroup{" + epithet + ' ' + authorship + " | " +
           basionym + ": " + joiner.join(recombinations) +
           ", DUPES: " + joiner.join(basionymDuplicates) + '}';
  }
}
