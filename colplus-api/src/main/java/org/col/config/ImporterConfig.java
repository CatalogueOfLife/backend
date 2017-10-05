package org.col.config;

import javax.validation.constraints.Min;

/**
 *
 */
@SuppressWarnings("PublicField")
public class ImporterConfig {

  @Min(1)
  public int chunkSize = 10000;

  @Min(0)
  public int chunkMinSize = 100;
}
