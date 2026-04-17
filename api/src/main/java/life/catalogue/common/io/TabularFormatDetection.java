package life.catalogue.common.io;

import life.catalogue.api.vocab.TabularFormat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;

public class TabularFormatDetection {
  private static final int PROBE_LINES = 10;

  /**
   * Probes the given file, checking the first few lines whether we have a tab delimited TSV or true comma delimited CSV.
   *
   * @param file    to test
   * @param charset
   * @return CSV or TSV
   * @throws IOException 10 lines of the file could not be read or no delimiter was found
   */
  public static TabularFormat detectFormat(File file, Charset charset) throws IOException {
    int tabs = 0;
    int commas = 0;
    try (BufferedReader reader = new BufferedReader(new FileReader(file, charset))) {
      for (int i = 0; i < PROBE_LINES; i++) {
        String line = reader.readLine();
        if (line == null) break;
        for (char c : line.toCharArray()) {
          if (c == '\t') tabs++;
          else if (c == ',') commas++;
        }
      }
    }
    if (tabs == 0 && commas == 0) throw new IOException("No tabular data found in "+file);
    return tabs >= commas ? TabularFormat.TSV : TabularFormat.CSV;
  }
}
