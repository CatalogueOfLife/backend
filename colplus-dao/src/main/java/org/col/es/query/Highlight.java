package org.col.es.query;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * This class serializes to a valid "highlight" block within an Elasticsearch search request. Note though that we currently don't use this
 * class (nor set the "highlight" field within the EsSearchRequest class). The reason is that the things we want to highlight are tucked
 * away within the payload field of the EsNameUsage class, and Elasticsearch basically knows nothing about this field (it might even be
 * zipped). Instead we currently use a DIY highlighting solution (see NameSearchHighlighter class). Nevertheless it's probably a good idea
 * to keep the classes that just delegate everything to ES (e.g. this one).
 */
public class Highlight {

  public static enum Highlighter {
    UNIFIED("unified"), PLAIN("plain"), FAST_VECTOR("fvh");

    private String name;

    private Highlighter(String name) {
      this.name = name;
    }

    @JsonValue
    public String toString() {
      return name;
    }
  }

  public static enum Encoder {
    DEFAULT, HTML;

    @JsonValue
    public String toString() {
      return name().toLowerCase();
    }
  }

  private static final Map<String, Highlight> RECURSION_BLOCK = Collections.emptyMap();

  public static Highlight forFields(List<String> fields) {
    Highlight highlight = new Highlight(new HashMap<>());
    for (String field : fields) {
      Highlight h = new Highlight(RECURSION_BLOCK);
      highlight.fields.put(field, h);
    }
    return highlight;
  }

  Highlighter type;

  Encoder encoder;

  @JsonProperty("number_of_fragments")
  Integer numberOfFragments;

  @JsonProperty("fragment_size")
  Integer fragmentSize;

  @JsonProperty("highlight_query")
  Query highlightQuery;

  @JsonInclude(JsonInclude.Include.ALWAYS)
  private final Map<String, Highlight> fields;

  private Highlight(Map<String, Highlight> fields) {
    this.fields = fields;
  }

  public Highlight getSettingsForField(String field) {
    if (fields == RECURSION_BLOCK) {
      throw new IllegalStateException("No recursion please");
    }
    if (!fields.containsKey(field)) {
      throw new IllegalArgumentException("No such field: " + field);
    }
    return fields.get(field);
  }

  public void setType(Highlighter type) {
    this.type = type;
  }

  public void setEncoder(Encoder encoder) {
    this.encoder = encoder;
  }

  public void setNumberOfFragments(Integer numberOfFragments) {
    this.numberOfFragments = numberOfFragments;
  }

  public void setFragmentSize(Integer fragmentSize) {
    this.fragmentSize = fragmentSize;
  }

  public void setHighlightQuery(Query highlightQuery) {
    this.highlightQuery = highlightQuery;
  }

}
