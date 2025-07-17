package life.catalogue.config;

import java.time.LocalDate;

import javax.annotation.Nullable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class ContinuousImportConfig {

  /**
   * Duration in minutes the continous import scheduler will fall to sleep if imports are running already.
   * Zero will turn off continuous importing.
   */
  @Min(0)
  public int polling = 0;

  /**
   * The number of allowed datasets already in the queue before the continuous import polling adds more.
   */
  @Min(0)
  @Max(100)
  public int threshold = 0;

  /**
   * The number of datasets to queue during one import poll.
   */
  @Min(1)
  @Max(1000)
  public int batchSize = 10;

  /**
   * The default frequency number in days to use between import attempts when no explicit frequency is configured
   */
  @Min(1)
  @Max(365)
  public int defaultFrequency = 7;

  /**
   * If set, imports will be forced if the last import of the dataset was before the configured date.
   * This setting can be used to deploy major changes of the importer and slowly reimport all datasets
   * even if the archives have not changed.
   */
  @Nullable
  public LocalDate forceBefore;

}
