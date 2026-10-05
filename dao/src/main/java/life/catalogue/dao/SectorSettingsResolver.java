package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomStatus;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Layers the settings of a sector sync: built-in defaults per mode, then every matching profile in the order given,
 * then the sector itself. For scalars and allow-lists the highest level that sets a value wins, null and empty
 * sets meaning "not set". Blocklists are unioned over all levels, so no level can lift a block set below it.
 */
public final class SectorSettingsResolver {
  public static final String DEFAULT = "default";
  public static final String SECTOR = "sector";
  // in merge mode we dont want any higher ranks than family by default
  public static final Set<Rank> MERGE_RANKS_DEFAULT = Collections.unmodifiableSet(EnumSet.of(
    Rank.FAMILY, Rank.GENUS, Rank.SPECIES, Rank.SUBSPECIES, Rank.VARIETY, Rank.FORM
  ));

  private record Level(String source, SyncSettings settings) {}

  private interface Field {
    String name();
    void resolve(List<Level> levels, SyncSettings into, Map<String, String> sources);
  }

  private record Nearest<T>(String name, Function<SyncSettings, T> getter, BiConsumer<SyncSettings, T> setter) implements Field {
    @Override
    public void resolve(List<Level> levels, SyncSettings into, Map<String, String> sources) {
      for (int i = levels.size() - 1; i >= 0; i--) {
        T val = getter.apply(levels.get(i).settings());
        if (isSet(val)) {
          setter.accept(into, val);
          sources.put(name, levels.get(i).source());
          return;
        }
      }
      // no level sets it, e.g. no name filter at all
      sources.put(name, DEFAULT);
    }
  }

  private record Union<E>(String name, Function<SyncSettings, Set<E>> getter, BiConsumer<SyncSettings, Set<E>> setter) implements Field {
    @Override
    public void resolve(List<Level> levels, SyncSettings into, Map<String, String> sources) {
      Set<E> all = new HashSet<>();
      List<String> from = new ArrayList<>();
      for (Level lvl : levels) {
        Set<E> val = getter.apply(lvl.settings());
        if (isSet(val)) {
          all.addAll(val);
          from.add(lvl.source());
        }
      }
      setter.accept(into, all);
      sources.put(name, from.isEmpty() ? DEFAULT : String.join(",", from));
    }
  }

  private static final List<Field> FIELDS = List.<Field>of(
    new Nearest<>("ranks", SyncSettings::getRanks, SyncSettings::setRanks),
    new Nearest<>("entities", SyncSettings::getEntities, SyncSettings::setEntities),
    new Nearest<>("nameTypes", SyncSettings::getNameTypes, SyncSettings::setNameTypes),
    new Nearest<>("nameFilter", SyncSettings::getNameFilter, SyncSettings::setNameFilter),
    new Nearest<>("extinctFilter", SyncSettings::getExtinctFilter, SyncSettings::setExtinctFilter),
    new Nearest<>("copyAccordingTo", SyncSettings::getCopyAccordingTo, SyncSettings::setCopyAccordingTo),
    new Nearest<>("removeOrdinals", SyncSettings::getRemoveOrdinals, SyncSettings::setRemoveOrdinals),
    new Nearest<>("createImplicitNames", SyncSettings::getCreateImplicitNames, SyncSettings::setCreateImplicitNames),
    new Nearest<>("code", SyncSettings::getCode, SyncSettings::setCode),
    new Nearest<>("authorshipUpdate", SyncSettings::getAuthorshipUpdate, SyncSettings::setAuthorshipUpdate),
    new Union<>("nameStatusExclusion", SyncSettings::getNameStatusExclusion, SyncSettings::setNameStatusExclusion),
    new Union<>("issueExclusion", SyncSettings::getIssueExclusion, SyncSettings::setIssueExclusion),
    new Union<>("blockedNames", SyncSettings::getBlockedNames, SyncSettings::setBlockedNames),
    new Union<>("blockedNamePatterns", SyncSettings::getBlockedNamePatterns, SyncSettings::setBlockedNamePatterns)
  );

  private SectorSettingsResolver() {
  }

  public static String source(SectorProfile p) {
    return "profile:" + p.getId();
  }

  /**
   * @return the names of all settings the resolver layers, i.e. all SyncSettings properties
   */
  public static Set<String> fieldNames() {
    return FIELDS.stream().map(Field::name).collect(Collectors.toSet());
  }

  /**
   * The built-in defaults, the lowest level of every resolution.
   */
  public static SectorSettings defaults(Sector.Mode mode) {
    var d = new SectorSettings();
    d.setRanks(mode == Sector.Mode.MERGE ? EnumSet.copyOf(MERGE_RANKS_DEFAULT) : EnumSet.allOf(Rank.class));
    d.setEntities(EnumSet.allOf(EntityType.class));
    d.setNameTypes(EnumSet.noneOf(NameType.class));
    d.setNameStatusExclusion(EnumSet.noneOf(NomStatus.class));
    d.setCopyAccordingTo(false);
    d.setRemoveOrdinals(false);
    d.setCreateImplicitNames(true);
    d.setAuthorshipUpdate(Sector.AuthorshipUpdate.NONE);
    d.setIssueExclusion(EnumSet.noneOf(Issue.class));
    d.setBlockedNames(new HashSet<>());
    d.setBlockedNamePatterns(new HashSet<>());
    return d;
  }

  /**
   * @param profiles the profiles matching the sector, in ascending position. A later one overrides an earlier one.
   */
  public static EffectiveSectorSettings resolve(Sector sector, List<SectorProfile> profiles) {
    List<Level> levels = new ArrayList<>();
    levels.add(new Level(DEFAULT, defaults(sector.getMode())));
    for (SectorProfile p : profiles) {
      levels.add(new Level(source(p), p.getSettings()));
    }
    levels.add(new Level(SECTOR, sector));
    var resolved = new SectorSettings();
    Map<String, String> sources = new LinkedHashMap<>();
    for (Field f : FIELDS) {
      f.resolve(levels, resolved, sources);
    }
    // never hand out a set owned by a profile, the sector or the defaults
    return new EffectiveSectorSettings(SectorSettings.of(resolved), sources);
  }

  static boolean isSet(Object val) {
    if (val == null) return false;
    if (val instanceof Collection<?> c) return !c.isEmpty();
    if (val instanceof String s) return !s.isBlank();
    return true;
  }
}
