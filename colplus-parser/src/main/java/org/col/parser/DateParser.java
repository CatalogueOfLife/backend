package org.col.parser;

import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;

/**
 * CoL date parser wrapping the GBIF date parser
 */
public class DateParser extends GbifParserBased<LocalDate, TemporalAccessor> {
  public static final Parser<LocalDate> PARSER = new DateParser();

  public DateParser() {
    super(LocalDate.class, org.gbif.common.parsers.date.DateParsers.defaultTemporalParser());
  }

  @Override
  LocalDate convertFromGbif(TemporalAccessor value) {
    // try full date if possible
    if (value == null) {
      return null;

    } else if (value.isSupported(ChronoField.EPOCH_DAY)) {
      return LocalDate.from(value);

    } else if (value.isSupported(ChronoField.MONTH_OF_YEAR)) {
      return LocalDate.of(value.get(ChronoField.YEAR), value.get(ChronoField.MONTH_OF_YEAR), 1);

    } else if (value.isSupported(ChronoField.YEAR)) {
      return LocalDate.of(value.get(ChronoField.YEAR), 1, 1);
    }

    throw new IllegalArgumentException("Unsupported date");
  }

}
