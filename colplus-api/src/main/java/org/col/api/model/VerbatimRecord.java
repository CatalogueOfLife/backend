package org.col.api.model;

import java.io.Serializable;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.apache.commons.text.StringEscapeUtils;
import org.col.api.vocab.Issue;
import org.gbif.dwc.terms.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A verbatim record that offers basic value cleaning and unescaping of common character entities
 * on read methods without altering the underlying original verbatim data.
 * <p>
 * It also strips simple xml/html tags without attributes such as <i> or </b>
 * <p>
 * When a value is altered during unescaping the record keeps track that it had been unescaped.
 * <p>
 * The following escaped character formats are recognized:
 * 1) html entities
 * named &amp;
 * hex &#x0026;
 * decimal &#38;
 * 2) Hexadecimal and octal escapes as used in Java, CSS & ECMA Javascript:
 * Hexadecimal unicode escapes started by  "\\u": \\u00A9
 * Unicode code point escapes indicated by "\\u{}": \\u{2F80}
 */
public class VerbatimRecord implements IssueContainer, GlobalEntity, Serializable {
  private static final Logger LOG = LoggerFactory.getLogger(VerbatimRecord.class);
  private static final Pattern REMOVE_TAGS = Pattern.compile("</? *[a-z][a-z1-5]{0,5} *>", Pattern.CASE_INSENSITIVE);
  private static final Pattern ECMA_UNICODE = Pattern.compile("\\\\u\\{([0-9a-f]{4})}", Pattern.CASE_INSENSITIVE);
  
  private Integer key;
  private Integer datasetKey;
  // instance hash created on load to see if the instance has been changed
  private int _hashKeyOnLoad = -1;
  
  private long line;
  private String file;
  private Term type;
  private Map<Term, String> terms = new HashMap<>();
  private Set<Issue> issues = EnumSet.noneOf(Issue.class);
  
  public VerbatimRecord() {
  }
  
  public VerbatimRecord(long line, String file, Term type) {
    this.line = line;
    this.file = file;
    this.type = type;
  }
  
  public Integer getKey() {
    return key;
  }
  
  public void setKey(Integer key) {
    this.key = key;
  }
  
  public Integer getDatasetKey() {
    return datasetKey;
  }
  
  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
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
  
  public Map<Term, String> getTerms() {
    return terms;
  }
  
  public void setTerms(Map<Term, String> terms) {
    this.terms = terms;
  }
  
  @Override
  public Set<Issue> getIssues() {
    return issues;
  }
  
  @Override
  public void setIssues(Set<Issue> issues) {
    this.issues = issues;
  }
  
  @Override
  public void addIssue(Issue issue) {
    issues.add(issue);
  }
  
  public void addIssues(Issue... issues) {
    addIssues(Arrays.asList(issues));
  }

  public void addIssues(Collection<Issue> issues) {
    this.issues.addAll(issues);
  }
  
  @Override
  public boolean removeIssue(Issue issue) {
    return issues.remove(issue);
  }

  @Override
  public boolean hasIssue(Issue issue) {
    return issues.contains(issue);
  }
  
  private String unescape(String x) {
    if (Strings.isNullOrEmpty(x)) {
      return null;
    }
    try {
      String unescaped = ECMA_UNICODE.matcher(x).replaceAll("\\\\u$1");
      unescaped = StringEscapeUtils.unescapeEcmaScript(StringEscapeUtils.unescapeHtml4(unescaped));
      unescaped = REMOVE_TAGS.matcher(unescaped).replaceAll("");
      if (!x.equals(unescaped)) {
        issues.add(Issue.ESCAPED_CHARACTERS);
      }
      return unescaped;
    } catch (RuntimeException e) {
      LOG.debug("Failed to unescape: {}", x, e);
      return x;
    }
  }
  
  /**
   * @return true if a term exists and is not null or an empty string
   */
  public boolean hasTerm(Term term) {
    checkNotNull(term, "term can't be null");
    return terms.get(term) != null;
  }
  
  /**
   * @return true if at least one term exists and is not null or an empty string
   */
  public boolean hasAny(Term... terms) {
    for (Term t : terms) {
      if (hasTerm(t)) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return the raw value without any unescaping
   */
  public String getRaw(Term term) {
    checkNotNull(term, "term can't be null");
    return terms.get(term);
  }
  
  /**
   * @return the potentially unescaped value
   */
  public String get(Term term) {
    checkNotNull(term, "term can't be null");
    String val = terms.get(term);
    if (val != null) {
      return unescape(val);
    }
    return null;
  }
  
  public int size() {
    return terms.size();
  }
  
  @JsonIgnore
  public boolean isEmpty() {
    return terms.isEmpty();
  }
  
  public Set<Term> terms() {
    return terms.keySet();
  }
  
  public void put(Term term, String value) {
    checkNotNull(term, "term can't be null");
    if (value == null) {
      terms.remove(term);
    } else {
      terms.put(term, value);
    }
  }
  
  /**
   * @return a URI based on the raw value without applying any unescaping or null if an invalid URI
   */
  public URI getURI(Term term) {
    checkNotNull(term, "term can't be null");
    String val = terms.get(term);
    if (val != null) {
      try {
        return URI.create(val);
      } catch (IllegalArgumentException e) {
        LOG.debug("Invalid URI: {}", val);
      }
    }
    return null;
  }
  
  /**
   * Returns a parsed integer for a term value, adding the invalidIssue to the verbatim record issues
   * if the value cannot be parsed, returning null in that case.
   */
  public Integer getInt(Term term, Issue invalidIssue) {
    try {
      return getInt(term);
    } catch (NumberFormatException e) {
      issues.add(invalidIssue);
      return null;
    }
  }
  
  /**
   * Returns a parsed integer for a term value, throwing NumberFormatException if the value cannot be parsed.
   */
  public Integer getInt(Term term) throws NumberFormatException {
    checkNotNull(term, "term can't be null");
    String val = get(term);
    if (val != null) {
      return Integer.valueOf(val);
    }
    return null;
  }
  
  /**
   * Returns a parsed date for a term value, throwing DateTimeParseException if the value cannot be parsed.
   */
  public LocalDate getDate(Term term) throws DateTimeParseException {
    checkNotNull(term, "term can't be null");
    String val = get(term);
    if (val != null) {
      return LocalDate.parse(val);
    }
    return null;
  }
  
  /**
   * Get the first non blank term for a list of terms.
   *
   * @param terms list to try
   */
  @Nullable
  public String getFirst(Term... terms) {
    for (Term t : terms) {
      String val = get(t);
      if (val != null) {
        return val;
      }
    }
    return null;
  }
  
  /**
   * Get the first non blank term for a list of terms.
   *
   * @param terms list to try
   */
  @Nullable
  public String getFirstRaw(Term... terms) {
    for (Term t : terms) {
      String val = getRaw(t);
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
    checkNotNull(term, "term can't be null");
    String val = get(term);
    if (val != null) {
      return splitter.splitToList(val);
    }
    return null;
  }
  
  
  /**
   * Stores the current state of the instance for subsequent hasChanged() tests.
   */
  public void setHashCode() {
    _hashKeyOnLoad = hashCode();
  }
  
  /**
   * @return true if the instance has been modified since the last time setHashCode was executed.
   */
  public boolean hasChanged() {
    return _hashKeyOnLoad == -1 || _hashKeyOnLoad != hashCode();
  }
  
  @Override
  public String toString() {
    return "v" + key + "{" + file + "#" + line + ", " + terms.size() + " terms";
  }
  
  public String fileLine() {
    return file + "#" + line;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VerbatimRecord that = (VerbatimRecord) o;
    return line == that.line &&
        Objects.equals(key, that.key) &&
        Objects.equals(datasetKey, that.datasetKey) &&
        Objects.equals(file, that.file) &&
        Objects.equals(type, that.type) &&
        Objects.equals(terms, that.terms) &&
        Objects.equals(issues, that.issues);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(key, datasetKey, line, file, type, terms, issues);
  }
  
  /**
   * @return all terms and values as a string
   */
  public String toStringComplete() {
    StringBuilder sb = new StringBuilder();
    sb.append(key).append(" ")
        .append(file).append("#").append(line)
        .append(", " + type)
        .append(": ");
    boolean first = true;
    for (Map.Entry<Term, String> te : terms.entrySet()) {
      if (first) {
        first = false;
      } else {
        sb.append(", ");
      }
      sb.append(te.getKey().prefixedName())
          .append("=")
          .append(te.getValue());
    }
    return sb.toString();
  }
}
