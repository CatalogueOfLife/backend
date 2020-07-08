package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import life.catalogue.api.vocab.MatchType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class NameMatch {
  private final List<IndexName> names = new ArrayList<>();
  private MatchType type;
  private List<IndexName> alternatives;
  
  public static NameMatch noMatch() {
    NameMatch m = new NameMatch();
    m.setType(MatchType.NONE);
    return m;
  }

  public List<IndexName> getNames() {
    return names;
  }

  public IntSet getNameIds() {
    IntSet is = new IntOpenHashSet();
    names.forEach(n -> {
      is.add(n.getKey());
    });
    return is;
  }

  public void addName(IndexName n) {
    names.add(n);
  }

  @JsonIgnore
  public boolean hasMatch() {
    return !names.isEmpty();
  }

  @JsonIgnore
  public boolean hasSingleMatch() {
    return names.size() == 1;
  }

  public MatchType getType() {
    return type;
  }
  
  public void setType(MatchType type) {
    this.type = type;
  }
  
  public List<IndexName> getAlternatives() {
    return alternatives;
  }
  
  public void setAlternatives(List<IndexName> alternatives) {
    this.alternatives = alternatives;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NameMatch nameMatch = (NameMatch) o;
    return Objects.equals(names, nameMatch.names) &&
        type == nameMatch.type &&
        Objects.equals(alternatives, nameMatch.alternatives);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(names, type, alternatives);
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(type.name())
        .append(" match");
    if (type != MatchType.NONE) {
      sb.append(": [");
      boolean first = true;
      for (IndexName a : names) {
        sb.append(a.getLabel(false));
        if (first) {
          first = false;
        } else {
          sb.append("; ");
        }
      }
      sb.append("]");
    }
    return sb.toString();
  }
}
