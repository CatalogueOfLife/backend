package life.catalogue.common.io;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.input.ReaderInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Closeables;
import com.univocity.parsers.common.IterableResult;
import com.univocity.parsers.common.ParsingContext;
import com.univocity.parsers.common.ResultIterator;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

public class TabReader implements IterableResult<String[], ParsingContext>, AutoCloseable {
  protected static Logger LOG = LoggerFactory.getLogger(TabReader.class);

  public static TabReader csv(Reader reader, int skip) throws IOException  {
    return csv(new ReaderInputStream(reader, StandardCharsets.UTF_8), StandardCharsets.UTF_8, skip, 2);
  }

  public static TabReader csv(File file, Charset charset, int skip) throws IOException  {
    return csv(file, charset, skip, 2);
  }

  public static TabReader csv(File file, Charset charset, int skip, int minColumns) throws IOException  {
    return custom(file, charset, ',', '"', skip, minColumns);
  }

  public static TabReader csv(InputStream stream, Charset charset, int skip, int minColumns) throws IOException  {
    return custom(stream, charset, ',', '"', skip, minColumns);
  }

  public static TabReader tab(Reader reader, int skip) throws IOException  {
    return tab(new ReaderInputStream(reader, StandardCharsets.UTF_8), StandardCharsets.UTF_8, skip, 2);
  }

  public static TabReader tab(File file, Charset charset, int skip) throws IOException  {
    return tab(file, charset, skip, 2);
  }

  public static TabReader tab(File file, Charset charset, int skip, int minColumns) throws IOException {
    return custom(file, charset, '\t', '"', skip, minColumns);
  }

  public static TabReader tab(InputStream stream, Charset charset, int skip, int minColumns) throws IOException {
    return custom(stream, charset, '\t', '"', skip, minColumns);
  }

  public static TabReader custom(File file, Charset charset, char delimiter, char quote, int skip, int minColumns) throws IOException  {
    return custom(new FileInputStream(file), charset, delimiter, quote, skip, minColumns);
  }

  public static TabReader custom(InputStream in, Charset charset, char delimiter, char quote, int skip, int minColumns) {
    CsvParserSettings cfg = new CsvParserSettings();
    cfg.getFormat().setDelimiter(delimiter);
    cfg.getFormat().setQuote(quote);
    cfg.getFormat().setQuoteEscape(quote);

    cfg.setDelimiterDetectionEnabled(false);
    cfg.setSkipEmptyLines(true);
    cfg.trimValues(true);
    cfg.setReadInputOnSeparateThread(false);
    cfg.setNullValue(null);
    cfg.setMaxColumns(64);
    cfg.setMaxCharsPerColumn(1024 * cfg.getMaxColumns());

    return new TabReader(new CsvParser(cfg), in, charset, skip, minColumns);
  }

  private final CsvParser parser;
  private final int skip;
  private final int minColumns;
  private final InputStream stream;
  private final Charset charset;

  public TabReader(CsvParser parser, InputStream stream, Charset charset, int skip, int minColumns) {
    this.parser = parser;
    this.skip = skip;
    this.minColumns = minColumns;
    this.stream = stream;
    this.charset = charset;
  }

  @Override
  public ParsingContext getContext() {
    return parser.getContext();
  }

  @Override
  public ResultIterator<String[], ParsingContext> iterator() {
    return new RowSkipper();
  }

  @Override
  public void close() {
    if (stream != null) {
      Closeables.closeQuietly(stream);
    }
  }

  class RowSkipper implements ResultIterator<String[], ParsingContext> {
    private final ResultIterator<String[], ParsingContext> it;

    RowSkipper() {
      it = parser.iterate(stream, charset).iterator();
      if (skip>0) {
        for (int x=0; x<skip; x++) {
          it.next();
        }
      }
    }

    @Override
    public boolean hasNext() {
      return it.hasNext();
    }

    @Override
    public String[] next() {
      String[] row = it.next();
      while (row.length < minColumns && it.hasNext()) {
        LOG.warn("Row {} with too little columns", it.getContext().currentLine());
        row = it.next();
      }
      return row;
    }

    @Override
    public ParsingContext getContext() {
      return null;
    }
  }
}
