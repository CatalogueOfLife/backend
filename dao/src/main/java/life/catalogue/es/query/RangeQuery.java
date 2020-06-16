package life.catalogue.es.query;

import java.util.Map;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.es.nu.NameUsageFieldLookup;
import static java.util.Collections.singletonMap;

public class RangeQuery<T> extends ConstraintQuery<RangeConstraint<T>> {

  public static <U> RangeQuery<U> on(NameUsageSearchParameter p) {
    return new RangeQuery<>(NameUsageFieldLookup.INSTANCE.lookup(p));
  }

  private final Map<String, RangeConstraint<T>> range;

  public RangeQuery(String field) {
    range = singletonMap(field, new RangeConstraint<>());
  }

  public RangeQuery<T> lessThan(T value) {
    getConstraint().lt(value);
    return this;
  }

  public RangeQuery<T> greaterThan(T value) {
    getConstraint().gt(value);
    return this;
  }

  public RangeQuery<T> lessOrEqual(T value) {
    getConstraint().lte(value);
    return this;
  }

  public RangeQuery<T> greaterOrEqual(T value) {
    getConstraint().gte(value);
    return this;
  }

  @Override
  RangeConstraint<T> getConstraint() {
    return range.values().iterator().next();
  }

}
