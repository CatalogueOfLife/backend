package life.catalogue.es.ddl;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A {@code MultiField} is a virtual field underneath a {@link SimpleField regular field} that specifies an alternative way of indexing the
 * field (more specifically: an extra analyzer to be applied to the data stored in that field). Note that there is no multifield for no-op
 * as-is indexing using the "keyword" analyzer. If a <code>SimpleField</code> was created from a stringy Java datatype, it will <i>by
 * default</i> be indexed as-is, unless you explicitly disable this. If you don't want as-is indexing (and you don't specify any other
 * analyzer either), you must explicitly decorate the Java field with the {@link NotIndexed} annotation. (Alternatively, you could specify
 * an empty {@link Analyzers} array.)
 */
public class MultiField extends ESField {

  private final String analyzer;
  @JsonProperty("search_analyzer")
  private final String searchAnalyzer;

  MultiField(String name, String analyzer, String searchAnalyzer) {
    super();
    this.type = ESDataType.TEXT;
    this.name = name;
    this.analyzer = analyzer;
    this.searchAnalyzer = searchAnalyzer;
  }

  public String getAnalyzer() {
    return analyzer;
  }

  public String getSearchAnalyzer() {
    return searchAnalyzer;
  }

  @Override
  public String toString() {
    return "MultiField{" +
        "name='" + name + '\'' +
        ", type=" + type +
        ", analyzer='" + analyzer + '\'' +
        ", searchAnalyzer='" + searchAnalyzer + '\'' +
        '}';
  }
}
