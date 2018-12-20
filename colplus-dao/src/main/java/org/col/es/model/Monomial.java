package org.col.es.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;

import org.col.es.annotations.MapToType;
import org.col.es.mapping.ESDataType;
import org.gbif.nameparser.api.Rank;

public class Monomial {

  private Rank rank;
  private String name;

  @JsonCreator
  public Monomial(Rank rank, String name) {
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
