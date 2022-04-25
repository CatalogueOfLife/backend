package life.catalogue.importer;

import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.csv.MappingInfos;

import java.nio.file.Path;
import java.util.Optional;

public interface NeoInserter {

  void insertAll() throws NormalizationFailedException;

  void reportBadFks();

  Optional<DatasetWithSettings> readMetadata();

  MappingInfos getMappingFlags();

  Optional<Path> logo();
}
