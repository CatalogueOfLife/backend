package life.catalogue.assembly;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleNameWithPub;
import life.catalogue.api.vocab.MatchType;

import java.util.List;

public class UsageMatch {
  public final SimpleNameWithPub usage;
  public final Integer sourceDatasetKey; // optional dataset key to identify the source of the usage in a project, i.e. not the projects datasetKey
  public final boolean ignore;
  public final SimpleNameWithPub doubtfulUsage;
  public final MatchType type;
  public final List<SimpleNameWithPub> alternatives;

  private UsageMatch(SimpleNameWithPub usage, Integer sourceDatasetKey, MatchType type, boolean ignore, SimpleNameWithPub doubtfulUsage, List<SimpleNameWithPub> alternatives) {
    this.usage = usage;
    this.sourceDatasetKey = sourceDatasetKey;
    this.type = type;
    this.ignore = ignore;
    this.doubtfulUsage = doubtfulUsage;
    this.alternatives = alternatives;
  }

  public static UsageMatch match(SimpleNameWithPub usage) {
    return UsageMatch.match(usage.getNamesIndexMatchType(), usage);
  }

  public static UsageMatch match(MatchType type, SimpleNameWithPub usage) {
    return new UsageMatch(usage, null, type, false, null, null);
  }


  /**
   * Snaps to a usage but flag it to be ignored in immediate processing.
   */
  public static UsageMatch snap(SimpleNameWithPub usage) {
    return UsageMatch.snap(usage.getNamesIndexMatchType(), usage);
  }

  public static UsageMatch snap(MatchType type, SimpleNameWithPub usage) {
    return new UsageMatch(usage, null, type, true, null, null);
  }


  /**
   * No match
   */
  public static UsageMatch empty(List<SimpleNameWithPub> alternatives) {
    return new UsageMatch(null, null, MatchType.AMBIGUOUS, false, null, alternatives);
  }

  public static UsageMatch empty(SimpleNameWithPub doubtfulUsage) {
    return new UsageMatch(null, null, doubtfulUsage.getNamesIndexMatchType(), false, doubtfulUsage, null);
  }

  public static UsageMatch empty() {
    return new UsageMatch(null, null, MatchType.NONE, false, null, null);
  }


  public boolean isMatch() {
    return usage != null;
  }

  public DSID<String> asDSID(){
    return DSID.of(sourceDatasetKey, usage.getId());
  }
}
