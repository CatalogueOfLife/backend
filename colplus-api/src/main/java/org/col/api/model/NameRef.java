package org.col.api.model;

import java.util.Objects;

import org.gbif.nameparser.api.Rank;

/**
 * A small class that acts as a reference to a scientific name in a dataset.
 * It combines the source ID with the full scientific name in order to best deal with changing identifiers in sources.
 */
public class NameRef {
  private String id;
  private String indexNameId;
  private String name;
  private String authorship;
  private Rank rank;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getIndexNameId() {
    return indexNameId;
  }

  public void setIndexNameId(String indexNameId) {
    this.indexNameId = indexNameId;
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
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NameRef nameRef = (NameRef) o;
    return Objects.equals(id, nameRef.id) &&
        Objects.equals(indexNameId, nameRef.indexNameId) &&
        Objects.equals(name, nameRef.name) &&
        Objects.equals(authorship, nameRef.authorship) &&
        rank == nameRef.rank;
  }

  @Override
  public int hashCode() {

    return Objects.hash(id, indexNameId, name, authorship, rank);
  }
}
