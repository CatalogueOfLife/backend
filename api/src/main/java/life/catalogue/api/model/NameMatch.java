package life.catalogue.api.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * The result of matching a name against the single-tier, canonical-only names index.
 * Collapsed to a bare names-index id (nidx) plus a matched flag: the index no longer distinguishes
 * EXACT/VARIANT match types (that classification now lives in the usage-match layer) and never
 * carries alternative candidates.
 */
public class NameMatch {
  private Integer nidx;   // the matched names-index id, null if no match
  private boolean matched;

  public static NameMatch noMatch() {
    return new NameMatch();
  }

  public static NameMatch unsupported() {
    return new NameMatch();
  }

  public static NameMatch match(int nidx) {
    NameMatch m = new NameMatch();
    m.nidx = nidx;
    m.matched = true;
    return m;
  }

  /**
   * @return the matched names index id or null if no match exists
   */
  public Integer getNidx() {
    return nidx;
  }

  public void setNidx(Integer nidx) {
    this.nidx = nidx;
  }

  public boolean isMatched() {
    return matched;
  }

  public void setMatched(boolean matched) {
    this.matched = matched;
  }

  /**
   * Alias for {@link #isMatched()} kept for existing consumers.
   */
  @JsonIgnore
  public boolean hasMatch() {
    return matched;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NameMatch nameMatch = (NameMatch) o;
    return matched == nameMatch.matched && Objects.equals(nidx, nameMatch.nidx);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nidx, matched);
  }

  @Override
  public String toString() {
    return matched ? "match " + nidx : "no match";
  }
}
