package org.col.api.vocab;

import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;

import java.net.URI;

/**
 *
 */
public enum CoLTerm implements Term {

  nomenclaturalRemarks,
  etymology;

  private static final String PREFIX = "col";
  private static final String NS = "http://rs.col.plus/terms/";
  private static final URI NS_URI = URI.create(NS);

  static {
    TermFactory.instance().addTerms(CoLTerm.values(), new String[]{});
  }

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
  @Override
  public boolean isClass() {
    return Character.isUpperCase(simpleName().charAt(0));
  }

  @Override
  public String prefixedName() {
    return PREFIX + ":" + simpleName();
  }


  @Override
  public String prefix() {
    return PREFIX;
  }

  @Override
  public URI namespace() {
    return NS_URI;
  }

  @Override
  public String toString() {
    return prefixedName();
  }

}
