package org.col.api.model;

import java.util.Comparator;
import java.util.Objects;

import javax.validation.constraints.NotNull;

import org.gbif.nameparser.api.Rank;

/**
 * A small class representing a name with an id. It can act as a reference to a scientific name in a dataset. It combines the source ID with
 * the full scientific name in order to best deal with changing identifiers in sources.
 */
public class SimpleName implements Comparable<SimpleName> {
  private static final Comparator<SimpleName> NATURAL_ORDER_COMPARATOR =
      Comparator.comparing(SimpleName::getRank)
          .thenComparing(SimpleName::getName)
          .thenComparing(SimpleName::getAuthorship);
  
  private String id;
  
  @NotNull
  private String name;
  private String authorship;
  @NotNull
  private Rank rank;

  public SimpleName() {}

  public SimpleName(String id, String name, Rank rank) {
    this.id = id;
    this.name = name;
    this.rank = rank;
  }

  public SimpleName(String id, String name, String authorship, Rank rank) {
    this.id = id;
    this.name = name;
    this.authorship = authorship;
    this.rank = rank;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getAuthorship() {
    return authorship;
  }

  public void setAuthorship(String authorship) {
    this.authorship = authorship;
  }

  public Rank getRank() {
    return rank;
  }

  public void setRank(Rank rank) {
    this.rank = rank;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    SimpleName simpleName = (SimpleName) o;
    return Objects.equals(id, simpleName.id) &&
        Objects.equals(name, simpleName.name) &&
        Objects.equals(authorship, simpleName.authorship) &&
        rank == simpleName.rank;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, authorship, rank);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (rank != null) {
      sb.append(rank);
      sb.append(" ");
    }
    sb.append(name);
    if (authorship != null) {
      sb.append(" ");
      sb.append(authorship);
    }
    if (id != null) {
      sb.append(" [");
      sb.append(id);
      sb.append("]");
    }
    return sb.toString();
  }
  
  public int compareTo(SimpleName other){
    return NATURAL_ORDER_COMPARATOR.compare(this, other);
  }
}
