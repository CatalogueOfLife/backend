package life.catalogue.api.vocab;

/**
 *
 */
public enum Frequency {
  NEVER(-1),
  ONCE(0),
  DAILY(1),
  WEEKLY(7),
  MONTHLY(30),
  QUARTERLY(91),
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
    Frequency best = WEEKLY;
    if (days != null) {
      if (days < 0) {
        return NEVER;
      }
      for (Frequency f : Frequency.values()) {
        if (f.days == days) return f;
        if (f.days < days) {
          best = f;
        } else {
          return best;
        }
      }
    }
    return best;
  }
}
