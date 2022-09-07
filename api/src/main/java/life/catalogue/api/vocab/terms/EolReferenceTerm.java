package life.catalogue.api.vocab.terms;

import org.gbif.dwc.terms.AlternativeNames;
import org.gbif.dwc.terms.Term;

import java.net.URI;

/**
 * EOL reference terms as used by Plazi in combination with DC terms and the bibo ontology.
 */
public enum EolReferenceTerm implements Term, AlternativeNames {
  Reference(true),
  publicationType,
  full_reference,
  primaryTitle;

  private static final String PREFIX = "eolref";
  private static final String NS = "http://eol.org/schema/reference/";
  private static final URI NS_URI = URI.create(NS);

  private final boolean isClass;
  private final String[] alternatives;

  EolReferenceTerm() {
    this(false);
  }

  EolReferenceTerm(boolean isClass) {
    this.alternatives = new String[0];
    this.isClass = isClass;
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
  public String simpleName() {
    return name();
  }
  
  @Override
  public String toString() {
    return prefixedName();
  }
  
  @Override
  public String[] alternativeNames() {
    return this.alternatives;
  }
  
  @Override
  public boolean isClass() {
    return isClass;
  }
  
}
