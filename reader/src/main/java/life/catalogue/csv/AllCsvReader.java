package life.catalogue.csv;

import org.gbif.dwc.terms.AcefTerm;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

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
    super.validate();
  }
  
}
