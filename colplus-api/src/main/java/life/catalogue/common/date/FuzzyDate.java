package life.catalogue.common.date;

import java.time.*;
import java.time.temporal.TemporalAccessor;
import java.util.Objects;

import static java.time.temporal.ChronoField.*;

/**
 * A FuzzyDate encapsulates a {@link TemporalAccessor} instance which is guaranteed to have at least
 * its YEAR field set. Other fields may be unknown, in which case the date is said to be fuzzy.
 */
public final class FuzzyDate {

  private final TemporalAccessor ta;

  public FuzzyDate(TemporalAccessor ta) {
    // Won't happen when obtaining a FuzzyDate from the FuzzyDateParser, but
    // since this is a public constructor ...
    Objects.requireNonNull(ta, "ta");
    if (!ta.isSupported(YEAR)) {
      throw new IllegalArgumentException("Cannot create FuzzyDate without a year");
    }
    this.ta = ta;
  }

  /**
   * Returns a {@link LocalDate}, setting month and/or day to 1 if unknown.
   *
   * @return
   */
  public LocalDate toLocalDate() {
    if (ta.getClass() == LocalDate.class) {
      return (LocalDate) ta;
    }
    if (ta.getClass() == OffsetDateTime.class) {
      return ((OffsetDateTime) ta).toLocalDate();
    }
    if (ta.getClass() == LocalDateTime.class) {
      return ((LocalDateTime) ta).toLocalDate();
    }
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
  public boolean isFuzzyDate() {
    return !ta.isSupported(MONTH_OF_YEAR) || !ta.isSupported(DAY_OF_MONTH);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FuzzyDate fuzzyDate = (FuzzyDate) o;
    return Objects.equals(ta, fuzzyDate.ta);
  }

  @Override
  public int hashCode() {

    return Objects.hash(ta);
  }
}
