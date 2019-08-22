package org.col.api.search;

import org.col.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

/**
 * Represents a single suggestion comming back from the NameSuggestionService.
 */
public class NameSuggestion {

  private String name;
  private Rank rank;
  private TaxonomicStatus status;
  private NomCode code;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Rank getRank() {
    return rank;
  }

  public void setRank(Rank rank) {
    this.rank = rank;
  }

  public TaxonomicStatus getStatus() {
    return status;
  }

  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }

  public NomCode getCode() {
    return code;
  }

  public void setCode(NomCode code) {
    this.code = code;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((code == null) ? 0 : code.hashCode());
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result + ((rank == null) ? 0 : rank.hashCode());
    result = prime * result + ((status == null) ? 0 : status.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    NameSuggestion other = (NameSuggestion) obj;
    if (code != other.code)
      return false;
    if (name == null) {
      if (other.name != null)
        return false;
    } else if (!name.equals(other.name))
      return false;
    if (rank != other.rank)
      return false;
    if (status != other.status)
      return false;
    return true;
  }

}
