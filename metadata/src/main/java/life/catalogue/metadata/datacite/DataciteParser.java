package life.catalogue.metadata.datacite;

import life.catalogue.api.model.DatasetWithSettings;

import java.io.InputStream;
import java.util.Optional;

import org.apache.commons.lang3.NotImplementedException;

public class DataciteParser {

  public static Optional<DatasetWithSettings> parse(InputStream stream) {
    throw new NotImplementedException("DataCite XML is currently not supported");
  }

}
