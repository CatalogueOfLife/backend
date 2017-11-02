package org.col.api;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * TODO: deal with ex-authors https://github.com/Sp2000/colplus-backend/issues/10
 */
public class Authorship {
  private static final Joiner AUTHORSHIP_JOINER = Joiner.on(", ").skipNulls();

  /**
   * list of basionym authors.
   */
  private LinkedList<String> basionymAuthors = Lists.newLinkedList();

  /**
   * Year of original name publication
   */
  private String basionymYear;

  /**
   * list of authors excluding ex- authors
   */
  private LinkedList<String> combinationAuthors = Lists.newLinkedList();

  /**
   * The year this combination was first published, usually the same as the publishedIn reference.
   * It is used for sorting names and ought to be populated even for botanical names which do not use it in the complete authorship string.
   */
  private String combinationYear;

  public List<String> getBasionymAuthors() {
    return basionymAuthors;
  }

  public void setBasionymAuthors(List<String> basionymAuthors) {
    this.basionymAuthors = Lists.newLinkedList(basionymAuthors);
  }

  public String getBasionymYear() {
    return basionymYear;
  }

  public void setBasionymYear(String basionymYear) {
    this.basionymYear = basionymYear;
  }

  public List<String> getCombinationAuthors() {
    return combinationAuthors;
  }

  public void setCombinationAuthors(List<String> combinationAuthors) {
    this.combinationAuthors = Lists.newLinkedList(combinationAuthors);
  }

  public String getCombinationYear() {
    return combinationYear;
  }

  public void setCombinationYear(String combinationYear) {
    this.combinationYear = combinationYear;
  }

  public boolean isEmpty() {
    return combinationAuthors.isEmpty() && combinationYear == null && basionymAuthors.isEmpty() && basionymYear == null;
  }

  /**
   * @return true if original year or authors exist
   */
  public boolean hasOriginal() {
    return !basionymAuthors.isEmpty() || basionymYear != null;
  }

  /**
   * @return true if original year or authors exist
   */
  public boolean hasCombination() {
    return !combinationAuthors.isEmpty() || combinationYear != null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Authorship that = (Authorship) o;
    return Objects.equals(basionymAuthors, that.basionymAuthors) &&
        Objects.equals(basionymYear, that.basionymYear) &&
        Objects.equals(combinationAuthors, that.combinationAuthors) &&
        Objects.equals(combinationYear, that.combinationYear);
  }

  @Override
  public int hashCode() {
    return Objects.hash(basionymAuthors, basionymYear, combinationAuthors, combinationYear);
  }

  private String joinAuthors(LinkedList<String> authors) {
    // TODO: offer option to abbreviate with et al.
    if (authors.size() > 1) {
      return AUTHORSHIP_JOINER.join(authors.subList(0, authors.size()-1)) + " & " + authors.getLast();
    } else {
      return AUTHORSHIP_JOINER.join(authors);
    }
  }

  /**
   * @return the full authorship string as used in scientific names
   */
  @Override
  public String toString() {
    if (hasCombination() || hasOriginal()) {
      StringBuilder sb = new StringBuilder();
      if (hasOriginal()) {
        sb.append("(");
        sb.append(joinAuthors(basionymAuthors));
        if (basionymYear != null && !basionymAuthors.isEmpty()) {
          sb.append(", ");
          sb.append(basionymYear);
        }
        sb.append(") ");
      }
      if (hasCombination()) {
        sb.append(joinAuthors(combinationAuthors));
        if (combinationYear != null && !combinationAuthors.isEmpty()) {
          sb.append(", ");
          sb.append(combinationYear);
        }
      }
      return sb.toString();
    }
    return null;
  }
}
