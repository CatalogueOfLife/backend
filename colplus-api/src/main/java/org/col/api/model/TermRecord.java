package org.col.api.model;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.apache.commons.lang3.StringUtils;
import org.gbif.dwc.terms.Term;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 *
 */
public class TermRecord {
  private long line;
  private String file;
  private Term type;
  private Map<Term, String> terms = new HashMap<>();

  public TermRecord() {
  }

  public TermRecord(long line, String file, Term type) {
    this.line = line;
    this.file = file;
    this.type = type;
  }

  /**
   * @return line number this record represents in the underlying source file
   */
  public long getLine() {
    return line;
  }

  public void setLine(long line) {
    this.line = line;
  }

  public void setFile(String file) {
    this.file = file;
  }

  public String getFile() {
    return file;
  }

  public Term getType() {
    return type;
  }

  public void setType(Term type) {
    this.type = type;
  }

  /**
   * @return true if a term exists and is not null or an empty string
   */
  public boolean hasTerm(Term term) {
    checkNotNull(term, "term can't be null");
    return !Strings.isNullOrEmpty(get(term));
  }

  public String get(Term term) {
    checkNotNull(term, "term can't be null");
    String val = terms.get(term);
    if (!StringUtils.isBlank(val)) {
      return val;
    }
    return null;
  }

  public URI getURI(Term term) {
    String val = terms.get(term);
    if (!StringUtils.isBlank(val)) {
      return URI.create(val);
    }
    return null;
  }

  public int size() {
    return terms.size();
  }

  public boolean isEmpty() {
    return terms.isEmpty();
  }

  public String remove(Term term) {
    return terms.remove(term);
  }

  public void clear() {
    terms.clear();
  }

  public Set<Term> terms() {
    return terms.keySet();
  }

  public Set<Map.Entry<Term, String>> termValues() {
    return terms.entrySet();
  }

  public String getOrDefault(Term term, String defaultValue) {
    return terms.getOrDefault(term, defaultValue);
  }

  public void forEach(BiConsumer<? super Term, ? super String> action) {
    terms.forEach(action);
  }

  public void put(Term term, String value) {
    checkNotNull(term, "term can't be null");
    terms.put(term, value);
  }

  public void putAll(Map<? extends Term, ? extends String> m) {
    terms.putAll(m);
  }

  public String putIfAbsent(Term term, String value) {
    return terms.putIfAbsent(term, value);
  }

  public String merge(Term term, String value, BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
    return terms.merge(term, value, remappingFunction);
  }

  /**
   * Returns a parsed integer for a term value, throwing NumberFormatException if the value cannot be parsed.
   */
  public Integer getInt(Term term) throws NumberFormatException {
    String val = terms.get(term);
    if (!StringUtils.isBlank(val)) {
      return Integer.valueOf(val);
    }
    return null;
  }

  /**
   * Returns a parsed integer for a term value.
   * If no value is found it the value cannot be parsed the default value is returned
   * and no exception is thrown.
   */
  public Integer getIntDefault(Term term, Integer defaultValue) {
    Integer i = null;
    try {
      i = getInt(term);
    } catch (NumberFormatException e) {
      // swallow
    }
    return i == null ? defaultValue : i;
  }

  /**
   * Get the first non blank term for a list of terms.
   * @param terms list to try
   */
  @Nullable
  public String getFirst(Term ... terms) {
    for (Term t : terms) {
      String val = get(t);
      if (val != null) {
        return val;
      }
    }
    return null;
  }

  /**
   * Get the term value split by a delimiter
   */
  @Nullable
  public List<String> get(Term term, Splitter splitter) {
    String val = get(term);
    if (val != null) {
      return splitter.splitToList(val);
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TermRecord that = (TermRecord) o;
    return line == that.line &&
        Objects.equals(file, that.file) &&
        Objects.equals(type, that.type) &&
        Objects.equals(terms, that.terms);
  }

  @Override
  public int hashCode() {
    return Objects.hash(line, file, type, terms);
  }

  @Override
  public String toString() {
    return "TermRecord{" + file + "#" + line +", "+ terms.size() + " terms";
  }
}
