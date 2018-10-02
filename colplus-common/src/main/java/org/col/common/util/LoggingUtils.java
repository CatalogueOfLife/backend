package org.col.common.util;

import org.slf4j.MDC;

public class LoggingUtils {

  public static final String MDC_KEY_TASK = "task";
  public static final String MDC_KEY_DATASET = "dataset";

  public static void setMDC(int datasetKey, Class<?> source) {
    MDC.put(MDC_KEY_TASK, source.getSimpleName());
    MDC.put(MDC_KEY_DATASET, String.valueOf(datasetKey));
  }

  public static void removeMDC() {
    MDC.remove(MDC_KEY_TASK);
    MDC.remove(MDC_KEY_DATASET);
  }

}
