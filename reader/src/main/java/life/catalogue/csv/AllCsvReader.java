package life.catalogue.csv;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A CSV Reader that supports all txt, csv, tsv files in a given folder.
 * No need to match to specific term entities.
 */
public class AllCsvReader extends CsvReader {

  private AllCsvReader(Path folder) throws IOException {
    super(folder, null, null);
  }
  
  public static AllCsvReader from(Path folder) throws IOException {
    return new AllCsvReader(folder);
  }
  
  protected void validate() throws SourceInvalidException {
    // nothing to check!
    // This is supposed to allow for any and also no csv files to be read
  }
  
}
