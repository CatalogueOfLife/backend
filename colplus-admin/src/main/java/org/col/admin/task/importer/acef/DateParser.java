package org.col.admin.task.importer.acef;

import org.apache.commons.lang3.StringUtils;
import org.col.parser.Parser;
import org.col.parser.UnparsableException;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/*
 * Currently allows only exactly for those patterns actually encountered in the ACEF sources. We
 * could allow for more date patterns (e.g. month not case sensitive), or even allow the patterns to
 * be settable.
 */
public class DateParser implements Parser<LocalDate> {
  
  public static void main(String[] args) {
    String pattern = "[MMM-][dd-]yyyy";
    DateTimeFormatter dtf = new DateTimeFormatterBuilder().appendPattern(pattern).parseCaseInsensitive().toFormatter();
    TemporalAccessor ta = dtf.parse("Apr-1972");
    System.out.println("XXX: " + ta.getClass());
    System.out.println("XXX: " + (ta.isSupported(ChronoField.YEAR)? Year.from(ta) : -1));
    System.out.println("XXX: " + ta.isSupported(ChronoField.MONTH_OF_YEAR));
    System.out.println("XXX: " + ta.isSupported(ChronoField.DAY_OF_MONTH));
    System.out.println("XXX: " + ta.isSupported(ChronoField.SECOND_OF_MINUTE));
  }

  public static final DateParser PARSER = new DateParser();

  private List<DateTimeFormatter> allowedYearPatterns;
  private List<DateTimeFormatter> allowedYearMonthPatterns;
  private List<DateTimeFormatter> allowedLocalDatePatterns;

  private DateParser() {
    allowedYearPatterns = Arrays.asList(DateTimeFormatter.ofPattern("yyyy"));
    allowedYearMonthPatterns = Arrays.asList(DateTimeFormatter.ofPattern("MMM-yyyy"));
    allowedLocalDatePatterns = Arrays.asList(DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
        DateTimeFormatter.ofPattern("dd-MMM-yy"));
  }

  @Override
  public Optional<LocalDate> parse(String value) throws UnparsableException {
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
    throw new UnparsableException("Unsupported date: " + value);
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
