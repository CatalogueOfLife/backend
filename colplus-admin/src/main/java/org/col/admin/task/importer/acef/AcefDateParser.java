package org.col.admin.task.importer.acef;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.col.parser.Parser;

/*
 * Currently allows only exactly for those patterns actually encountered in the ACEF sources. We
 * could allow for more date patterns (e.g. month not case sensitive), or even allow the patterns to
 * be settable.
 */
public class AcefDateParser implements Parser<LocalDate> {

  public static final AcefDateParser PARSER = new AcefDateParser();

  private List<DateTimeFormatter> allowedYearPatterns;
  private List<DateTimeFormatter> allowedYearMonthPatterns;
  private List<DateTimeFormatter> allowedLocalDatePatterns;

  private AcefDateParser() {
    allowedYearPatterns = Arrays.asList(DateTimeFormatter.ofPattern("yyyy"));
    allowedYearMonthPatterns = Arrays.asList(DateTimeFormatter.ofPattern("MMM-yyyy"));
    allowedLocalDatePatterns = Arrays.asList(DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
        DateTimeFormatter.ofPattern("dd-MMM-yy"));
  }

  public Optional<LocalDate> parse(String value) {
    if (StringUtils.isBlank(value)) {
      return Optional.empty();
    }
    LocalDate date;
    if (null != (date = parseAsYearMonthDay(value))) {
      return Optional.of(date);
    }
    if (null != (date = parseAsYearMonth(value))) {
      return Optional.of(date);
    }
    if (null != (date = parseAsYear(value))) {
      return Optional.of(date);
    }
    throw new IllegalArgumentException("Unsupported date: " + value);
  }

  private LocalDate parseAsYearMonthDay(String value) {
    for (DateTimeFormatter dtf : allowedLocalDatePatterns) {
      try {
        return LocalDate.parse(value, dtf);
      } catch (DateTimeParseException e) {
      }
    }
    return null;
  }

  private LocalDate parseAsYearMonth(String value) {
    for (DateTimeFormatter dtf : allowedYearMonthPatterns) {
      try {
        YearMonth ym = YearMonth.parse(value, dtf);
        return ym.atDay(1);
      } catch (DateTimeParseException e) {
      }
    }
    return null;
  }

  private LocalDate parseAsYear(String value) {
    for (DateTimeFormatter dtf : allowedYearPatterns) {
      try {
        Year year = Year.parse(value, dtf);
        return LocalDate.of(year.getValue(), 1, 1);
      } catch (DateTimeParseException e) {
      }
    }
    return null;
  }

}
