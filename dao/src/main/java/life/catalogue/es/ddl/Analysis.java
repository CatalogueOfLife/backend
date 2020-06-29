package life.catalogue.es.ddl;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * The heart of an index definition: its analyzers, tokenizers and character filters. However we currently just define them as raw maps
 * since we don't do anything with them (except passing them on to Elasticsearch). Also, we currently don't use token filters, so they are
 * not defined here either yet.
 */
public class Analysis {

  private Map<String, Object> analyzer;
  private Map<String, Object> filter;
  @JsonProperty("char_filter")
  private Map<String, Object> charFilter;
  private Map<String, Object> tokenizer;

  public Map<String, Object> getAnalyzer() {
    return analyzer;
  }

  public Map<String, Object> getFilter() {
    return filter;
  }

  public void setFilter(Map<String, Object> filter) {
    this.filter = filter;
  }

  public Map<String, Object> getCharFilter() {
    return charFilter;
  }

  public Map<String, Object> getTokenizer() {
    return tokenizer;
  }

  public void setAnalyzer(Map<String, Object> analyzer) {
    this.analyzer = analyzer;
  }

  public void setCharFilter(Map<String, Object> charFilter) {
    this.charFilter = charFilter;
  }

  public void setTokenizer(Map<String, Object> tokenizer) {
    this.tokenizer = tokenizer;
  }

}
