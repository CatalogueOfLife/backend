package org.col.es.query;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MatchAllQuery extends AbstractQuery {

  public static final MatchAllQuery INSTANCE = new MatchAllQuery();

  @JsonInclude(JsonInclude.Include.ALWAYS)
  @JsonProperty("match_all")
  final Map<?, ?> matchAll = Collections.EMPTY_MAP;

}
