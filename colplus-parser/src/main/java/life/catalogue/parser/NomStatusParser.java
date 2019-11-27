package life.catalogue.parser;

import java.io.IOException;

import com.google.common.base.Enums;
import com.google.common.base.Optional;
import life.catalogue.api.vocab.NomStatus;
import org.gbif.utils.file.csv.CSVReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class NomStatusParser extends EnumParser<NomStatus> {
  private static final Logger LOG = LoggerFactory.getLogger(NomStatusParser.class);
  
  public static final NomStatusParser PARSER = new NomStatusParser();
  
  public NomStatusParser() {
    super("nomstatus.csv", NomStatus.class);
  
    try {
      CSVReader reader = dictReader("nomstatus-suffices.csv");
      while (reader.hasNext()) {
        String[] row = reader.next();
        if (row.length == 0) continue;
        if (row.length != 2) {
          throw new IllegalStateException("Bad nomstatus-suffices.csv file");
        }
        Optional<NomStatus> val = Enums.getIfPresent(NomStatus.class, row[1]);
        if (val.isPresent()) {
          for (String suffix : new String[]{"a", "um"}) {
            add(row[0]+suffix, val.get());
            add("nom " + row[0]+suffix, val.get());
            add("nomen " + row[0]+suffix, val.get());
            add("nomina " + row[0]+suffix, val.get());
          }
        } else {
          throw new IllegalStateException("Bad nomstatus-suffices.csv enum value " + row[0]);
        }
      }
      reader.close();

    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    for (NomStatus st : NomStatus.values()) {
      add(st.getBotanicalLabel(), st);
      add(st.getZoologicalLabel(), st);
      add(st.getAbbreviatedLabel(), st);
    }
  }
  
}
