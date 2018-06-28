package org.col.matching;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.col.api.model.Name;


public class NameMatch {
  private Name name;
  private MatchType type;
  private List<Name> alternatives;

  public static NameMatch noMatch() {
    NameMatch m = new NameMatch();
    m.setType(MatchType.NONE);
    return m;
  }

  public Name getName() {
    return name;
  }

  public void setName(Name name) {
    this.name = name;
  }

  @JsonIgnore
  public boolean hasMatch() {
    return name != null;
  }

  public MatchType getType() {
    return type;
  }

  public void setType(MatchType type) {
    this.type = type;
  }

  public List<Name> getAlternatives() {
    return alternatives;
  }

  public void setAlternatives(List<Name> alternatives) {
    this.alternatives = alternatives;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NameMatch nameMatch = (NameMatch) o;
    return Objects.equals(name, nameMatch.name) &&
        type == nameMatch.type &&
        Objects.equals(alternatives, nameMatch.alternatives);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type, alternatives);
  }
}
