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
  INCONSISTENT_NAME(),
  IGNORED_PARENT(NameUsageBase::getParentId),
  // ignored name types
  NAME_SCIENTIFIC(),
  NAME_VIRUS(),
  NAME_HYBRID_FORMULA(),
  NAME_INFORMAL(),
  NAME_OTU(),
  NAME_PLACEHOLDER(),
  NAME_NO_NAME();

  private static final Map<NameType, IgnoreReason> nameTypes = Map.of(
    SCIENTIFIC, NAME_SCIENTIFIC,
    VIRUS, NAME_VIRUS,
    HYBRID_FORMULA, NAME_HYBRID_FORMULA,
    INFORMAL, NAME_INFORMAL,
    OTU, NAME_OTU,
    PLACEHOLDER, NAME_PLACEHOLDER,
    NO_NAME, NAME_NO_NAME
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
