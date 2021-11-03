package org.catalogueoflife.coldp.gen;

import com.univocity.parsers.common.IterableResult;
import com.univocity.parsers.common.ParsingContext;
import com.univocity.parsers.common.ResultIterator;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.Charset;

public class TabReader implements IterableResult<String[], ParsingContext> {
  protected static Logger LOG = LoggerFactory.getLogger(TabReader.class);

  public static TabReader csv(File file, Charset charset, int skip) {
    return csv(file, charset, skip, Integer.MAX_VALUE);
  }

  public static TabReader csv(File file, Charset charset, int skip, int minColumns) {
    return custom(file, charset, ',', '"', skip, minColumns);
  }

  public static TabReader tab(File file, Charset charset, int skip) {
    return tab(file, charset, skip, Integer.MAX_VALUE);
  }

  public static TabReader tab(File file, Charset charset, int skip, int minColumns) {
    return custom(file, charset, '\t', '"', skip, minColumns);
  }

  public static TabReader custom(File file, Charset charset, char delimiter, char quote, int skip, int minColumns) {
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

    return new TabReader(new CsvParser(cfg), file, charset, skip, minColumns);
  }

  private final CsvParser parser;
  private final int skip;
  private final int minColumns;
  private final File file;
  private final Charset charset;

  public TabReader(CsvParser parser, File file, Charset charset, int skip, int minColumns) {
    this.parser = parser;
    this.skip = skip;
    this.minColumns = minColumns;
    this.file = file;
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

  class RowSkipper implements ResultIterator<String[], ParsingContext> {
    private final ResultIterator<String[], ParsingContext> it;

    RowSkipper() {
      it = parser.iterate(file, charset).iterator();
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
