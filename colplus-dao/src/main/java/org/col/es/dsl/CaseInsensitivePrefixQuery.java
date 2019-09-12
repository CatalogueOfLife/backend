package org.col.es.dsl;

import org.col.es.mapping.MultiField;

/**
 * Case-insentive variant of the {@code PrefixQuery}. Note that since this class extends {@code PrefixQuery}, it will
 * result in an Elasticsearch term query being issued. In order to make the query time analyzer (the "search analyzer")
 * kick in, we should officially use a match query (see {@link MatchConstraint}). However, it's pretty obvious what that
 * does (lowercase the search phrase), so we do it ourselves and issue a "pure" term query.
 *
 */
public class CaseInsensitivePrefixQuery extends PrefixQuery {

  public CaseInsensitivePrefixQuery(String field, Object value) {
    super(field, value.toString().toLowerCase());
  }

  protected String getField(String field) {
    return field + "." + MultiField.IGNORE_CASE.getName();
  }

}
