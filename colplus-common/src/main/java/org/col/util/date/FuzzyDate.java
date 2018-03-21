package org.col.util.date;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.*;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.temporal.TemporalAccessor;

/**
 * A FuzzyDate encapsulates a {@link TemporalAccessor} instance of which at least the year is known.
 * If month or day are unknown it is said to be fuzzy.
 * 
 * @author Ayco Holleman
 *
 */
public final class FuzzyDate {

  private final TemporalAccessor ta;
  private final String verbatim;

  public FuzzyDate(TemporalAccessor ta, String verbatim) {
    if (!ta.isSupported(YEAR)) {
      // Won't happen when obtaining a FuzzyDate from the FuzzyDateParser, but
      // since this is a public constructor ...
      throw new IllegalArgumentException("Cannot create FuzzyDate without a year");
    }
    this.ta = ta;
    this.verbatim = verbatim;
  }

  /**
   * Returns a {@link LocalDate}, setting month and/or day to 1 if unknown.
   * 
   * @return
   */
  public LocalDate toLocalDate() {
    if (ta.isSupported(MONTH_OF_YEAR)) {
      if (ta.isSupported(DAY_OF_MONTH)) {
        return LocalDate.of(ta.get(YEAR), ta.get(MONTH_OF_YEAR), ta.get(DAY_OF_MONTH));
      }
      return LocalDate.of(ta.get(YEAR), ta.get(MONTH_OF_YEAR), 1);
    }
    return LocalDate.of(ta.get(YEAR), 1, 1);
  }


  /**
   * Returns a {@link LocalDate} if year, month and day are known; a {@link YearMonth} if year and
   * month are known; or a {@link Year} if only the year is known.
   * 
   * @return
   */
  public TemporalAccessor bestMatch() {
    if (ta.isSupported(MONTH_OF_YEAR)) {
      if (ta.isSupported(DAY_OF_MONTH)) {
        return LocalDate.of(ta.get(YEAR), ta.get(MONTH_OF_YEAR), ta.get(DAY_OF_MONTH));
      }
      return YearMonth.of(ta.get(YEAR), ta.get(MONTH_OF_YEAR));
    }
    return Year.of(ta.get(YEAR));
  }

  /**
   * Returns false if year, month and day are known, true otherwise.
   * 
   * @return
   */
  public boolean isPartial() {
    return !ta.isSupported(MONTH_OF_YEAR) || !ta.isSupported(DAY_OF_MONTH);
  }

}
