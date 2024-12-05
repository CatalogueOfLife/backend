package life.catalogue.matching.util;

import com.google.common.base.Throwables;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Utility class to analyze strings using a Lucene analyzer.
 */
public class LuceneUtils {

  private LuceneUtils() {}

  public static List<String> analyzeString(Analyzer analyzer, String string) {
    List<String> result = new ArrayList<>();
    try (TokenStream stream = analyzer.tokenStream(null, new StringReader(string))) {
      stream.reset();
      while (stream.incrementToken()) {
        CharTermAttribute charTermAttribute = stream.getAttribute(CharTermAttribute.class);
        result.add(charTermAttribute.toString());
      }
    } catch (IOException e) {
      // not thrown b/c we're using a string reader...
      Throwables.propagateIfPossible(e, RuntimeException.class);
    }
    return result;
  }
}
