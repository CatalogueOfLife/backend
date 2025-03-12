package life.catalogue.matching;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.MatchType;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class UsageMatch<T extends SimpleNameWithNidx> implements DSID<String> {
  @JsonIgnore
  public final int datasetKey; // dataset key the usage and classification belongs to
  public final SimpleNameClassified<T> usage;
  public final Integer sectorKey; // optional sector key to identify the source of the usage in a project
  @JsonIgnore
  public final boolean ignore;
  public final SimpleNameClassified<T> doubtfulUsage;
  public final MatchType type;
  public final List<SimpleNameClassified<T>> alternatives;

  protected UsageMatch(UsageMatch<T> src) {
    this(src.datasetKey, src.usage, src.sectorKey, src.type, src.ignore, src.doubtfulUsage, src.alternatives);
  }

  protected UsageMatch(UsageMatch<T> src, MatchType type) {
    this(src.datasetKey, src.usage, src.sectorKey, type, src.ignore, src.doubtfulUsage, src.alternatives);
  }

  protected UsageMatch(int datasetKey, SimpleNameClassified<T> usage, Integer sectorKey, MatchType type, boolean ignore, SimpleNameClassified<T> doubtfulUsage, List<SimpleNameClassified<T>> alternatives) {
    this.datasetKey = datasetKey;
    this.usage = usage;
    this.sectorKey = sectorKey;
    this.type = type;
    this.ignore = ignore;
    this.doubtfulUsage = doubtfulUsage;
    this.alternatives = alternatives;
  }

  public static <T extends SimpleNameWithNidx> UsageMatch<T> ignore(UsageMatch<T> original) {
    return new UsageMatch(original.datasetKey, original.usage, original.sectorKey, original.type, true, original.doubtfulUsage, original.alternatives);
  }

  public static <T extends SimpleNameWithNidx> UsageMatch<T> match(SimpleNameClassified<T> usage, int datasetKey, List<SimpleNameClassified<T>> alternatives) {
    return UsageMatch.match(usage.getNamesIndexMatchType(), usage, datasetKey, rmFromAlt(usage, alternatives));
  }

  public static <T extends SimpleNameWithNidx> UsageMatch<T> match(MatchType type, SimpleNameClassified<T> usage, int datasetKey, List<SimpleNameClassified<T>> alternatives) {
    return new UsageMatch<>(datasetKey, usage, usage.getSectorKey(), type, false, null, rmFromAlt(usage, alternatives));
  }

  /**
   * Snaps to a usage but flag it to be ignored in immediate processing.
   */
  public static <T extends SimpleNameWithNidx> UsageMatch<T> snap(SimpleNameClassified<T> usage, int datasetKey, List<SimpleNameClassified<T>> alternatives) {
    return new UsageMatch(datasetKey, usage, null, usage.getNamesIndexMatchType(), true, null, rmFromAlt(usage, alternatives));
  }

  /**
   * No match
   */
  public static <T extends SimpleNameWithNidx> UsageMatch<T> empty(MatchType type, List<SimpleNameClassified<T>> alternatives, int datasetKey) {
    return new UsageMatch<>(datasetKey, null, null, type, false, null, alternatives);
  }

  public static <T extends SimpleNameWithNidx> UsageMatch<T> empty(SimpleNameClassified<T> doubtfulUsage, int datasetKey) {
    return new UsageMatch<>(datasetKey, null, null, doubtfulUsage.getNamesIndexMatchType(), false, doubtfulUsage, null);
  }

  public static <T extends SimpleNameWithNidx> UsageMatch<T> empty(int datasetKey, MatchType type) {
    return new UsageMatch<>(datasetKey, null, null, type, false, null, null);
  }

  public static <T extends SimpleNameWithNidx> UsageMatch<T> empty(int datasetKey) {
    return new UsageMatch<>(datasetKey, null, null, MatchType.NONE, false, null, null);
  }

  public static <T extends SimpleNameWithNidx> UsageMatch<T> unsupported(int datasetKey) {
    return new UsageMatch<>(datasetKey, null, null, MatchType.UNSUPPORTED, true, null, null);
  }

  public boolean isMatch() {
    return usage != null;
  }

  private static <T extends SimpleNameWithNidx> List<SimpleNameClassified<T>> rmFromAlt(SimpleName usage, List<SimpleNameClassified<T>> alternatives) {
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
