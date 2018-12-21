package org.col.es.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.col.es.annotations.MapToType;
import org.col.es.mapping.ESDataType;
import org.gbif.nameparser.api.Rank;

/**
 * A simple rank-name tuple. When indexing NameUsages the SimpleName instance that constitute the taxon's/synonym's classification are split
 * into a list of taxon/synonym ids on the one hand and a list of monomials on the other. This allows for fast retrieval by id, because no
 * nested query on subdocuments is necessary this way.
 */
public class Monomial {

  private Rank rank;
  private String name;

  @JsonCreator
  public Monomial(@JsonProperty("rank") Rank rank, @JsonProperty("name") String name) {
    this.rank = rank;
    this.name = name;
  }

  @MapToType(ESDataType.KEYWORD)
  public Rank getRank() {
    return rank;
  }

  public String getName() {
    return name;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, rank);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Monomial other = (Monomial) obj;
    return Objects.equals(name, other.name) && rank == other.rank;
  }

}
