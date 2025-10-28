package life.catalogue.matching.controller;

import life.catalogue.matching.model.ClassificationQuery;
import life.catalogue.matching.model.NameUsageQuery;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.MDC;

import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

public class ApiLogger {

  public static void logRequest(Logger log, String requestPath, NameUsageQuery query, StopWatch watch) {
    if (log.isInfoEnabled()) {
      StringJoiner joiner = new StringJoiner(", ");
      addIfNotNull(joiner, query.usageKey);
      addIfNotNull(joiner, query.taxonID);
      addIfNotNull(joiner, query.taxonConceptID);
      addIfNotNull(joiner, query.scientificNameID);
      addIfNotNull(joiner, query.scientificName);
      addIfNotNull(joiner, query.authorship);
      addIfNotNull(joiner, query.rank);
      addIfNotNull(joiner, query.genericName);
      addIfNotNull(joiner, query.specificEpithet);
      addIfNotNull(joiner, query.classification);

      MDC.put("executionTime",  watch.getTime(TimeUnit.MILLISECONDS) + "ms");
      MDC.put("requestPath", requestPath);
      log.info(joiner.toString());
      MDC.clear();
    }
  }

  private static void addIfNotNull(StringJoiner joiner, Object value) {
    if (Objects.nonNull(value) && !value.toString().isEmpty()) {
      joiner.add(value.toString());
    }
  }

  private static void addIfNotNull(StringJoiner joiner, ClassificationQuery value) {
    if (Objects.nonNull(value) && !value.toString().isEmpty()) {
      joiner.add(value.toString());
    }
  }

  public static void logRequest(Logger log, String requestPath, String query, StopWatch watch) {
    MDC.put("executionTime",  watch.getTime(TimeUnit.MILLISECONDS)+ "ms");
    MDC.put("requestPath", requestPath);
    log.info("{}: {}", requestPath, query);
    MDC.clear();
  }
}
