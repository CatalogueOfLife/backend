package life.catalogue.assembly;

import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.vocab.MatchType;

import java.util.List;

public class UsageMatch {
  public final NameUsageBase usage;
  public final boolean ignore;
  public final NameUsageBase doubtfulUsage;
  public final MatchType type;
  public final List<NameUsageBase> alternatives;

  private UsageMatch(NameUsageBase usage, MatchType type, boolean ignore, NameUsageBase doubtfulUsage, List<NameUsageBase> alternatives) {
    this.usage = usage;
    this.type = type;
    this.ignore = ignore;
    this.doubtfulUsage = doubtfulUsage;
    this.alternatives = alternatives;
  }

  public static UsageMatch match(NameUsageBase usage) {
    return UsageMatch.match(usage.getName().getNamesIndexType(), usage);
  }

  public static UsageMatch match(MatchType type, NameUsageBase usage) {
    return new UsageMatch(usage, type, false, null, null);
  }

  /**
   * Snaps to a usage but flag it to be ignored in immediate processing.
   */
  public static UsageMatch snap(MatchType type, NameUsageBase usage) {
    return new UsageMatch(usage, type, true, null, null);
  }

  public static UsageMatch snap(NameUsageBase usage) {
    return UsageMatch.snap(usage.getName().getNamesIndexType(), usage);
  }

  public static UsageMatch empty(List<NameUsageBase> alternatives) {
    return new UsageMatch(null, MatchType.AMBIGUOUS, false, null, alternatives);
  }

  public static UsageMatch empty(NameUsageBase doubtfulUsage) {
    return new UsageMatch(null, doubtfulUsage.getName().getNamesIndexType(), false, doubtfulUsage, null);
  }

  public static UsageMatch empty() {
    return new UsageMatch(null, MatchType.NONE, false, null, null);
  }

  public boolean isMatch() {
    return usage != null;
  }
}
