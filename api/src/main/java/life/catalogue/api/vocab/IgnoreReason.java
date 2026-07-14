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
  // ignored name types
  NAME_SCIENTIFIC(),
  NAME_VIRUS(),
  NAME_HYBRID_FORMULA(),
  NAME_INFORMAL(),
  NAME_OTU(),
  NAME_PLACEHOLDER(),
  NAME_NO_NAME();

  // name-parser v4.2 dropped NameType.VIRUS; viruses are now OTHER (carrying NomCode.VIRUS).
  // NAME_VIRUS is kept as a historical reason value but no NameType maps to it anymore.
  // name-parser 5.0 reintroduced NameType.IDENTIFIER for identifier pseudo-names (BOLD:, UNITE
  // SH...FU codes) - the old OTU concept - so it maps to the historical NAME_OTU reason.
  private static final Map<NameType, IgnoreReason> nameTypes = Map.of(
    SCIENTIFIC, NAME_SCIENTIFIC,
    FORMULA, NAME_HYBRID_FORMULA,
    INFORMAL, NAME_INFORMAL,
    PLACEHOLDER, NAME_PLACEHOLDER,
    IDENTIFIER, NAME_OTU,
    OTHER, NAME_NO_NAME
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
