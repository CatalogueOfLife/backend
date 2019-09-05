package org.col.es.ddl;

import java.util.Map;

public class Analysis {

  private Map<String, Object> tokenizer;
  private Map<String, Object> analyzer;

  public Map<String, Object> getTokenizer() {
    return tokenizer;
  }

  public void setTokenizer(Map<String, Object> tokenizer) {
    this.tokenizer = tokenizer;
  }

  public Map<String, Object> getAnalyzer() {
    return analyzer;
  }

  public void setAnalyzer(Map<String, Object> analyzer) {
    this.analyzer = analyzer;
  }

}
