package org.col.es.dsl;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A general representation of the thing that you require documents to have, for example a literal value in the case of
 * a {@link TermQuery}. It is at <i>this</i> level that you can boost the query, or turn it into a named query, rather
 * than at the level of the query itself - which is pretty odd. Unfortunately, Elasticsearch's object model is not very
 * consistent, and the {@link TermsQuery} (not to be confused with the {@link TermQuery}) and the
 * {@link ConstantScoreQuery} are exceptions where the name and boost value <i>are indeed</i> set in the query object
 * itself.
 *
 */
@SuppressWarnings("unused")
class Constraint {

  @JsonProperty("_name")
  private String name;
  private Double boost;

  void name(String name) {
    this.name = name;
  }

  void boost(Double boost) {
    this.boost = boost;
  }

}
