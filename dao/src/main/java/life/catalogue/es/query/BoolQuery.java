package life.catalogue.es.query;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BoolQuery extends ConstraintQuery<BoolConstraint> {

  public static BoolQuery withFilters(Query... filters) {
    return withFilters(Arrays.stream(filters));
  }
  public static BoolQuery withFilters(List<Query> filters) {
    return withFilters(filters.stream());
  }
  private static BoolQuery withFilters(Stream<Query> filters) {
    return (BoolQuery) filters.reduce(new BoolQuery(), (q1, q2) -> ((BoolQuery) q1).filter(q2));
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
