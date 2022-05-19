package life.catalogue.importer;

import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.csv.MappingInfos;

import java.nio.file.Path;
import java.util.Optional;

public interface NeoInserter {

  /**
   * @throws NormalizationFailedException if some fatal error forced the normalization to stop.
   * @throws InterruptedException if the thread was interrupted, i.e. e.g. the import got canceled by a user
   */
  void insertAll() throws NormalizationFailedException, InterruptedException;

  void reportBadFks();

  Optional<DatasetWithSettings> readMetadata();

  MappingInfos getMappingFlags();

  Optional<Path> logo();
}
