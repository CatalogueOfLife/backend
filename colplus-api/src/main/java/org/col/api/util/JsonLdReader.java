package org.col.api.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonLdReader {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  static {
    MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    MAPPER.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
    MAPPER.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
  }
  
  public static class JsonLD {
    @JsonProperty("@graph")
    public List<LDItem> graph;
  }
  public static class LDItem {
    @JsonProperty("@id")
    public String id;
    @JsonProperty("@type")
    public List<String> type;
    public Label description;
    public Label definition;
    public List<Label> prefLabel;
    public String inScheme;
    public String broader;
    public String isReplacedBy;
    public List<Label> comment;
  }
  public static class Label {
    @JsonProperty("@language")
    public String language;
    @JsonProperty("@value")
    public String value;
  
    @Override
    public String toString() {
      return language + ':' + value;
    }
  }
  
  public static JsonLD read(InputStream jsonLD) throws IOException {
    return MAPPER.readValue(jsonLD, JsonLD.class);
  }
  
}
