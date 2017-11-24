package org.col.api;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Objects;

/**
 * TODO: make new class grouping basionym & comb Authorship and add authors, ex-authors & sanction authors part of Authorship!!!
 * TODO: deal with ex-authors https://github.com/Sp2000/colplus-backend/issues/10
 * TODO: add sanctioning author (Fr. or Pers.) to both basionym & recomb
 */
public class Authorship {
  private static final Joiner AUTHORSHIP_JOINER = Joiner.on(", ").skipNulls();

  /**
   * list of authors.
   */
  private List<String> authors = Lists.newArrayList();

  /**
   * list of authors excluding ex- authors
   */
  private List<String> exAuthors = Lists.newArrayList();

  /**
   * The year the combination or basionym was first published, usually the same as the publishedIn reference.
   * It is used for sorting names and ought to be populated even for botanical names which do not use it in the complete authorship string.
   */
  private String year;

  public List<String> getAuthors() {
    return authors;
  }

  public void setAuthors(List<String> authors) {
    this.authors = authors;
  }

  public List<String> getExAuthors() {
    return exAuthors;
  }

  public void setExAuthors(List<String> exAuthors) {
    this.exAuthors = exAuthors;
  }

  public String getYear() {
    return year;
  }

  public void setYear(String year) {
    this.year = year;
  }

  public boolean isEmpty() {
    return authors.isEmpty() && year == null;
  }

  public boolean exists() {
    return !authors.isEmpty() || year != null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Authorship that = (Authorship) o;
    return Objects.equals(authors, that.authors) &&
        Objects.equals(exAuthors, that.exAuthors) &&
        Objects.equals(year, that.year);
  }

  @Override
  public int hashCode() {
    return Objects.hash(authors, exAuthors, year);
  }

  private static String joinAuthors(List<String> authors, boolean useEtAl) {
    if (useEtAl && authors.size() > 2) {
      return AUTHORSHIP_JOINER.join(authors.subList(0, 1)) + " et al.";

    } else if (authors.size() > 1) {
      return AUTHORSHIP_JOINER.join(authors.subList(0, authors.size()-1)) + " & " + authors.get(authors.size() - 1);

    } else {
      return AUTHORSHIP_JOINER.join(authors);
    }
  }

  /**
   * @return the full authorship string as used in scientific names
   */
  @Override
  public String toString() {
    if (exists()) {
      StringBuilder sb = new StringBuilder();
      if (!exAuthors.isEmpty()) {
        sb.append(joinAuthors(exAuthors, false));
        sb.append(" ex ");
      }
      if (!authors.isEmpty()) {
        sb.append(joinAuthors(authors, false));
      }
      if (year != null) {
        if (sb.length() > 0) {
          sb.append(", ");
        }
        sb.append(year);
      }
      return sb.toString();
    }
    return null;
  }
}
