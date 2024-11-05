package life.catalogue.common.date;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import life.catalogue.common.io.DownloadUtil;
import life.catalogue.common.io.Resources;
import life.catalogue.common.io.TabReader;
import life.catalogue.common.io.UTF8IoUtils;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DateUtils {
  private static final Logger LOG = LoggerFactory.getLogger(DateUtils.class);
  private static final Pattern TIME_COMMENT = Pattern.compile("\\s*\\(.*\\)\\s*$");
  private static final Pattern TIME_REDUNDANT = Pattern.compile("\\s+[A-Z]+\\s*([+-]\\d{4})\\s*$");
  private static final Pattern TIME_ZONES = Pattern.compile("\\s+([A-Z]+)\\s*$");
  private static final Map<String, String> ZONES;
  static {
    var map = new HashMap<String, String>();
    Resources.tabRows("life/catalogue/common/date/timezones.tsv").forEach(row -> {
      var key = row[0];
      var zone = row[2];
      map.put(key, zone);
    });
    ZONES = Map.copyOf(map);
  }

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
   * Parses RFC 1123 date time strings as found in http headers.
   * It mostly relies on the DateTimeFormatter.RFC_1123_DATE_TIME,
   * but extends it to support comments and other timezone abbreviations than just GMT.
   * @return Optional of the parsed date time or empty if unparsable or null
   */
  public static Optional<TemporalAccessor> parseRFC1123(String x) {
    TemporalAccessor dt = null;
    if (x != null) {
      x = TIME_COMMENT.matcher(x).replaceFirst("");
      x = TIME_REDUNDANT.matcher(x).replaceFirst(" $1");
      var m = TIME_ZONES.matcher(x);
      if (m.find()) {
        if (ZONES.containsKey(m.group(1))) {
          x = m.replaceFirst(" " + ZONES.get(m.group(1)));
        }
      }
      try {
        dt = DateTimeFormatter.RFC_1123_DATE_TIME.parse(x);
      } catch (DateTimeParseException e) {
        LOG.warn("Failed to parse modified date {}", x, e);
      }
    }
    return Optional.ofNullable(dt);
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
