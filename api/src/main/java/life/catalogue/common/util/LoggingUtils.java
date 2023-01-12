package life.catalogue.common.util;

import life.catalogue.api.model.DSID;

import java.util.Objects;
import java.util.UUID;

import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class LoggingUtils {

  /**
   * Taken from logback classic so we dont have to import logback here.
   * Marks finalized sessions, e.g. for sifting appenders.
   */
  public static final String FINALIZE_SESSION = "FINALIZE_SESSION";
  public static final String MDC_KEY_JOB = "job";
  public static final String MDC_KEY_TASK = "task";
  public static final String MDC_KEY_DATASET = "dataset";
  public static final String MDC_KEY_SECTOR  = "sector";
  public static final String MDC_KEY_ATTEMPT = "attempt";

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
  public static boolean setDatasetMDC(int datasetKey, Class<?> source) {
    if (Objects.equals(String.valueOf(datasetKey), MDC.get(MDC_KEY_DATASET))) {
      return false;
    }
    MDC.put(MDC_KEY_TASK, source.getSimpleName());
    MDC.put(MDC_KEY_DATASET, String.valueOf(datasetKey));
    return true;
  }

  public static void setSectorMDC(DSID<Integer> sectorKey, Integer attempt, Class<?> source) {
    MDC.put(MDC_KEY_TASK, source.getSimpleName());
    MDC.put(MDC_KEY_DATASET, String.valueOf(sectorKey.getDatasetKey()));
    MDC.put(MDC_KEY_SECTOR, String.valueOf(sectorKey.getId()));
    if (attempt != null) {
      MDC.put(MDC_KEY_ATTEMPT, String.valueOf(attempt));
    }
  }

  public static void removeJobMDC() {
    MDC.remove(MDC_KEY_JOB);
    MDC.remove(MDC_KEY_TASK);
  }

  public static void removeDatasetMDC() {
    MDC.remove(MDC_KEY_TASK);
    MDC.remove(MDC_KEY_DATASET);
    MDC.remove(MDC_KEY_ATTEMPT);
  }
  
  public static void removeSectorMDC() {
    MDC.remove(MDC_KEY_TASK);
    MDC.remove(MDC_KEY_DATASET);
    MDC.remove(MDC_KEY_SECTOR);
    MDC.remove(MDC_KEY_ATTEMPT);
  }
  public static final Marker FINALIZE_SESSION_MARKER = MarkerFactory.getMarker(FINALIZE_SESSION);

}
