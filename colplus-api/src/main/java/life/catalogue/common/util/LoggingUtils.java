package life.catalogue.common.util;

import org.slf4j.MDC;

public class LoggingUtils {
  
  public static final String MDC_KEY_TASK = "task";
  public static final String MDC_KEY_DATASET = "dataset";
  public static final String MDC_KEY_SECTOR  = "sector";
  public static final String MDC_KEY_ATTEMPT = "attempt";
  
  public static void setDatasetMDC(int datasetKey, int attempt, Class<?> source) {
    setDatasetMDC(datasetKey, source);
    MDC.put(MDC_KEY_ATTEMPT, String.valueOf(attempt));
  }

  public static void setDatasetMDC(int datasetKey, Class<?> source) {
    MDC.put(MDC_KEY_TASK, source.getSimpleName());
    MDC.put(MDC_KEY_DATASET, String.valueOf(datasetKey));
  }

  public static void setSectorMDC(int sectorKey, int attempt, Class<?> source) {
    MDC.put(MDC_KEY_TASK, source.getSimpleName());
    MDC.put(MDC_KEY_SECTOR, String.valueOf(sectorKey));
    MDC.put(MDC_KEY_ATTEMPT, String.valueOf(attempt));
  }

  public static void removeDatasetMDC() {
    MDC.remove(MDC_KEY_TASK);
    MDC.remove(MDC_KEY_DATASET);
    MDC.remove(MDC_KEY_ATTEMPT);
  }
  
  public static void removeSectorMDC() {
    MDC.remove(MDC_KEY_TASK);
    MDC.remove(MDC_KEY_SECTOR);
    MDC.remove(MDC_KEY_ATTEMPT);
  }
}
