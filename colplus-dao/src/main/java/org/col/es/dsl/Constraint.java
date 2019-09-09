package org.col.es.dsl;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A general representation of the thing that you require documents to have, for example a literal value in the case of
 * a {@link TermQuery}. It is at <i>this</i> level that you can boost the query, or turn it into a named query, rather
 * than at the level of the query itself, which is pretty odd, especially with regard to the name of the query.
 * Unfortunately, Elasticsearch's object model is not very rigorously designed, and the {@link TermsQuery} (not to be
 * confused with the {@link TermQuery}) and the {@link ConstantScoreQuery} are exceptions where the name and boost value
 * <i>are indeed</i> set at the level of the query itself. We can't, however, make them extend {@code Constraint},
 * because they already extend {@link AbstractQuery} (which is the more vital extension). So we don't have something like
 * a {@code TermsConstraint} accompanying the {@code TermsQuery}. In stead, {@code TermsQuery} only implements the empty
 * {@link Query} interface, and the name and boost value are defined directly in the {@code TermsQuery} itself.
 *
 */
@SuppressWarnings("unused")
class Constraint {

  @JsonProperty("_name")
  private String name;
  private Float boost;

  void name(String name) {
    this.name = name;
  }

  void boost(Float boost) {
    this.boost = boost;
  }

}
