package org.col.api.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.apache.commons.lang3.StringUtils;
import org.col.api.jackson.RecTermsSerde;
import org.col.api.jackson.TermSerde;
import org.gbif.dwc.terms.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.HashMap;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 *
 */
@JsonSerialize(using = RecTermsSerde.Serializer.class)
@JsonDeserialize(keyUsing = TermSerde.TermKeyDeserializer.class, using = RecTermsSerde.Deserializer.class)
public class TermRecord extends HashMap<Term, String> {
  private static final Logger LOG = LoggerFactory.getLogger(TermRecord.class);

  /**
   * @return true if a term exists and is not null or an empty string
   */
  public boolean hasTerm(Term term) {
    checkNotNull(term, "term can't be null");
    return !Strings.isNullOrEmpty(get(term));
  }

  public String get(Term key) {
    String val = super.get(key);
    if (!StringUtils.isBlank(val)) {
      return val;
    }
    return null;
  }

  public URI getURI(Term key) {
    String val = super.get(key);
    if (!StringUtils.isBlank(val)) {
      return URI.create(val);
    }
    return null;
  }

  /**
   * Returns a parsed integer for a term value, throwing NumberFormatException if the value cannot be parsed.
   */
  public Integer getInt(Term key) throws NumberFormatException {
    String val = super.get(key);
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
  public Integer getIntDefault(Term key, Integer defaultValue) {
    Integer i = null;
    try {
      i = getInt(key);
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
}
