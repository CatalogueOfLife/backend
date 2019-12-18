package life.catalogue.es.ddl;

import life.catalogue.es.query.Query;

/**
 * An object fine-tuning the behaviour of index aliases. We currently don't actually do any fine-tuning.
 *
 */
public class AliasDefinition {

  private Query filter;

  public Query getFilter() {
    return filter;
  }

  public void setFilter(Query filter) {
    this.filter = filter;
  }

}
