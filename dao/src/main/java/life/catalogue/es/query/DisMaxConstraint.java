package life.catalogue.es.query;

import java.util.List;

import life.catalogue.es.query.CollapsibleList;
import life.catalogue.es.query.Query;

class DisMaxConstraint extends Constraint {

  private final List<Query> queries = new CollapsibleList<>(5);

  void subquery(Query query) {
    queries.add(query);
  }
}
