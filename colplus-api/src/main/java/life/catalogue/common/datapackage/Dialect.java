package life.catalogue.common.datapackage;

import java.util.Objects;

public class Dialect {
  public static final Dialect CSV = new Dialect(",", '"', true);
  public static final Dialect TSV = new Dialect("\t", null, false);

  private String delimiter = "\t";
  private Character quoteChar = null;
  private boolean doubleQuote = false;
  private boolean header = true;
  
  public Dialect() {
  }
  
  public Dialect(String delimiter, Character quoteChar, boolean doubleQuote) {
    this.delimiter = delimiter;
    this.quoteChar = quoteChar;
    this.doubleQuote = doubleQuote;
  }
  
  public String getDelimiter() {
    return delimiter;
  }
  
  public void setDelimiter(String delimiter) {
    this.delimiter = delimiter;
  }
  
  public Character getQuoteChar() {
    return quoteChar;
  }
  
  public void setQuoteChar(Character quoteChar) {
    this.quoteChar = quoteChar;
  }
  
  public boolean isDoubleQuote() {
    return doubleQuote;
  }
  
  public void setDoubleQuote(boolean doubleQuote) {
    this.doubleQuote = doubleQuote;
  }
  
  public boolean isHeader() {
    return header;
  }
  
  public void setHeader(boolean header) {
    this.header = header;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Dialect dialect = (Dialect) o;
    return doubleQuote == dialect.doubleQuote &&
        header == dialect.header &&
        Objects.equals(delimiter, dialect.delimiter) &&
        Objects.equals(quoteChar, dialect.quoteChar);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(delimiter, quoteChar, doubleQuote, header);
  }
}
