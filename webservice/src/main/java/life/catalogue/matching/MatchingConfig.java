package life.catalogue.matching;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import life.catalogue.config.ContinuousImportConfig;

import javax.annotation.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 *
 */
@SuppressWarnings("PublicField")
public class MatchingConfig {

  /**
   * Directory to store data neéded for matching
   */
  public File storage;

  /**
   * Kryo poolsize for the storage layer
   */
  public int poolSize = 256;
}
