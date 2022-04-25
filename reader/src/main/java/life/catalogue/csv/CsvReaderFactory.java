package life.catalogue.csv;

import java.nio.file.Files;
import java.nio.file.Path;

public class CsvReaderFactory {

  public static CsvReader open(Path folder) {
    if (!Files.isDirectory(folder)) {
      throw new IllegalArgumentException("Not a directory: " + folder);
    }

    try {
      return ColdpReader.from(folder);
    } catch (Exception e) {
      // swallow
    }

    try {
      return AcefReader.from(folder);
    } catch (Exception e) {
      // swallow
    }

    try {
      return DwcaReader.from(folder);
    } catch (Exception e) {
      // swallow
    }
    throw new IllegalArgumentException("Unknown csv reader format in " + folder);
  }

}
