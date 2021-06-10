package life.catalogue.common.date;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import de.undercouch.citeproc.csl.CSLDate;
import de.undercouch.citeproc.csl.CSLDateBuilder;
import life.catalogue.api.model.CslDate;
import org.apache.commons.lang3.StringUtils;

import java.time.*;
import java.time.temporal.TemporalAccessor;
import java.util.Objects;

import static java.time.temporal.ChronoField.*;

/**
 * A FuzzyDate encapsulates a {@link TemporalAccessor} instance which is guaranteed to have at least
 * its YEAR field set. Other fields may be unknown, in which case the date is said to be fuzzy.
 */
public final class FuzzyDate {
  private static final int DAY_PER_MONTH = 32;
  private static final int DAY_PER_YEAR  = DAY_PER_MONTH * 13;
  // only Year, YearMonth or LocalDate allowed here!!!
  private final TemporalAccessor ta;

  @JsonCreator
  public static FuzzyDate of(int year) {
    return new FuzzyDate(Year.of(year));
  }

  public static FuzzyDate of(int year, int month) {
    return new FuzzyDate(YearMonth.of(year, month));
  }

  public static FuzzyDate of(int year, int month, int day) {
    return new FuzzyDate(LocalDate.of(year, month, day));
  }

  public static FuzzyDate now() {
    return new FuzzyDate(LocalDate.now());
  }

  /**
   * Potentially incomplete iso date with year required
   * E.g. 1919, 1919-10, 1919-10-23
   */
  @JsonCreator
  public static FuzzyDate of(String isoDate) {
    if (StringUtils.isBlank(isoDate)) return null;

    int dashCnt = StringUtils.countMatches(isoDate, '-');
    switch (dashCnt) {
      case 0:
        return new FuzzyDate(Year.parse(isoDate));
      case 1:
        return new FuzzyDate(YearMonth.parse(isoDate));
      case 2:
        return new FuzzyDate(LocalDate.parse(isoDate));
    }
    throw new IllegalArgumentException("No fuzzy ISO date");
  }

  public static FuzzyDate fromInt(int x) {
    if (x <= 0) return null;
    int y = x / DAY_PER_YEAR;
    x -= y*DAY_PER_YEAR;

    if (x > 0) {
      int m = x / DAY_PER_MONTH;
      x -= m*DAY_PER_MONTH;

      if (x > 0) {
        return FuzzyDate.of(y,m,x);

      } else {
        return FuzzyDate.of(y,m);
      }
    }
    return FuzzyDate.of(y);
  }

  public FuzzyDate(TemporalAccessor ta) {
    // Won't happen when obtaining a FuzzyDate from the FuzzyDateParser, but
    // since this is a public constructor ...
    Objects.requireNonNull(ta, "ta");

    // make sure we deal with one of the 3 supported classes
    if (ta instanceof Year || ta instanceof YearMonth || ta instanceof LocalDate) {
      this.ta = ta;
    } else if (!ta.isSupported(YEAR)) {
      throw new IllegalArgumentException("Cannot create FuzzyDate without a year");
    } else {
      // copy fuzzy date
      this.ta = bestMatch(ta);
    }
  }

  /**
   * Returns a {@link LocalDate} if year, month and day are known; a {@link YearMonth} if year and
   * month are known; or a {@link Year} if only the year is known.
   *
   * @return
   */
  private static TemporalAccessor bestMatch(TemporalAccessor ta) {
    if (ta.isSupported(MONTH_OF_YEAR)) {
      if (ta.isSupported(DAY_OF_MONTH)) {
        return LocalDate.of(ta.get(YEAR), ta.get(MONTH_OF_YEAR), ta.get(DAY_OF_MONTH));
      }
      return YearMonth.of(ta.get(YEAR), ta.get(MONTH_OF_YEAR));
    }
    return Year.of(ta.get(YEAR));
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
   * @return Year, YearMonth or LocalDate instance represeting this fuzzy date
   */
  public TemporalAccessor getDate() {
    return ta;
  }

  public int getYear() {
    return ta.get(YEAR);
  }

  /**
   * Returns false if year, month and day are known, true otherwise.
   *
   * @return
   */
  public boolean isFuzzyDate() {
    return !ta.isSupported(MONTH_OF_YEAR) || !ta.isSupported(DAY_OF_MONTH);
  }

  public CSLDate toCSLDate() {
    if (ta.isSupported(MONTH_OF_YEAR)) {
      if (ta.isSupported(DAY_OF_MONTH)) {
        return new CSLDateBuilder().dateParts(ta.get(YEAR), ta.get(MONTH_OF_YEAR), ta.get(DAY_OF_MONTH)).build();
      }
      return new CSLDateBuilder().dateParts(ta.get(YEAR), ta.get(MONTH_OF_YEAR)).build();
    }
    return new CSLDateBuilder().dateParts(ta.get(YEAR)).build();
  }

  public CslDate toCslDate() {
    if (ta.isSupported(MONTH_OF_YEAR)) {
      if (ta.isSupported(DAY_OF_MONTH)) {
        return new CslDate(ta.get(YEAR), ta.get(MONTH_OF_YEAR), ta.get(DAY_OF_MONTH));
      }
      return new CslDate(ta.get(YEAR), ta.get(MONTH_OF_YEAR));
    }
    return new CslDate(ta.get(YEAR));
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

  @Override
  @JsonValue
  public String toString() {
    return ta.toString();
  }

  /**
   * Int representation of the fuzzy date. Can be reconstructed via the fromInt factory method.
   * @return a single int representing the fuzzy date
   */
  public int toInt() {
    int x = ta.get(YEAR) * DAY_PER_YEAR;
    if (ta.isSupported(MONTH_OF_YEAR)) {
      x += ta.get(MONTH_OF_YEAR) * DAY_PER_MONTH;
      if (ta.isSupported(DAY_OF_MONTH)) {
        x += ta.get(DAY_OF_MONTH);
      }
    }
    return x;
  }
}
