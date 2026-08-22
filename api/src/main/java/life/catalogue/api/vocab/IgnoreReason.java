package life.catalogue.api.vocab;

import life.catalogue.api.model.NameUsageBase;

import org.gbif.nameparser.api.NameType;

import java.util.Map;
import java.util.function.Function;

import static org.gbif.nameparser.api.NameType.*;

public enum IgnoreReason {

  NOMENCLATURAL_STATUS(u -> u.getName().getNomStatus()),
  INDETERMINED(),
  RANK(u -> u.getName().getRank()),
  EXTINCT(u -> u.isTaxon() ? u.asTaxon().isExtinct() : null),
  INCONSISTENT_NAME(),
  NAME_FILTER(u -> u.getName().getScientificName()),
  IGNORED_PARENT(NameUsageBase::getParentId),
  // ignored name types - one reason per NameType, named to match the NameType value
  NAME_SCIENTIFIC(),
  NAME_FORMULA(),
  NAME_INFORMAL(),
  NAME_PLACEHOLDER(),
  NAME_IDENTIFIER(),
  NAME_OTHER(),
  // historical: name-parser v4.2 dropped NameType.VIRUS (viruses are now OTHER carrying
  // NomCode.VIRUS). Kept so old import metrics still resolve, but no NameType maps to it.
  NAME_VIRUS();

  private static final Map<NameType, IgnoreReason> nameTypes = Map.of(
    SCIENTIFIC, NAME_SCIENTIFIC,
    FORMULA, NAME_FORMULA,
    INFORMAL, NAME_INFORMAL,
    PLACEHOLDER, NAME_PLACEHOLDER,
    IDENTIFIER, NAME_IDENTIFIER,
    OTHER, NAME_OTHER
  );

  private final Function<NameUsageBase, Object> valueExtractor;

  public Function<NameUsageBase, Object> getValueExtractor() {
    return valueExtractor;
  }

  IgnoreReason(Function<NameUsageBase, Object> valueExtractor) {
    this.valueExtractor = valueExtractor;
  }
  IgnoreReason() {
    this.valueExtractor = null;
  }

  public static IgnoreReason reasonByNameType(NameType nameType) {
    return nameTypes.get(nameType);
  }
}
