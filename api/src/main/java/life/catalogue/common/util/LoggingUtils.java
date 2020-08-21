package life.catalogue.common.util;

import life.catalogue.api.model.DSID;
import org.slf4j.MDC;

import java.util.UUID;

public class LoggingUtils {

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

  public static void setDatasetMDC(int datasetKey, Class<?> source) {
    MDC.put(MDC_KEY_TASK, source.getSimpleName());
    MDC.put(MDC_KEY_DATASET, String.valueOf(datasetKey));
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
}
