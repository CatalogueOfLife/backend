package org.col.es.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.col.api.search.NameSearchRequest.SortBy;

public class SortBuilder {

  public static SortBuilder create(SortBy sortBy) {
    if (sortBy == SortBy.TAXONOMIC) {
      return SB_TAXONOMIC;
    }
    if (sortBy == SortBy.NAME) {
      return SB_NAME;
    }
    return SB_NATIVE;
  }

  /**
   * Sorts document by index order ("_doc"). This is the fastest sort, but you must explicitly ask for it if you're not interested in any
   * particular sort order, otherwise the sort will be on score descending.
   */
  private static final SortBuilder SB_NATIVE = new SortBuilder("_doc");
  private static final SortBuilder SB_NAME = new SortBuilder("scientificName");
  private static final SortBuilder SB_TAXONOMIC = new SortBuilder("rank").and("scientificName");

  // Each element is either a string (the sort field) or a single-entry hash map with the key being the
  // sort field and the value being the sort options.
  private final List<Object> fields = new ArrayList<>();

  public SortBuilder(String field) {
    and(field);
  }

  public SortBuilder(String field, SortOptions options) {
    and(field, options);
  }

  public SortBuilder and(String field) {
    fields.add(field);
    return this;
  }

  public SortBuilder and(String field, SortOptions options) {
    fields.add(new HashMap<String, SortOptions>() {
      {
        put(field, options);
      }
    });
    return this;
  }

  public List<Object> build() {
    return fields;
  }

}
