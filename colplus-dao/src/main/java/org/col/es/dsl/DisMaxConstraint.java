package org.col.es.dsl;

import java.util.List;

class DisMaxConstraint extends Constraint {

  private final List<Query> queries = new CollapsibleList<>(5);

  void subquery(Query query) {
    queries.add(query);
  }
}
