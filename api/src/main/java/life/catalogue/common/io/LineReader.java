package life.catalogue.common.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

public class LineReader implements Iterable<String>, AutoCloseable {
  private final BufferedReader br;
  private final boolean skipBlank;
  private final boolean skipComments;
  private int row = 0;
  private int currRow;

  public LineReader(InputStream steam) {
    this(UTF8IoUtils.readerFromStream(steam));
  }

  public LineReader(BufferedReader br) {
    this(br, true, true);
  }

  public LineReader(BufferedReader br, boolean skipBlank, boolean skipComments) {
    this.br = br;
    this.skipBlank = skipBlank;
    this.skipComments = skipComments;
  }

  public int getRow() {
    return currRow;
  }

  @NotNull
  @Override
  public Iterator<String> iterator() {
    return new LineIterator();
  }

  @Override
  public void close() {
    IOUtils.closeQuietly(br);
  }

  class LineIterator implements Iterator<String> {
    private String next;

    public LineIterator() {
      fetch();
    }

    @Override
    public boolean hasNext() {
      return next != null;
    }

    @Override
    public String next() {
      String val = next;
      currRow = row;
      fetch();
      return val;
    }

    private void fetch() {
      try {
        while (row == 0 || next != null) {
          next = br.readLine();
          row++;
          if (skipBlank && StringUtils.isBlank(next)) {
            continue;
          }
          if (skipComments && next.startsWith("#")) {
            continue;
          }
          break;
        }
      } catch (IOException e) {
        throw new RuntimeException("Error reading row "+row, e);
      }
    }
  }
}
