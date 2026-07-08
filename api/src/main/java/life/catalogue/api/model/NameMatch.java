package life.catalogue.api.model;

import life.catalogue.api.vocab.MatchType;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;


public class NameMatch {
  private IndexName name;
  private MatchType type;
  private List<IndexName> alternatives;
  
  public static NameMatch noMatch() {
    NameMatch m = new NameMatch();
    m.setType(MatchType.NONE);
    return m;
  }

  public static NameMatch unsupported() {
    NameMatch m = new NameMatch();
    m.setType(MatchType.UNSUPPORTED);
    return m;
  }

  public IndexName getName() {
    return name;
  }

  /**
   * @return the matched names key or null if no match exists
   */
  public Integer getNameKey() {
    return name == null ? null : name.getKey();
  }

  /**
   * The names index is single-tier & canonical-only: every entry is its own canonical, so the
   * canonical key is simply the matched entry's own key.
   * @return the matched names canonical key or null if no match exists
   */
  public Integer getCanonicalNameKey() {
    return name == null ? null : name.getKey();
  }

  public void setName(IndexName n) {
    Preconditions.checkNotNull(n.getKey());
    name=n;
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
    return Objects.equals(name, nameMatch.name) &&
        type == nameMatch.type &&
        Objects.equals(alternatives, nameMatch.alternatives);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(name, type, alternatives);
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(type.name());
    if (type != MatchType.NONE) {
      sb.append(": ");
      sb.append(name.getLabelWithRank());
    }
    return sb.toString();
  }
}
