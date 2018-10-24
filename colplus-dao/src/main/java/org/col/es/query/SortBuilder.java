package org.col.es.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.col.api.search.NameSearchRequest;

public class SortBuilder {

  /**
   * Sorts document by index order ("_doc"). This is the fastest sort, but you must explicitly ask
   * for it if you're not interested in any particular sort order, otherwise the sort will be on
   * score descending.
   */
  public static final SortBuilder DEFAULT_SORT = new SortBuilder("_doc");

  private final List<Object> fields;

  public SortBuilder(NameSearchRequest.SortBy sortBy) {
    fields = new ArrayList<>();
    addField(sortBy);
  }

  public SortBuilder(NameSearchRequest.SortBy sortBy, SortOptions options) {
    fields = new ArrayList<>();
    addField(sortBy, options);
  }

  private SortBuilder(String field) {
    fields = Collections.unmodifiableList(Arrays.asList(field));
  }

  public SortBuilder addField(NameSearchRequest.SortBy sortBy) {
    fields.add(getField(sortBy));
    return this;
  }

  public SortBuilder addField(NameSearchRequest.SortBy sortBy, SortOptions options) {
    fields.add(new HashMap<String, SortOptions>() {
      {
        put(getField(sortBy), options);
      }
    });
    return this;
  }

  public List<Object> build() {
    return fields;
  }

  private static String getField(NameSearchRequest.SortBy sortBy) {
    switch (sortBy) {
      case KEY:
        return "nameId";
      case NAME:
        return "scientificName";
      case RELEVANCE:
      default:
        return "_score";
    }
  }

}
