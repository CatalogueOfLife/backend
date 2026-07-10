package life.catalogue.common.csl;

import life.catalogue.api.model.CSLType;

/**
 * Boundary converter between the COL-owned {@link CSLType} enum (in the slim api model) and
 * citeproc's {@code de.undercouch.citeproc.csl.CSLType}. Both enums share constant names 1:1,
 * so the mapping is by {@code name()}.
 */
public class CslTypeConverter {
  public static de.undercouch.citeproc.csl.CSLType toCiteproc(CSLType t) {
    return t == null ? null : de.undercouch.citeproc.csl.CSLType.valueOf(t.name());
  }
  public static CSLType fromCiteproc(de.undercouch.citeproc.csl.CSLType t) {
    return t == null ? null : CSLType.valueOf(t.name());
  }
}
