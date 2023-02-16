package life.catalogue.assembly;

import com.fasterxml.jackson.annotation.JsonIgnore;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.model.SimpleNameWithPub;
import life.catalogue.api.vocab.MatchType;

import java.util.List;

public class UsageMatch {
  @JsonIgnore
  public final int datasetKey; // dataset key the usage and classification belongs to
  public final SimpleNameClassified<SimpleNameWithPub> usage;
  public final Integer sourceDatasetKey; // optional dataset key to identify the source of the usage in a project, i.e. not the projects datasetKey
  @JsonIgnore
  public final boolean ignore;
  public final SimpleNameClassified<SimpleNameWithPub> doubtfulUsage;
  public final MatchType type;
  public final List<SimpleNameClassified<SimpleNameWithPub>> alternatives;

  protected UsageMatch(int datasetKey, SimpleNameClassified<SimpleNameWithPub> usage, Integer sourceDatasetKey, MatchType type, boolean ignore, SimpleNameClassified<SimpleNameWithPub> doubtfulUsage, List<SimpleNameClassified<SimpleNameWithPub>> alternatives) {
    this.datasetKey = datasetKey;
    this.usage = usage;
    this.sourceDatasetKey = sourceDatasetKey;
    this.type = type;
    this.ignore = ignore;
    this.doubtfulUsage = doubtfulUsage;
    this.alternatives = alternatives;
  }

  public static UsageMatch match(SimpleNameClassified<SimpleNameWithPub> usage, int datasetKey) {
    return UsageMatch.match(usage.getNamesIndexMatchType(), usage, datasetKey);
  }

  public static UsageMatch match(MatchType type, SimpleNameClassified<SimpleNameWithPub> usage, int datasetKey) {
    return new UsageMatch(datasetKey, usage, null, type, false, null, null);
  }


  /**
   * Snaps to a usage but flag it to be ignored in immediate processing.
   */
  public static UsageMatch snap(SimpleNameClassified<SimpleNameWithPub> usage, int datasetKey) {
    return UsageMatch.snap(usage.getNamesIndexMatchType(), usage, datasetKey);
  }

  public static UsageMatch snap(MatchType type, SimpleNameClassified<SimpleNameWithPub> usage, int datasetKey) {
    return new UsageMatch(datasetKey, usage, null, type, true, null, null);
  }


  /**
   * No match
   */
  public static UsageMatch empty(List<SimpleNameClassified<SimpleNameWithPub>> alternatives, int datasetKey) {
    return new UsageMatch(datasetKey, null, null, MatchType.AMBIGUOUS, false, null, alternatives);
  }

  public static UsageMatch empty(SimpleNameClassified<SimpleNameWithPub> doubtfulUsage, int datasetKey) {
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

  @Override
  public String toString() {
    return String.format("%s [%s] %s", usage, type, datasetKey);
  }
}
