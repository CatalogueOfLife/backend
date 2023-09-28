package life.catalogue.common.date;

import java.time.*;
import java.util.Date;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;

public class DateUtils {
  
  public static Date asDate(LocalDate localDate) {
    return Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
  }

  public static Date asDate(LocalDateTime localDateTime) {
    return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
  }

  public static LocalDate asLocalDate(Date date) {
    return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
  }

  public static LocalDateTime asLocalDateTime(Date date) {
    return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
  }

  /**
   * Logs the time it took from start until now
   */
  public static void logDuration(Logger logger, Class<?> task, LocalDateTime start) {
    logDuration(logger, task.getSimpleName(), start, LocalDateTime.now());
  }

  /**
   * Logs the time it took from start until now
   */
  public static void logDuration(Logger logger, String task, LocalDateTime start) {
    logDuration(logger, task, start, LocalDateTime.now());
  }

  public static void logDuration(Logger logger, Class<?> task, LocalDateTime start, LocalDateTime end) {
    logDuration(logger, task.getSimpleName(), start, end);
  }

  public static void logDuration(Logger logger, String task, LocalDateTime start, LocalDateTime end) {
    Long dur = duration(start, end);
    if (dur != null) {
      logger.info("{} took {}", task, DurationFormatUtils.formatDuration(1000 * dur, "HH:mm:ss"));
    }
  }

  /**
   * Returns the duration in ms
   */
  public static Long duration(LocalDateTime start, LocalDateTime end) {
    if (start != null && end != null) {
      return end.toEpochSecond(ZoneOffset.UTC) - start.toEpochSecond(ZoneOffset.UTC);
    }
    return null;
  }


}
