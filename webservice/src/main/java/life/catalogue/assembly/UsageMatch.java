package life.catalogue.assembly;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.MatchType;

import java.util.List;

public class UsageMatch {
  public final int datasetKey; // dataset key the usage and classification belongs to
  public final SimpleNameClassified usage;
  public final Integer sourceDatasetKey; // optional dataset key to identify the source of the usage in a project, i.e. not the projects datasetKey
  public final boolean ignore;
  public final SimpleNameClassified doubtfulUsage;
  public final MatchType type;
  public final List<SimpleNameClassified> alternatives;

  private UsageMatch(int datasetKey, SimpleNameClassified usage, Integer sourceDatasetKey, MatchType type, boolean ignore, SimpleNameClassified doubtfulUsage, List<SimpleNameClassified> alternatives) {
    this.datasetKey = datasetKey;
    this.usage = usage;
    this.sourceDatasetKey = sourceDatasetKey;
    this.type = type;
    this.ignore = ignore;
    this.doubtfulUsage = doubtfulUsage;
    this.alternatives = alternatives;
  }

  public static UsageMatch match(SimpleNameClassified usage, int datasetKey) {
    return UsageMatch.match(usage.getNamesIndexMatchType(), usage, datasetKey);
  }

  public static UsageMatch match(MatchType type, SimpleNameClassified usage, int datasetKey) {
    return new UsageMatch(datasetKey, usage, null, type, false, null, null);
  }


  /**
   * Snaps to a usage but flag it to be ignored in immediate processing.
   */
  public static UsageMatch snap(SimpleNameClassified usage, int datasetKey) {
    return UsageMatch.snap(usage.getNamesIndexMatchType(), usage, datasetKey);
  }

  public static UsageMatch snap(MatchType type, SimpleNameClassified usage, int datasetKey) {
    return new UsageMatch(datasetKey, usage, null, type, true, null, null);
  }


  /**
   * No match
   */
  public static UsageMatch empty(List<SimpleNameClassified> alternatives, int datasetKey) {
    return new UsageMatch(datasetKey, null, null, MatchType.AMBIGUOUS, false, null, alternatives);
  }

  public static UsageMatch empty(SimpleNameClassified doubtfulUsage, int datasetKey) {
    return new UsageMatch(datasetKey, null, null, doubtfulUsage.getNamesIndexMatchType(), false, doubtfulUsage, null);
  }

  public static UsageMatch empty(int datasetKey) {
    return new UsageMatch(datasetKey, null, null, MatchType.NONE, false, null, null);
  }

  public boolean isMatch() {
    return usage != null;
  }

  public DSID<String> asDSID(){
    return DSID.of(datasetKey, usage.getId());
  }
}
