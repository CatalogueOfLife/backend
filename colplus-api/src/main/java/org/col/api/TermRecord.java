package org.col.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Strings;
import org.col.api.jackson.RecTermsSerde;
import org.col.api.jackson.TermSerde;
import org.gbif.dwc.terms.Term;

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

}
