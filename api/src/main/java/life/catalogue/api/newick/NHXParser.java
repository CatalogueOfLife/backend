package life.catalogue.api.newick;

import java.io.Reader;
import java.io.StreamTokenizer;

public class NHXParser {

  class NewickStreamTokenizer extends StreamTokenizer {

    public NewickStreamTokenizer(Reader r) {
      super(r);
      resetSyntax();
      wordChars(0, 255);
      whitespaceChars(0, '\n');

      ordinaryChar(';');
      ordinaryChar(',');
      ordinaryChar(')');
      ordinaryChar('(');
      ordinaryChar('[');
      ordinaryChar(']');
      ordinaryChar(':');
      ordinaryChar('\\');
      //commentChar('/');
      quoteChar('"');
      quoteChar('\'');
    }
  }
}
