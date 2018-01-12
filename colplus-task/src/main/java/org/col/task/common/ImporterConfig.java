package org.col.task.common;

import javax.validation.constraints.Min;

/**
 *
 */
@SuppressWarnings("PublicField")
public class ImporterConfig {

  @Min(1)
  public int batchSize = 10000;

}
