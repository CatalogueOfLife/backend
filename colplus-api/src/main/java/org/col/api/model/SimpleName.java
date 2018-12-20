package org.col.api.model;

import java.util.Objects;

import org.gbif.nameparser.api.Rank;

/**
 * A small class representing a name with an id. It can act as a reference to a scientific name in a dataset. It combines the source ID with
 * the full scientific name in order to best deal with changing identifiers in sources.
 */
public class SimpleName {
  private String id;
  private String name;
  private String authorship;
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
    return rank + " " + name + ' ' + authorship + " [" + id + ']';
  }
}
