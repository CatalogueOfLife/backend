package life.catalogue.common.util;

import life.catalogue.api.model.DSID;

import java.util.UUID;

import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class LoggingUtils {

  /**
   * Marks finished jobs, e.g. for job log appenders.
   */
  public static final Marker JOB_STARTING_MARKER = MarkerFactory.getMarker("JOB_STARTING");
  public static final Marker JOB_FINISHED_MARKER = MarkerFactory.getMarker("JOB_FINISHED");
  public static final Marker COPY_RELEASE_LOGS_MARKER = MarkerFactory.getMarker("COPY_RELEASE_LOGS");
  public static final String MDC_KEY_JOB = "job";
  public static final String MDC_KEY_TASK = "task";
  public static final String MDC_KEY_DATASET = "dataset";
  public static final String MDC_KEY_DATASET_TASK = "dataset_task";
  public static final String MDC_KEY_SECTOR  = "sector";
  public static final String MDC_KEY_ATTEMPT = "attempt";
  public static final String MDC_KEY_SOURCE = "source";

  public static void setJobMDC(UUID key, Class<?> jobClass) {
    MDC.put(MDC_KEY_JOB, key.toString());
    MDC.put(MDC_KEY_TASK, jobClass.getSimpleName());
  }

  public static void setDatasetMDC(int datasetKey, Integer attempt, Class<?> source) {
    setDatasetMDC(datasetKey, source);
    if (attempt != null) {
      MDC.put(MDC_KEY_ATTEMPT, String.valueOf(attempt));
    }
  }

  /**
   * Sets the dataset key in MDC if it does not exist yet.
   * @return true if a new datasetKey has been set, false if the same key already existed before.
   */
  public static void setDatasetMDC(int datasetKey, Class<?> source) {
    MDC.put(MDC_KEY_DATASET_TASK, source.getSimpleName());
    MDC.put(MDC_KEY_DATASET, String.valueOf(datasetKey));
  }

  public static void setSectorMDC(DSID<Integer> sectorKey, Integer attempt) {
    MDC.put(MDC_KEY_SECTOR, String.valueOf(sectorKey.getId()));
    if (attempt != null) {
      MDC.put(MDC_KEY_ATTEMPT, String.valueOf(attempt));
    }
  }

  public static void setSectorAndDatasetMDC(DSID<Integer> sectorKey, Integer attempt, Class<?> source) {
    setDatasetMDC(sectorKey.getDatasetKey(), source);
    setSectorMDC(sectorKey, attempt);
  }

  public static void removeJobMDC() {
    MDC.remove(MDC_KEY_JOB);
    MDC.remove(MDC_KEY_TASK);
  }

  public static void removeDatasetMDC() {
    MDC.remove(MDC_KEY_DATASET_TASK);
    MDC.remove(MDC_KEY_DATASET);
  }
  
  public static void removeSectorMDC() {
    MDC.remove(MDC_KEY_SECTOR);
    MDC.remove(MDC_KEY_ATTEMPT);
  }
  public static void setSourceMDC(Integer sourceDatasetKey) {
    MDC.put(MDC_KEY_SOURCE, sourceDatasetKey.toString());
  }
  public static void removeSourceMDC() {
    MDC.remove(MDC_KEY_SOURCE);
  }
}
