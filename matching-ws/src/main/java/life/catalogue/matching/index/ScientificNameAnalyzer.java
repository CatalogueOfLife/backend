package life.catalogue.matching.index;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;

/** Keyword analyzer that uses the scientific name normalizer */
public class ScientificNameAnalyzer extends Analyzer {

  public static final int BUFFER_SIZE = 1024;

  public ScientificNameAnalyzer() {}

  @Override
  protected TokenStreamComponents createComponents(final String fieldName) {
    KeywordTokenizer source = new KeywordTokenizer(BUFFER_SIZE);
    TokenStream result = new LowerCaseFilter(new ScientificNameNormalizerFilter(source));
    return new TokenStreamComponents(source, result);
  }
}
