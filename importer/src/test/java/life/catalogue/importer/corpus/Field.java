package life.catalogue.importer.corpus;

import life.catalogue.api.model.ParsedNameUsage;
import life.catalogue.api.vocab.Issue;

import org.gbif.nameparser.api.Authorship;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/**
 * The columns the runner writes for every interpreted record, in file order, and how the diff compares them.
 */
public enum Field {
  ROW(Group.META, null),
  N(Group.META, null),
  TYPE(Group.META, null),
  DATASET(Group.META, null),
  INPUT(Group.META, null),
  STATUS(Group.STATUS, null),
  ERROR(Group.META, null),

  LABEL(Group.TEXT, u -> u.getName().getLabel()),
  SCIENTIFIC_NAME(Group.TEXT, u -> u.getName().getScientificName()),
  AUTHORSHIP(Group.TEXT, u -> u.getName().getAuthorship()),

  RANK(Group.STRUCTURE, u -> u.getName().getRank()),
  CODE(Group.STRUCTURE, u -> u.getName().getCode()),
  NAME_TYPE(Group.STRUCTURE, u -> u.getName().getType()),
  UNINOMIAL(Group.STRUCTURE, u -> u.getName().getUninomial()),
  GENUS(Group.STRUCTURE, u -> u.getName().getGenus()),
  INFRAGENERIC_EPITHET(Group.STRUCTURE, u -> u.getName().getInfragenericEpithet()),
  SPECIFIC_EPITHET(Group.STRUCTURE, u -> u.getName().getSpecificEpithet()),
  INFRASPECIFIC_EPITHET(Group.STRUCTURE, u -> u.getName().getInfraspecificEpithet()),
  CULTIVAR_EPITHET(Group.STRUCTURE, u -> u.getName().getCultivarEpithet()),
  NOTHO(Group.STRUCTURE, u -> join(u.getName().getNotho())),
  CANDIDATUS(Group.STRUCTURE, u -> u.getName().isCandidatus() ? "true" : null),
  ORIGINAL_SPELLING(Group.STRUCTURE, u -> u.getName().isOriginalSpelling()),
  NOM_STATUS(Group.STRUCTURE, u -> u.getName().getNomStatus()),
  EXTINCT(Group.STRUCTURE, u -> u.isExtinct() ? "true" : null),
  DOUBTFUL(Group.STRUCTURE, u -> u.isDoubtful() ? "true" : null),

  COMBINATION_AUTHORSHIP(Group.AUTHOR_ATOMS, u -> atoms(u.getName().getCombinationAuthorship())),
  BASIONYM_AUTHORSHIP(Group.AUTHOR_ATOMS, u -> atoms(u.getName().getBasionymAuthorship())),

  UNPARSED(Group.NOTES, u -> u.getName().getUnparsed()),
  NOMENCLATURAL_NOTE(Group.NOTES, u -> u.getName().getNomenclaturalNote()),
  TAXONOMIC_NOTE(Group.NOTES, ParsedNameUsage::getTaxonomicNote),
  PUBLISHED_IN(Group.NOTES, ParsedNameUsage::getPublishedIn),
  PUBLISHED_IN_YEAR(Group.NOTES, u -> u.getName().getPublishedInYear()),

  ISSUES(Group.ISSUES, null);

  /**
   * How a change of a field is judged.
   */
  public enum Group {
    /** not compared */
    META,
    /** whether a name came out at all, or the interpreter threw */
    STATUS,
    /** rendered strings, compared at increasing levels of normalisation */
    TEXT,
    /** parsed properties, any change counts */
    STRUCTURE,
    /** the parsed authorship, any change counts */
    AUTHOR_ATOMS,
    /** strings kept besides the name, compared like TEXT */
    NOTES,
    /** compared issue by issue */
    ISSUES
  }

  public final Group group;
  @Nullable
  private final Function<ParsedNameUsage, Object> getter;

  Field(Group group, @Nullable Function<ParsedNameUsage, Object> getter) {
    this.group = group;
    this.getter = getter;
  }

  boolean isNameProperty() {
    return getter != null;
  }

  @Nullable
  Object get(ParsedNameUsage u) {
    return getter == null ? null : getter.apply(u);
  }

  static String header() {
    return java.util.Arrays.stream(values()).map(f -> f.name().toLowerCase()).collect(Collectors.joining("\t"));
  }

  @Nullable
  static String join(@Nullable Collection<?> vals) {
    if (vals == null || vals.isEmpty()) return null;
    return vals.stream().map(Object::toString).sorted().collect(Collectors.joining(","));
  }

  @Nullable
  static String issues(Collection<Issue> issues) {
    return join(issues);
  }

  /**
   * Renders the parsed atoms of an authorship so that any difference between two shows.
   */
  @Nullable
  static String atoms(@Nullable Authorship a) {
    if (a == null || a.isEmpty() && !a.hasExAuthors() && !a.hasImprintYear() && !a.hasSanctioningAuthor()) return null;
    StringBuilder sb = new StringBuilder();
    if (a.hasExAuthors()) {
      sb.append(String.join("|", a.getExAuthors())).append(" ex ");
    }
    sb.append(String.join("|", a.getAuthors()));
    if (a.isAnonymous()) {
      sb.append(" {anon}");
    }
    if (a.getYear() != null) {
      sb.append(", ").append(a.getYear());
    }
    if (a.hasImprintYear()) {
      sb.append(" [").append(a.getImprintYear()).append(']');
    }
    if (a.hasSanctioningAuthor()) {
      sb.append(" : ").append(a.getSanctioningAuthor());
    }
    return sb.toString().trim();
  }
}
