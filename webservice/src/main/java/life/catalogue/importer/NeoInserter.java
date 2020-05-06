package life.catalogue.importer;

import life.catalogue.api.model.DatasetWithSettings;

import java.nio.file.Path;
import java.util.Optional;

public interface NeoInserter {

  void insertAll() throws NormalizationFailedException;

  void reportBadFks();

  Optional<DatasetWithSettings> readMetadata();

  MappingFlags getMappingFlags();

  Optional<Path> logo();
}
