package org.col.dw.api.vocab;

import org.gbif.dwc.terms.Term;

/**
 *
 */
public enum CoLTerm implements Term {

  nomenclaturalRemarks,
  etymology;

  public static final String NS = "http://rs.col.plus/terms/";
  public static final String PREFIX = "col";

  /**
   * The full qualified term uri including the namespace.
   * For example http://rs.tdwg.org/dwc/terms/scientificName.
   *
   * @return full qualified term uri
   */
  @Override
  public String qualifiedName() {
    return NS + simpleName();
  }

  /**
   * The simple term name without a namespace.
   * For example scientificName.
   *
   * @return simple term name
   */
  @Override
  public String simpleName() {
    return name();
  }

  /**
   * @return true if the col term is defining a class instead of a property, e.g. Taxon
   */
  public boolean isClass() {
    return Character.isUpperCase(simpleName().charAt(0));
  }

  @Override
  public String toString() {
    return PREFIX + ":" + name();
  }

}
