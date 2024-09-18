package life.catalogue.api.model;

import life.catalogue.api.vocab.Issue;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.text.StringUtils;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.util.UnicodeUtils;

import java.io.Serializable;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;

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
 * 3) Unicode char escaping using brackets: <U+00A0>
 */
public class VerbatimRecord implements DSID<Integer>, IssueContainer, Serializable {
  private static final Logger LOG = LoggerFactory.getLogger(VerbatimRecord.class);
  private static final Pattern HEX_UNICODE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
  private static final Pattern ECMA_UNICODE = Pattern.compile("\\\\u\\{([0-9a-fA-F]{4})}");
  private static final Pattern ANGLE_UNICODE = Pattern.compile("<U\\+?([0-9a-f]{4})>", Pattern.CASE_INSENSITIVE);
  private static final Pattern REMOVE_TAGS = Pattern.compile("</? *[a-z][a-z1-5]{0,5} *>", Pattern.CASE_INSENSITIVE);

  private Integer id;
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

  public VerbatimRecord(VerbatimRecord v) {
    this(v.datasetKey, v.line, v.file, v.type);
  }

  public VerbatimRecord(long line, String file, Term type) {
    this.line = line;
    this.file = file;
    this.type = type;
  }

  public VerbatimRecord(Integer datasetKey, long line, String file, Term type) {
    this(line, file, type);
    this.datasetKey = datasetKey;
  }

  public Integer getId() {
    return id;
  }
  
  public void setId(Integer id) {
    this.id = id;
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

  /**
   * @return true if at least one term in the DwC namespace exists
   */
  @JsonIgnore
  public boolean hasDwcTerms() {
    for (Term t : terms.keySet()) {
      if (t instanceof DwcTerm) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return true if at least one term in the ColDP namespace exists
   */
  @JsonIgnore
  public boolean hasColdpTerms() {
    for (Term t : terms.keySet()) {
      if (t instanceof ColdpTerm) {
        return true;
      }
    }
    return false;
  }

  private String replUnicode(MatchResult m) {
    // create an int primitive cp and assign value
    int cp = Integer.decode("0x"+m.group(1));
    // assign result of toChars on cp to ch
    return String.valueOf(Character.toChars(cp));
  }

  private String unescape(String x) {
    if (Strings.isNullOrEmpty(x)) {
      return null;
    }
    try {
      String unescaped = HEX_UNICODE.matcher(x).replaceAll(this::replUnicode);
      unescaped = ECMA_UNICODE.matcher(unescaped).replaceAll(this::replUnicode);
      unescaped = ANGLE_UNICODE.matcher(unescaped).replaceAll(this::replUnicode);
      unescaped = StringEscapeUtils.unescapeHtml4(unescaped);
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
   * This cleans the string from invisible characters and homoglyphs:
   * - removes invisible control characters
   * - normalises space characters
   * - replaces various homoglyphs with their standard form
   */
  private String cleanInvisible(String x) {
    if (Strings.isNullOrEmpty(x)) {
      return null;
    }
    String cleaned = StringUtils.cleanInvisible(x);
    if (!x.equals(cleaned)) {
      issues.add(Issue.INVISIBLE_CHARACTERS);
    }
    return cleaned;
  }

  private String flagIssues(String x) {
    if (UnicodeUtils.containsHomoglyphs(x)) {
      issues.add(Issue.HOMOGLYPH_CHARACTERS);
    }
    if (UnicodeUtils.containsHomoglyphs(x)) {
      issues.add(Issue.DIACRITIC_CHARACTERS);
    }
    return x;
  }

  /**
   * @return true if a term exists and is not null or an empty string
   */
  public boolean hasTerm(Term term) {
    checkNotNull(term, "term can't be null");
    return terms.get(term) != null;
  }

  /**
   * Asserts that all terms are existing
   */
  public boolean hasTerms(Term... term) {
    checkNotNull(term, "term can't be null");
    for (var t : term) {
      if (!hasTerm(t)) {
        return false;
      }
    }
    return true;
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
    if (term != null) {
      return terms.get(term);
    }
    return null;
  }
  
  /**
   * @return the potentially unescaped value, replacing empty strings with true nulls
   */
  public String get(Term term) {
    if (term != null) {
      String val = terms.get(term);
      if (val != null) {
        return flagIssues(cleanInvisible(unescape(val)));
      }
    }
    return null;
  }
  
  public String getOrDefault(Term term, String defaultValue) {
    String val = get(term);
    return val == null ? defaultValue : val;
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
  @JsonIgnore
  public boolean hasChanged() {
    return _hashKeyOnLoad == -1 || _hashKeyOnLoad != hashCode();
  }
  
  @Override
  public String toString() {
    return "v" + id + "{" + file + "#" + line + ", " + terms.size() + " terms";
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
        Objects.equals(id, that.id) &&
        Objects.equals(datasetKey, that.datasetKey) &&
        Objects.equals(file, that.file) &&
        Objects.equals(type, that.type) &&
        Objects.equals(terms, that.terms) &&
        Objects.equals(issues, that.issues);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(id, datasetKey, line, file, type, terms, issues);
  }
  
  /**
   * @return all terms and values as a string
   */
  public String toStringComplete() {
    StringBuilder sb = new StringBuilder();
    sb.append(id).append(" ")
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
