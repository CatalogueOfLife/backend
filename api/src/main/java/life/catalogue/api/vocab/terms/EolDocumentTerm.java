package life.catalogue.api.vocab.terms;

import org.gbif.dwc.terms.AlternativeNames;
import org.gbif.dwc.terms.Term;

import java.net.URI;

/**
 * EOL document terms as used by Plazi for html treatments.
 */
public enum EolDocumentTerm implements Term, AlternativeNames {
  Document(true);

  private static final String PREFIX = "eoldoc";
  private static final String NS = "http://eol.org/schema/media/";
  private static final URI NS_URI = URI.create(NS);

  private final boolean isClass;
  private final String[] alternatives;

  EolDocumentTerm() {
    this(false);
  }

  EolDocumentTerm(boolean isClass) {
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
