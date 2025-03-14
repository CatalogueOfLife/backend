package life.catalogue.matching;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.MatchType;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class UsageMatch<T extends SimpleNameWithNidx> {
  public final SimpleNameClassified<T> usage;
  public final Integer sectorKey; // optional sector key to identify the source of the usage in a project
  @JsonIgnore
  public final boolean ignore;
  public final SimpleNameClassified<T> doubtfulUsage;
  public final MatchType type;
  public final List<SimpleNameClassified<T>> alternatives;

  protected UsageMatch(UsageMatch<T> src) {
    this(src.usage, src.sectorKey, src.type, src.ignore, src.doubtfulUsage, src.alternatives);
  }

  protected UsageMatch(UsageMatch<T> src, MatchType type) {
    this(src.usage, src.sectorKey, type, src.ignore, src.doubtfulUsage, src.alternatives);
  }

  protected UsageMatch(SimpleNameClassified<T> usage, Integer sectorKey, MatchType type, boolean ignore, SimpleNameClassified<T> doubtfulUsage, List<SimpleNameClassified<T>> alternatives) {
    this.usage = usage;
    this.sectorKey = sectorKey;
    this.type = type;
    this.ignore = ignore;
    this.doubtfulUsage = doubtfulUsage;
    this.alternatives = alternatives;
  }

  public static <T extends SimpleNameWithNidx> UsageMatch<T> ignore(UsageMatch<T> original) {
    return new UsageMatch<>(original.usage, original.sectorKey, original.type, true, original.doubtfulUsage, original.alternatives);
  }

  public static <T extends SimpleNameWithNidx> UsageMatch<T> match(SimpleNameClassified<T> usage, List<SimpleNameClassified<T>> alternatives) {
    return UsageMatch.match(usage.getNamesIndexMatchType(), usage, rmFromAlt(usage, alternatives));
  }

  public static <T extends SimpleNameWithNidx> UsageMatch<T> match(MatchType type, SimpleNameClassified<T> usage, List<SimpleNameClassified<T>> alternatives) {
    return new UsageMatch<>(usage, usage.getSectorKey(), type, false, null, rmFromAlt(usage, alternatives));
  }

  /**
   * Snaps to a usage but flag it to be ignored in immediate processing.
   */
  public static <T extends SimpleNameWithNidx> UsageMatch<T> snap(SimpleNameClassified<T> usage, List<SimpleNameClassified<T>> alternatives) {
    return new UsageMatch(usage, null, usage.getNamesIndexMatchType(), true, null, rmFromAlt(usage, alternatives));
  }

  /**
   * No match
   */
  public static <T extends SimpleNameWithNidx> UsageMatch<T> empty(MatchType type, List<SimpleNameClassified<T>> alternatives) {
    return new UsageMatch<>(null, null, type, false, null, alternatives);
  }

  public static <T extends SimpleNameWithNidx> UsageMatch<T> empty(SimpleNameClassified<T> doubtfulUsage) {
    return new UsageMatch<>(null, null, doubtfulUsage.getNamesIndexMatchType(), false, doubtfulUsage, null);
  }

  public static <T extends SimpleNameWithNidx> UsageMatch<T> empty(MatchType type) {
    return new UsageMatch<>(null, null, type, false, null, null);
  }

  public static <T extends SimpleNameWithNidx> UsageMatch<T> empty() {
    return new UsageMatch<>(null, null, MatchType.NONE, false, null, null);
  }

  public static <T extends SimpleNameWithNidx> UsageMatch<T> unsupported() {
    return new UsageMatch<>(null, null, MatchType.UNSUPPORTED, true, null, null);
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

  public String getId() {
    return usage == null ? null : usage.getId();
  }

  public DSID<String> toDSID(int datasetKey){
    return usage == null ? null : DSID.of(datasetKey, usage.getId());
  }

  @Override
  public String toString() {
    if (usage == null) {
      return String.format("[%s]", type);
    } else {
      return String.format("%s [%s]", usage, type);
    }
  }
}
