package org.col.api.vocab;

/**
 *
 */
public enum Frequency {
  NEVER(-1),
  ONCE(0),
  DAILY(1),
  WEEKLY(7),
  MONTHLY(30),
  YEARLY(365);

  private final int days;

  Frequency(int days) {
    this.days = days;
  }

  /**
   * @return frequency interval represented as number of days
   */
  public int getDays() {
    return days;
  }

  public static Frequency fromDays(Integer days) {
    if (days == null) return WEEKLY;
    for (Frequency f : Frequency.values()) {
      if (f.days == days) return f;
    }
    return WEEKLY;
  }
}
