package life.catalogue.matching;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.MatchType;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class UsageMatch implements DSID<String> {
  @JsonIgnore
  public final int datasetKey; // dataset key the usage and classification belongs to
  public final SimpleNameClassified<SimpleNameCached> usage;
  public final Integer sectorKey; // optional sector key to identify the source of the usage in a project
  @JsonIgnore
  public final boolean ignore;
  public final SimpleNameClassified<SimpleNameCached> doubtfulUsage;
  public final MatchType type;
  public final List<SimpleNameClassified<SimpleNameCached>> alternatives;

  protected UsageMatch(UsageMatch src) {
    this(src.datasetKey, src.usage, src.sectorKey, src.type, src.ignore, src.doubtfulUsage, src.alternatives);
  }

  protected UsageMatch(UsageMatch src, MatchType type) {
    this(src.datasetKey, src.usage, src.sectorKey, type, src.ignore, src.doubtfulUsage, src.alternatives);
  }

  protected UsageMatch(int datasetKey, SimpleNameClassified<SimpleNameCached> usage, Integer sectorKey, MatchType type, boolean ignore, SimpleNameClassified<SimpleNameCached> doubtfulUsage, List<SimpleNameClassified<SimpleNameCached>> alternatives) {
    this.datasetKey = datasetKey;
    this.usage = usage;
    this.sectorKey = sectorKey;
    this.type = type;
    this.ignore = ignore;
    this.doubtfulUsage = doubtfulUsage;
    this.alternatives = alternatives;
  }

  public static UsageMatch ignore(UsageMatch original) {
    return new UsageMatch(original.datasetKey, original.usage, original.sectorKey, original.type, true, original.doubtfulUsage, original.alternatives);
  }

  public static UsageMatch match(SimpleNameClassified<SimpleNameCached> usage, int datasetKey, List<SimpleNameClassified<SimpleNameCached>> alternatives) {
    return UsageMatch.match(usage.getNamesIndexMatchType(), usage, datasetKey, rmFromAlt(usage, alternatives));
  }

  public static UsageMatch match(MatchType type, SimpleNameClassified<SimpleNameCached> usage, int datasetKey, List<SimpleNameClassified<SimpleNameCached>> alternatives) {
    return new UsageMatch(datasetKey, usage, usage.getSectorKey(), type, false, null, rmFromAlt(usage, alternatives));
  }

  /**
   * Snaps to a usage but flag it to be ignored in immediate processing.
   */
  public static UsageMatch snap(SimpleNameClassified<SimpleNameCached> usage, int datasetKey, List<SimpleNameClassified<SimpleNameCached>> alternatives) {
    return new UsageMatch(datasetKey, usage, null, usage.getNamesIndexMatchType(), true, null, rmFromAlt(usage, alternatives));
  }

  /**
   * No match
   */
  public static UsageMatch empty(MatchType type, List<SimpleNameClassified<SimpleNameCached>> alternatives, int datasetKey) {
    return new UsageMatch(datasetKey, null, null, type, false, null, alternatives);
  }

  public static UsageMatch empty(SimpleNameClassified<SimpleNameCached> doubtfulUsage, int datasetKey) {
    return new UsageMatch(datasetKey, null, null, doubtfulUsage.getNamesIndexMatchType(), false, doubtfulUsage, null);
  }

  public static UsageMatch empty(int datasetKey, MatchType type) {
    return new UsageMatch(datasetKey, null, null, type, false, null, null);
  }

  public static UsageMatch empty(int datasetKey) {
    return new UsageMatch(datasetKey, null, null, MatchType.NONE, false, null, null);
  }

  public static UsageMatch unsupported(int datasetKey) {
    return new UsageMatch(datasetKey, null, null, MatchType.UNSUPPORTED, true, null, null);
  }

  public boolean isMatch() {
    return usage != null;
  }

  private static List<SimpleNameClassified<SimpleNameCached>> rmFromAlt(SimpleName usage, List<SimpleNameClassified<SimpleNameCached>> alternatives) {
    if (usage != null && alternatives != null) {
      return alternatives.stream()
                         .filter(u -> !u.getId().equals(usage.getId()))
                         .collect(Collectors.toList());
    }
    return alternatives;
  }

  @Override
  public String toString() {
    if (usage == null) {
      return String.format("[%s] %s", type, datasetKey);
    } else {
      return String.format("%s [%s] %s", usage, type, datasetKey);
    }
  }

  @Override
  public String getId() {
    return usage == null ? null : usage.getId();
  }

  @Override
  public void setId(String id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Integer getDatasetKey() {
    return datasetKey;
  }

  @Override
  public void setDatasetKey(Integer key) {
    throw new UnsupportedOperationException();
  }
}
