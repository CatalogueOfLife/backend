package org.col.dw.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Strings;
import org.apache.commons.lang3.StringUtils;
import org.col.dw.api.jackson.RecTermsSerde;
import org.col.dw.api.jackson.TermSerde;
import org.gbif.dwc.terms.Term;

import javax.annotation.Nullable;
import java.util.HashMap;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 *
 */
@JsonSerialize(using = RecTermsSerde.Serializer.class)
@JsonDeserialize(keyUsing = TermSerde.TermKeyDeserializer.class, using = RecTermsSerde.Deserializer.class)
public class TermRecord extends HashMap<Term, String> {

  /**
   * @return true if a term exists and is not null or an empty string
   */
  public boolean hasTerm(Term term) {
    checkNotNull(term, "term can't be null");
    return !Strings.isNullOrEmpty(get(term));
  }

  /**
   * Get the first non blank term for a list of terms.
   * @param terms list to try
   */
  @Nullable
  public String getFirst(Term ... terms) {
    for (Term t : terms) {
      String val = this.get(t);
      if (!StringUtils.isBlank(val)) {
        return val;
      }
    }
    return null;
  }
}
