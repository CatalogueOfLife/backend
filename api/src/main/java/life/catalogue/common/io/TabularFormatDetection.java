package life.catalogue.common.io;

import life.catalogue.api.vocab.TabularFormat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class TabularFormatDetection {
  private static final int PROBE_LINES = 10;

  /**
   * Probes the given file, checking the first few non blank lines whether we have a tab delimited TSV or true comma delimited CSV.
   * A delimiter only counts if every probed line has the same, non zero number of it - commas inside double quotes are ignored.
   * Anything else is a single column, which is reported as TSV: the tab parser does no quoting and reads every line as one value,
   * so commas in values like "Aaroniella badonneli (Danks, 1950)" are kept. No header row is assumed.
   *
   * @param file    to test
   * @param charset
   * @return CSV or TSV
   * @throws IOException the file could not be read or has no content
   */
  public static TabularFormat detectFormat(File file, Charset charset) throws IOException {
    List<String> lines = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(file, charset))) {
      String line;
      while (lines.size() < PROBE_LINES && (line = reader.readLine()) != null) {
        if (!line.isBlank()) {
          lines.add(line);
        }
      }
    }
    if (lines.isEmpty()) throw new IOException("No tabular data found in "+file);
    if (consistent(lines, '\t')) return TabularFormat.TSV;
    if (consistent(lines, ',')) return TabularFormat.CSV;
    return TabularFormat.TSV;
  }

  private static boolean consistent(List<String> lines, char delimiter) {
    int expected = count(lines.get(0), delimiter);
    if (expected == 0) return false;
    for (String line : lines) {
      if (count(line, delimiter) != expected) return false;
    }
    return true;
  }

  private static int count(String line, char delimiter) {
    int cnt = 0;
    boolean quoted = false;
    for (char c : line.toCharArray()) {
      if (c == '"' && delimiter == ',') {
        quoted = !quoted;
      } else if (c == delimiter && !quoted) {
        cnt++;
      }
    }
    return cnt;
  }
}
