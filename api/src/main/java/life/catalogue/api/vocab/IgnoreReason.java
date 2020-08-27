package life.catalogue.api.vocab;

import org.gbif.nameparser.api.NameType;

import java.util.Map;

import static org.gbif.nameparser.api.NameType.*;

public enum IgnoreReason {

  CHRESONYM,
  INDETERMINED,
  RANK,
  INCONSISTENT_NAME,
  // ignored name types
  NAME_PLACEHOLDER,
  NAME_NO_NAME,
  NAME_HYBRID_FORMULA,
  NAME_INFORMAL;

  private static final Map<NameType, IgnoreReason> nameTypes = Map.of(
    PLACEHOLDER, NAME_PLACEHOLDER,
    NO_NAME, NAME_NO_NAME,
    HYBRID_FORMULA, NAME_HYBRID_FORMULA,
    INFORMAL, NAME_INFORMAL
  );

  public static IgnoreReason reasonByNameType(NameType nameType) {
    return nameTypes.get(nameType);
  }
}
