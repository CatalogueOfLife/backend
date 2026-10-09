package life.catalogue.importer.corpus;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.annotation.Nullable;

/**
 * Reads and writes the tab separated files of the interpreter corpus tools.
 * They all use the text format of postgres COPY: a tab between columns, backslash escapes for backslash, tab, newline
 * and carriage return inside a value, and {@code \N} for null - what the export script writes, and what the runner and
 * the diff write so that any value round trips. A file ending in {@code .gz} is gzipped.
 */
public class CorpusIO {
  static final String NULL = "\\N";

  private CorpusIO() {
  }

  public static BufferedReader reader(File f) throws IOException {
    InputStream in = new FileInputStream(f);
    if (f.getName().endsWith(".gz")) {
      in = new GZIPInputStream(in, 1 << 16);
    }
    return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16);
  }

  public static BufferedWriter writer(File f) throws IOException {
    File dir = f.getAbsoluteFile().getParentFile();
    if (dir != null) {
      dir.mkdirs();
    }
    OutputStream out = new FileOutputStream(f);
    if (f.getName().endsWith(".gz")) {
      out = new GZIPOutputStream(out, 1 << 16);
    }
    return new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), 1 << 16);
  }

  /**
   * Splits a line into its unescaped values. A line with fewer columns than expected is padded with nulls,
   * the parser corpus drops trailing empty columns.
   */
  public static String[] split(String line, int columns) {
    List<String> vals = new ArrayList<>(columns);
    int start = 0;
    for (int i = 0; i <= line.length(); i++) {
      if (i == line.length() || line.charAt(i) == '\t') {
        vals.add(unescape(line.substring(start, i)));
        start = i + 1;
      }
    }
    while (vals.size() < columns) {
      vals.add(null);
    }
    return vals.toArray(new String[0]);
  }

  @Nullable
  static String unescape(String x) {
    if (x.isEmpty() || x.equals(NULL)) {
      return null;
    }
    if (x.indexOf('\\') < 0) {
      return x;
    }
    StringBuilder sb = new StringBuilder(x.length());
    for (int i = 0; i < x.length(); i++) {
      char c = x.charAt(i);
      if (c == '\\' && i + 1 < x.length()) {
        char n = x.charAt(++i);
        switch (n) {
          case 't' -> sb.append('\t');
          case 'n' -> sb.append('\n');
          case 'r' -> sb.append('\r');
          default -> sb.append(n);
        }
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  static String escape(@Nullable Object x) {
    if (x == null) {
      return NULL;
    }
    String s = x.toString();
    if (s.isEmpty()) {
      return NULL;
    }
    StringBuilder sb = null;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      String esc = switch (c) {
        case '\\' -> "\\\\";
        case '\t' -> "\\t";
        case '\n' -> "\\n";
        case '\r' -> "\\r";
        default -> null;
      };
      if (esc != null && sb == null) {
        sb = new StringBuilder(s.length() + 8).append(s, 0, i);
      }
      if (sb != null) {
        if (esc != null) {
          sb.append(esc);
        } else {
          sb.append(c);
        }
      }
    }
    return sb == null ? s : sb.toString();
  }

  public static void writeRow(Writer w, Object... values) throws IOException {
    for (int i = 0; i < values.length; i++) {
      if (i > 0) {
        w.write('\t');
      }
      w.write(escape(values[i]));
    }
    w.write('\n');
  }
}
