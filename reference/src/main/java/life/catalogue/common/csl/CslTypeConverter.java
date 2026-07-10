package life.catalogue.common.csl;

import life.catalogue.api.model.CSLType;

/**
 * Boundary converter between the COL-owned {@link CSLType} enum (in the slim api model) and
 * citeproc's {@code de.undercouch.citeproc.csl.CSLType}. Both enums share constant names 1:1,
 * so the mapping is by {@code name()}.
 */
public class CslTypeConverter {
  /**
   * NOTE: there is a second, intentionally different COL->citeproc CSLType mapping in
   * {@link CslDataConverter#toCSLType(CSLType)}. That one remaps {@code null}/{@code ARTICLE} to
   * {@code ARTICLE_JOURNAL} to satisfy citeproc-java's rendering requirements at its call site,
   * whereas this method must preserve {@code null} as {@code null} for its own caller. Do NOT
   * "unify" these two methods - doing so would silently change citation-type output at one of
   * the two call sites.
   */
  public static de.undercouch.citeproc.csl.CSLType toCiteproc(CSLType t) {
    return t == null ? null : de.undercouch.citeproc.csl.CSLType.valueOf(t.name());
  }
  public static CSLType fromCiteproc(de.undercouch.citeproc.csl.CSLType t) {
    return t == null ? null : CSLType.valueOf(t.name());
  }
}
