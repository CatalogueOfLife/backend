package life.catalogue.basgroup;

import life.catalogue.api.model.FormattableName;

import org.gbif.nameparser.api.Authorship;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Joiner;

import org.gbif.nameparser.api.NomCode;

import javax.annotation.Nullable;


/**
 * A group of recombined names and their original basionym with the epithet and original publication author.
 * Duplicate basionyms can be given if the name is considered the same combination & rank, but with potentially minor misspellings.
 */
public class HomotypicGroup<T> {
  private static final Joiner joiner = Joiner.on("; ").skipNulls();
  private final NomCode code;
  private final T primary; // the primary name represented by the epithet and authorship
  private final String epithet;
  private final Authorship authorship;
  // basionym and its recombinations
  private T basionym;
  private int basionymPriority;
  private final List<T> basionymVariations = new ArrayList<>();
  private final List<T> recombinations = new ArrayList<>();
  // based on via ex author and its subsequent combinations (might already be included in aboves lists)
  private T basedOn;
  private int basedOnPriority;
  private final Set<String> basedOnNameIDs = new HashSet<>(); // identifier of names with the basedon names author as part of its ex authorship
  private final List<T> basedOnVariations = new ArrayList<>(); // orth vars of the based on name

  public HomotypicGroup(T primary, String epithet, Authorship authorship, @Nullable NomCode code) {
    this.primary = primary;
    this.epithet = epithet;
    this.authorship = authorship;
    this.code = code;
  }

  public T getPrimary() {
    return primary;
  }

  public T getBasionym() {
    return basionym;
  }
  
  public void setBasionym(T basionym) {
    this.basionym = basionym;
  }
  public void addBasionym(T obj, int basionymPriority) {
    if (basionym == null) {
      this.basionym = obj;
      this.basionymPriority = basionymPriority;
    } else if (basionymPriority < this.basionymPriority) {
      basionymVariations.add(this.basionym);
      this.basionym = obj;
      this.basionymPriority = basionymPriority;
    } else {
      basionymVariations.add(obj);
    }
  }

  public void add(T obj, FormattableName name, int prio) {
    String year;
    if (name.hasBasionymAuthorship()) {
      addRecombination(obj);
      year = name.getBasionymAuthorship().getYear();
    } else {
      addBasionym(obj, prio);
      year = name.getCombinationAuthorship().getYear();
    }
    // we add the first matched year to the reference authorship in case it does not yet exist
    // this avoids matching very different years when the main authorship does not have a year
    if (authorship.getYear() == null && year != null) {
      authorship.setYear(year);
    }
  }

  public void addRecombination(T recomb) {
    recombinations.add(recomb);
  }

  public List<T> getRecombinations() {
    return recombinations;
  }

  public List<T> getBasionymVariations() {
    return basionymVariations;
  }

  public void addBasionymVariation(T original) {
    basionymVariations.add(original);
  }

  public boolean hasBasionymVariations() {
    return !basionymVariations.isEmpty();
  }

  public boolean hasBasionym() {
    return basionym != null;
  }

  public boolean hasRecombinations() {
    return !recombinations.isEmpty();
  }

  public T getBasedOn() {
    return basedOn;
  }

  public void addBasedOn(T obj, int prio) {
    if (basedOn == null) {
      basedOn = obj;
      basedOnPriority = prio;
    } else if (prio < basedOnPriority) {
      basedOnVariations.add(basedOn);
      basedOn = obj;
      basedOnPriority = prio;
    } else {
      basedOnVariations.add(obj);
    }
  }
  public boolean hasBasedOn() {
    return basedOn != null;
  }

  public Set<String> getBasedOnNameIDs() {
    return basedOnNameIDs;
  }
  public void addBasedOnNameID(String id) {
    basedOnNameIDs.add(id);
  }

  public List<T> getBasedOnVariations() {
    return basedOnVariations;
  }

  public Authorship getAuthorship() {
    return authorship;
  }
  
  public String getEpithet() {
    return epithet;
  }

  public NomCode getCode() {
    return code;
  }

  @JsonIgnore
  public List<T> getAll() {
    var all = new ArrayList<>(recombinations);
    if (hasBasionym()) {
      all.add(basionym);
    }
    all.addAll(basionymVariations);
    if (hasBasedOn()) {
      all.add(basedOn);
    }
    all.addAll(basedOnVariations);
    return all;
  }

  public int size() {
    return recombinations.size() + basionymVariations.size() + (hasBasionym() ? 1 : 0) + basedOnVariations.size() + (hasBasedOn() ? 1 : 0);
  }

  public boolean isEmpty() {
    return size() < 1;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof HomotypicGroup)) return false;
    HomotypicGroup<?> that = (HomotypicGroup<?>) o;
    return basionymPriority == that.basionymPriority && basedOnPriority == that.basedOnPriority && code == that.code && Objects.equals(primary, that.primary) && Objects.equals(epithet, that.epithet) && Objects.equals(authorship, that.authorship) && Objects.equals(basionym, that.basionym) && Objects.equals(basionymVariations, that.basionymVariations) && Objects.equals(recombinations, that.recombinations) && Objects.equals(basedOn, that.basedOn) && Objects.equals(basedOnNameIDs, that.basedOnNameIDs) && Objects.equals(basedOnVariations, that.basedOnVariations);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, primary, epithet, authorship, basionym, basionymPriority, basionymVariations, recombinations, basedOn, basedOnPriority, basedOnNameIDs, basedOnVariations);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("HG{")
      .append(epithet)
      .append(' ')
      .append(authorship)
      .append(" | ")
      .append(basionym);
    if (!basionymVariations.isEmpty()) {
      sb.append(" [")
        .append(joiner.join(basionymVariations))
        .append("] ");
    }
    if (!basedOnVariations.isEmpty()) {
      sb.append(": ")
        .append(joiner.join(recombinations));
    }

    if (basedOn != null) {
      sb.append(" |ON| ")
        .append(basedOn);
      if (!basedOnVariations.isEmpty()) {
        sb.append(" [")
        .append(joiner.join(basedOnVariations))
        .append("] ");
      }
      if (!basedOnNameIDs.isEmpty()) {
        sb.append(": ")
          .append(joiner.join(basedOnNameIDs));
      }
    }
    return sb.toString();
  }
}
