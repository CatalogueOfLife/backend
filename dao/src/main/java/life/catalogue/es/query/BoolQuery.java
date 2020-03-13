package life.catalogue.es.query;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BoolQuery extends ConstraintQuery<BoolConstraint> {

  public static BoolQuery withFilters(Query... filters) {
    BoolQuery bq = new BoolQuery();
    for (Query filter : filters) {
      bq.filter(filter);
    }
    return bq;
  }

  @JsonProperty("bool")
  private final BoolConstraint constraint;

  public BoolQuery() {
    this.constraint = new BoolConstraint();
  }

  public BoolQuery must(Query query) {
    constraint.must(query);
    return this;
  }

  public BoolQuery filter(Query query) {
    constraint.filter(query);
    return this;
  }

  public BoolQuery mustNot(Query query) {
    constraint.mustNot(query);
    return this;
  }

  public BoolQuery should(Query query) {
    constraint.should(query);
    return this;
  }

  public BoolQuery minimumShouldMatch(Integer i) {
    constraint.minimumShouldMatch(i);
    return this;
  }

  @Override
  BoolConstraint getConstraint() {
    return constraint;
  }

}
