package org.col.es.dsl;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@SuppressWarnings("unused")
public class TermsQuery implements Query {

  private final Map<String, Collection<?>> terms;

  public TermsQuery(String field, Collection<?> values) {
    terms = Collections.singletonMap(field, values);
  }

  public TermsQuery(String field, Object... values) {
    this(field, Arrays.asList(values));
  }

}
